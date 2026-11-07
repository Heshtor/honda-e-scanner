package cz.helicode.hondaescanner;

import android.app.Application;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.harrysoft.androidbluetoothserial.BluetoothManager;
import com.harrysoft.androidbluetoothserial.SimpleBluetoothDeviceInterface;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

public class CommunicateViewModel extends AndroidViewModel {

    // A CompositeDisposable that keeps track of all of our asynchronous tasks
    private final CompositeDisposable _compositeDisposable = new CompositeDisposable();

    // Our BluetoothManager!
    private BluetoothManager _bluetoothManager;

    // Our Bluetooth Device! When disconnected it is null, so make sure we know that we need to deal with it potentially being null
    @Nullable
    private SimpleBluetoothDeviceInterface _deviceInterface;

    // The messages feed that the activity sees
    private final MutableLiveData<ConnectionStatus> _connectionStatusData = new MutableLiveData<>();
    // The device name that the activity sees
    private final MutableLiveData<String> _deviceNameData = new MutableLiveData<>();

    // Our configuration
    private String _mac;

    // A variable to help us not double-connect
    private boolean _connectionAttemptedOrMade = false;
    // A variable to help us not setup twice
    private boolean _viewModelSetup = false;

    private boolean _newMessage = false;
    private final Object _newMessageParsed = new Object();
    private String _message = "";
    private String _messageID = "";
    private int _expectedIsoTpBytes = -1;
    private boolean _retry = true;

    // Called by the system, this is just a constructor that matches AndroidViewModel.
    public CommunicateViewModel(@NonNull Application application) {
        super(application);
    }

    // Called in the activity's onCreate(). Checks if it has been called before, and if not, sets up the data.
    // Returns true if everything went okay, or false if there was an error and therefore the activity should finish.
    public boolean setupViewModel(String deviceName, String mac) {
        if (!_viewModelSetup) {
            _viewModelSetup = true;

            // Setup our BluetoothManager
            _bluetoothManager = BluetoothManager.getInstance();
            if (_bluetoothManager == null) {
                toast(R.string.bluetooth_unavailable);
                return false;
            }

            _mac = mac;
            _deviceNameData.postValue(deviceName);
            _connectionStatusData.postValue(ConnectionStatus.DISCONNECTED);
        }
        return true;
    }

    // Called when the user presses the connect button
    public void connect() {
        if (!_connectionAttemptedOrMade) {
            _connectionAttemptedOrMade = true;
            _connectionStatusData.postValue(ConnectionStatus.CONNECTING);

            _compositeDisposable.add(_bluetoothManager.openSerialDevice(_mac)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(device -> onConnected(device.toSimpleDeviceInterface()), t -> {
                        toast(R.string.connection_failed);
                        _connectionAttemptedOrMade = false;
                        _connectionStatusData.postValue(ConnectionStatus.RETRY);
                    }));
        }
    }

    // Called when the user presses the disconnect button
    public void disconnect() {
        _connectionAttemptedOrMade = false;
        if (_deviceInterface != null) {
            _bluetoothManager.closeDevice(_deviceInterface);
            _deviceInterface = null;
        }
        _connectionStatusData.postValue(ConnectionStatus.DISCONNECTED);
    }

    // Called once the library connects a bluetooth device
    private void onConnected(SimpleBluetoothDeviceInterface deviceInterface) {
        if (deviceInterface != null) {
            this._deviceInterface = deviceInterface;
            _connectionStatusData.postValue(ConnectionStatus.CONNECTED);
            this._deviceInterface.setListeners(this::onMessageReceived, this::onMessageSent, t -> toast(R.string.message_send_error));
            toast(R.string.connected);
        } else {
            this._deviceInterface = null;
            toast(R.string.connection_failed);
            _connectionAttemptedOrMade = false;
            _connectionStatusData.postValue(ConnectionStatus.DISCONNECTED);
        }
    }

    @SuppressWarnings("unused")
    private void onMessageSent(String message) {
    }

    // Adds a received message line from serial stream
    private void onMessageReceived(String rawMessage) {
        if (TextUtils.isEmpty(rawMessage)) {
            return;
        }
        String message = rawMessage.trim();
        if (message.isEmpty()) {
            return;
        }
        CanTrafficLogger.getInstance().logRx(message);

        synchronized (_newMessageParsed) {
            if (message.startsWith(">")) {
                String content = message.substring(1).trim();

                // 1. AT command responses or diagnostic adapter status
                if (content.equals("OK") || content.contains("OK")
                        || content.contains("ELM327") || content.contains("OBD")
                        || content.contains("NO DATA") || content.contains("CAN ERROR")
                        || content.contains("?") || content.contains("STOPPED")
                        || content.matches(".*\\d+\\.\\dV.*")) {
                    _message = content;
                    _messageID = content;
                    _newMessage = true;
                    _newMessageParsed.notifyAll();
                    return;
                }

                // 2. CAN frame with 29-bit CAN ID header: e.g. >18DAF1...
                if (content.length() >= 10) {
                    char pciType = content.charAt(8);
                    _message = trySubstring(message, 11);
                    _messageID = trySubstring(message, 11, 19);

                    // PCI byte starting with '0' indicates Single Frame (SF) - message is fully complete!
                    if (pciType == '0') {
                        _newMessage = true;
                        _newMessageParsed.notifyAll();
                        return;
                    } else if (pciType == '1' && content.length() >= 12) {
                        try {
                            _expectedIsoTpBytes = Integer.parseInt(content.substring(8, 12), 16) & 0x0FFF;
                        } catch (Exception ignored) {
                            _expectedIsoTpBytes = -1;
                        }
                    }
                } else {
                    _message = trySubstring(message, 11);
                    _messageID = trySubstring(message, 11, 19);
                }
            } else {
                // Multi-frame consecutive frame: strip 8-char CAN ID + 2-char sequence byte = 10 chars
                _message += trySubstring(message, 10);
            }

            // 3. Multi-frame end-of-message markers, ISO-TP length satisfied, or padding detection
            boolean lengthSatisfied = _expectedIsoTpBytes > 0 && (_message.length() >= (_expectedIsoTpBytes * 2));
            if (lengthSatisfied
                    || message.contains("5555")
                    || message.contains("OK") || message.contains("ELM327")
                    || message.matches(".*\\d+\\.\\dV.*")
                    || message.contains("NO DATA") || message.contains("CAN ERROR")) {
                _newMessage = true;
                _newMessageParsed.notifyAll();
            }
        }
    }

    // Tries to substring with beginIndex. If IndexOutOfBoundsException return complete String
    private String trySubstring(String message, int beginIndex) {
        try {
            return message.substring(beginIndex);
        } catch (IndexOutOfBoundsException ignored) {
            return message.length() > 1 ? message.substring(1) : "";
        }
    }

    private String trySubstring(String message, int beginIndex, int endIndex) {
        try {
            return message.substring(beginIndex, endIndex);
        } catch (IndexOutOfBoundsException ignored) {
            return message;
        }
    }

    // Send a message
    public void sendMessage(String message) {
        synchronized (_newMessageParsed) {
            _newMessage = false;
            _message = "";
            _messageID = "";
            _expectedIsoTpBytes = -1;
        }
        if (_deviceInterface != null && !TextUtils.isEmpty(message)) {
            CanTrafficLogger.getInstance().logTx(message.trim());
            _deviceInterface.sendMessage(message);
        }
    }

    // Called when the activity finishes - clear up after ourselves.
    @Override
    protected void onCleared() {
        _compositeDisposable.dispose();
        if (_bluetoothManager != null) {
            _bluetoothManager.close();
        }
    }

    // Helper method to create toast messages.
    private void toast(@StringRes int messageResource) {
        AppToast.show(getApplication(), messageResource, AppToast.LENGTH_LONG);
        if (messageResource == R.string.message_send_error) {
            disconnect();
        }
    }

    public LiveData<ConnectionStatus> getConnectionStatus() {
        return _connectionStatusData;
    }

    public LiveData<String> getDeviceName() {
        return _deviceNameData;
    }

    public String getMessage() {
        synchronized (_newMessageParsed) {
            return _message;
        }
    }

    public String getMessageID() {
        synchronized (_newMessageParsed) {
            return _messageID;
        }
    }

    public boolean isNewMessage() {
        synchronized (_newMessageParsed) {
            return _newMessage;
        }
    }

    public Object getNewMessageParsed() {
        return _newMessageParsed;
    }

    public void setNewMessageProcessed() {
        synchronized (_newMessageParsed) {
            _newMessage = false;
        }
    }

    public boolean isRetry() {
        return _retry;
    }

    public void setRetry(boolean retry) {
        this._retry = retry;
    }

    enum ConnectionStatus {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        RETRY
    }
}
