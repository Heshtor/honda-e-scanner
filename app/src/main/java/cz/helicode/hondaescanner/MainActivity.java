package cz.helicode.hondaescanner;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 1001;

    private MainActivityViewModel _viewModel;
    private SharedPreferences _preferences;
    private static final String DEVICE_NAME = "device_name";
    private static final String DEVICE_MAC = "device_mac";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        _viewModel = new ViewModelProvider(this).get(MainActivityViewModel.class);

        if (!_viewModel.setupViewModel()) {
            finish();
            return;
        }

        _preferences = getPreferences(MODE_PRIVATE);

        RecyclerView deviceList = findViewById(R.id.main_devices);
        SwipeRefreshLayout swipeRefreshLayout = findViewById(R.id.main_swiperefresh);
        View emptyView = findViewById(R.id.main_empty_view);

        swipeRefreshLayout.setColorSchemeColors(ContextCompat.getColor(this, R.color.colorAccent));
        swipeRefreshLayout.setProgressBackgroundColorSchemeColor(ContextCompat.getColor(this, R.color.colorCard));

        deviceList.setLayoutManager(new LinearLayoutManager(this));
        DeviceAdapter adapter = new DeviceAdapter();
        deviceList.setAdapter(adapter);

        updateThemeStyles();

        swipeRefreshLayout.setOnRefreshListener(() -> {
            ThemeHelper.applyTheme(this);
            updateThemeStyles();
            checkAndRequestPermissions();
            _viewModel.refreshPairedDevices();
            swipeRefreshLayout.setRefreshing(false);
        });

        _viewModel.getPairedDeviceList().observe(MainActivity.this, devices -> {
            adapter.updateList(devices);
            if (emptyView != null) {
                emptyView.setVisibility((devices == null || devices.isEmpty()) ? View.VISIBLE : View.GONE);
            }
        });

        Button demoButton = findViewById(R.id.main_btn_demo);
        if (demoButton != null) {
            demoButton.setOnClickListener(v -> {
                Intent intent = new Intent(this, CommunicateActivity.class);
                intent.putExtra("device_name", "Honda e (Demo)");
                intent.putExtra("device_mac", "DEMO_MODE");
                intent.putExtra("is_demo", true);
                startActivity(intent);
            });
        }

        checkAndRequestPermissions();
        _viewModel.refreshPairedDevices();

        if (savedInstanceState == null) {
            new Thread(this::connectToLastConnectedDevice).start();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        ThemeHelper.applyTheme(this);
        updateThemeStyles();
        _viewModel.refreshPairedDevices();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        ThemeHelper.applyTheme(this);
        updateThemeStyles();
        RecyclerView deviceList = findViewById(R.id.main_devices);
        if (deviceList != null && deviceList.getAdapter() != null) {
            deviceList.getAdapter().notifyDataSetChanged();
        }
    }

    private void updateThemeStyles() {
        SwipeRefreshLayout swipeRefreshLayout = findViewById(R.id.main_swiperefresh);
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setColorSchemeColors(ContextCompat.getColor(this, R.color.colorAccent));
            swipeRefreshLayout.setProgressBackgroundColorSchemeColor(ContextCompat.getColor(this, R.color.colorCard));
        }
        CardView headerCard = findViewById(R.id.main_header_card);
        if (headerCard != null) {
            headerCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.colorCard));
        }
        CardView emptyCard = findViewById(R.id.main_empty_view);
        if (emptyCard != null) {
            emptyCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.colorCard));
        }
        CardView demoCard = findViewById(R.id.main_demo_card);
        if (demoCard != null) {
            demoCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.colorCard));
        }
    }

    private void checkAndRequestPermissions() {
        List<String> permissions = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_SCAN);
            }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        if (!permissions.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            _viewModel.refreshPairedDevices();
        }
    }

    public void connectToLastConnectedDevice() {
        try {
            Thread.sleep(300);
            String deviceName = _preferences.getString(DEVICE_NAME, null);
            String deviceMac = _preferences.getString(DEVICE_MAC, null);

            if (deviceName != null && deviceMac != null) {
                openCommunicationsActivity(deviceName, deviceMac);
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    public void openCommunicationsActivity(String deviceName, String macAddress) {
        final SharedPreferences.Editor editor = _preferences.edit();
        editor.putString(DEVICE_NAME, deviceName);
        editor.putString(DEVICE_MAC, macAddress);
        editor.apply();

        Intent intent = new Intent(this, CommunicateActivity.class);
        intent.putExtra("device_name", deviceName);
        intent.putExtra("device_mac", macAddress);
        startActivity(intent);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            getOnBackPressedDispatcher().onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private class DeviceViewHolder extends RecyclerView.ViewHolder {

        private final CardView _card;
        private final TextView _text1;
        private final TextView _text2;
        private final FrameLayout _iconBadge;
        private final ImageView _iconBluetooth;
        private final ImageView _iconChevron;

        DeviceViewHolder(@NonNull View view) {
            super(view);
            _card = (CardView) view.findViewById(R.id.list_item);
            _text1 = view.findViewById(R.id.list_item_text1);
            _text2 = view.findViewById(R.id.list_item_text2);
            _iconBadge = view.findViewById(R.id.list_item_badge);
            _iconBluetooth = view.findViewById(R.id.list_item_icon);
            _iconChevron = view.findViewById(R.id.list_item_chevron);
        }

        void setupView(BluetoothDevice device) {
            String name = getString(R.string.default_adapter_name);
            try {
                if (device.getName() != null && !device.getName().isEmpty()) {
                    name = device.getName();
                }
            } catch (SecurityException ignored) {
            }

            final String displayName = name;
            final String mac = device.getAddress();

            _text1.setText(displayName);
            _text2.setText(mac);

            if (_card != null) {
                _card.setCardBackgroundColor(ContextCompat.getColor(MainActivity.this, R.color.colorCard));
                _card.setOnClickListener(view -> openCommunicationsActivity(displayName, mac));
            }
            if (_text1 != null) {
                _text1.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.colorText));
            }
            if (_text2 != null) {
                _text2.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.colorTextSecondary));
            }
            if (_iconBluetooth != null) {
                _iconBluetooth.setColorFilter(ContextCompat.getColor(MainActivity.this, R.color.colorAccent));
            }
            if (_iconChevron != null) {
                _iconChevron.setColorFilter(ContextCompat.getColor(MainActivity.this, R.color.colorAccent));
            }
            if (_iconBadge != null) {
                _iconBadge.setBackground(ContextCompat.getDrawable(MainActivity.this, R.drawable.bg_icon_badge));
            }
        }
    }

    private class DeviceAdapter extends RecyclerView.Adapter<DeviceViewHolder> {
        private BluetoothDevice[] _deviceList = new BluetoothDevice[0];

        @NonNull
        @Override
        public DeviceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new DeviceViewHolder(LayoutInflater.from(MainActivity.this).inflate(R.layout.list_item, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull DeviceViewHolder holder, int position) {
            holder.setupView(_deviceList[position]);
        }

        @Override
        public int getItemCount() {
            return _deviceList.length;
        }

        @SuppressLint("NotifyDataSetChanged")
        void updateList(Collection<BluetoothDevice> deviceList) {
            if (deviceList != null) {
                _deviceList = deviceList.toArray(new BluetoothDevice[0]);
            } else {
                _deviceList = new BluetoothDevice[0];
            }
            notifyDataSetChanged();
        }
    }
}
