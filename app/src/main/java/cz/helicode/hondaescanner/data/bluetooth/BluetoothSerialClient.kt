package cz.helicode.hondaescanner.data.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import cz.helicode.hondaescanner.data.can.CanTrafficLogger
import cz.helicode.hondaescanner.model.BluetoothDeviceItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Native Kotlin Coroutines Bluetooth Classic SPP/RFCOMM serial client for ELM327 / vLinker OBD-II adapters.
 * Replaces legacy RxJava2 + android-bluetooth-serial libraries with zero third-party dependencies.
 */
class BluetoothSerialClient(private val context: Context) {

    companion object {
        private const val TAG = "BluetoothSerialClient"
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        manager?.adapter
    }

    private val ioMutex = Mutex()
    @Volatile
    private var socket: BluetoothSocket? = null
    @Volatile
    private var inputStream: InputStream? = null
    @Volatile
    private var outputStream: OutputStream? = null

    val isBluetoothAvailable: Boolean
        get() = bluetoothAdapter != null

    val isConnected: Boolean
        get() = socket?.isConnected == true

    @SuppressLint("MissingPermission")
    fun getPairedDevices(defaultName: String): List<BluetoothDeviceItem> {
        val adapter = bluetoothAdapter ?: return emptyList()
        return try {
            adapter.bondedDevices
                ?.map { device ->
                    val name = try {
                        device.name?.takeIf { it.isNotBlank() } ?: defaultName
                    } catch (_: SecurityException) {
                        defaultName
                    }
                    BluetoothDeviceItem(name = name, macAddress = device.address)
                }
                ?.sortedBy { it.name.lowercase() }
                ?: emptyList()
        } catch (e: SecurityException) {
            Log.w(TAG, "Missing Bluetooth permission when listing paired devices", e)
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun connect(macAddress: String): Result<Unit> = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            closeInternal()
            val adapter = bluetoothAdapter
                ?: return@withLock Result.failure(IOException("Bluetooth adapter unavailable"))

            try {
                runCatching { adapter.cancelDiscovery() }
                val device = adapter.getRemoteDevice(macAddress)
                val rfcommSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                rfcommSocket.connect()
                socket = rfcommSocket
                inputStream = rfcommSocket.inputStream
                outputStream = rfcommSocket.outputStream
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "RFCOMM connect failed for $macAddress", e)
                closeInternal()
                Result.failure(e)
            }
        }
    }

    /**
     * Sends an AT or UDS command over RFCOMM and reads raw lines until ELM327 emits its `>` prompt
     * or [timeoutMs] expires.
     */
    suspend fun sendCommandAndReadLines(
        command: String,
        timeoutMs: Long = 350L
    ): List<String> = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            val out = outputStream ?: throw IOException("Not connected")
            val input = inputStream ?: throw IOException("Not connected")

            // Drain any stale unread bytes before transmitting
            while (input.available() > 0) {
                input.read()
            }

            val trimmed = command.trim()
            CanTrafficLogger.logTx(trimmed)
            out.write("$trimmed\r".toByteArray(Charsets.US_ASCII))
            out.flush()

            val buffer = StringBuilder()
            val deadline = System.currentTimeMillis() + timeoutMs
            val chunk = ByteArray(512)

            while (System.currentTimeMillis() < deadline) {
                val avail = input.available()
                if (avail > 0) {
                    val readCount = input.read(chunk, 0, minOf(avail, chunk.size))
                    if (readCount == -1) {
                        throw IOException("Bluetooth stream closed by adapter")
                    }
                    if (readCount > 0) {
                        val text = String(chunk, 0, readCount, Charsets.US_ASCII)
                        buffer.append(text)
                        if (text.contains('>')) {
                            break
                        }
                    }
                } else {
                    Thread.sleep(5)
                }
            }

            val lines = buffer.toString()
                .replace("\r\n", "\r")
                .replace('\n', '\r')
                .split('\r')
                .map { it.trim().removePrefix(">").trim() }
                .filter { it.isNotEmpty() && it != ">" }

            lines.forEach { CanTrafficLogger.logRx(it) }
            lines
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            closeInternal()
        }
    }

    fun closeBlocking() {
        closeInternal()
    }

    private fun closeInternal() {
        runCatching { inputStream?.close() }
        runCatching { outputStream?.close() }
        runCatching { socket?.close() }
        inputStream = null
        outputStream = null
        socket = null
    }
}
