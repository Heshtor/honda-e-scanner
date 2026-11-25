package cz.helicode.hondaescanner.data.can

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.content.edit
import cz.helicode.hondaescanner.model.CanLogInfo
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Thread-safe diagnostic CAN/OBD traffic logger.
 * Automatically clears the can_log directory and creates a timestamped log file per session.
 */
object CanTrafficLogger {

    private const val TAG = "CanTrafficLogger"
    private const val DIR_NAME = "can_log"
    private const val PREFS_NAME = "can_traffic_logger_prefs"
    private const val KEY_LOG_START_TIME = "log_start_time_ms"
    private const val KEY_CURRENT_LOG_FILE = "current_log_filename"

    private var writer: PrintWriter? = null
    private var currentLogFile: File? = null
    private var currentLogStartDate: Date? = null

    private fun timeFormat() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private fun fileDateFormat() = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)

    private fun getBaseDir(context: Context): File {
        val baseDir = context.getExternalFilesDir(DIR_NAME) ?: File(context.filesDir, DIR_NAME)
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
        return baseDir
    }

    @Synchronized
    fun getLogFile(context: Context): File? {
        currentLogFile?.let { file ->
            if (file.exists() && file.length() > 0) return file
        }

        val baseDir = getBaseDir(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedFilename = prefs.getString(KEY_CURRENT_LOG_FILE, null)
        if (savedFilename != null) {
            val savedFile = File(baseDir, savedFilename)
            if (savedFile.exists() && savedFile.length() > 0) {
                currentLogFile = savedFile
                return savedFile
            }
        }

        val newest = baseDir.listFiles { _, name -> name.endsWith(".log") }
            ?.maxByOrNull { it.lastModified() }
        if (newest != null && newest.length() > 0) {
            currentLogFile = newest
            return newest
        }
        return null
    }

    @Synchronized
    fun startLogging(context: Context, vin: String?) {
        try {
            writer?.runCatching {
                flush()
                close()
            }
            writer = null

            val baseDir = getBaseDir(context)
            baseDir.listFiles()?.forEach { it.delete() }

            val now = Date()
            currentLogStartDate = now

            val filename = "Honda_e_${fileDateFormat().format(now)}.log"
            val logFile = File(baseDir, filename)
            currentLogFile = logFile

            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
                putLong(KEY_LOG_START_TIME, now.time)
                putString(KEY_CURRENT_LOG_FILE, filename)
            }

            val safeVin = if (!vin.isNullOrBlank() && vin != "HONDA_E") vin else "HONDA_E"

            writer = PrintWriter(FileWriter(logFile, false), true).apply {
                println("================================================================================")
                println("Honda e Scanner - CAN & OBD Diagnostic Communication Log")
                println("Start date & time: ${timeFormat().format(now)}")
                println("Vehicle: Honda e (Advance)")
                println("VIN: $safeVin")
                println("Legend: [TX] = query sent to adapter | [RX] = raw response from CAN bus")
                println("================================================================================")
                flush()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting CAN traffic logger", e)
        }
    }

    @Synchronized
    fun updateVin(vin: String?) {
        if (!vin.isNullOrBlank() && vin != "HONDA_E") {
            logInfo("Vehicle VIN detected: $vin")
        }
    }

    @Synchronized
    fun logTx(command: String?) {
        val w = writer ?: return
        if (command != null) {
            w.println("[${timeFormat().format(Date())}] [TX] $command")
        }
    }

    @Synchronized
    fun logRx(response: String?) {
        val w = writer ?: return
        if (response != null) {
            w.println("[${timeFormat().format(Date())}] [RX] $response")
        }
    }

    @Synchronized
    fun logInfo(info: String?) {
        val w = writer ?: return
        if (info != null) {
            w.println("[${timeFormat().format(Date())}] [INFO] $info")
        }
    }

    @Synchronized
    fun stopLogging() {
        val w = writer ?: return
        w.println("================================================================================")
        w.println("Logging stopped: ${timeFormat().format(Date())}")
        w.println("================================================================================")
        w.flush()
        w.close()
        writer = null
    }

    private fun getLogStartDate(context: Context, logFile: File): Date {
        currentLogStartDate?.let { return it }

        val name = logFile.name
        if (name.startsWith("Honda_e_") && name.endsWith(".log")) {
            val datePart = name.removePrefix("Honda_e_").removeSuffix(".log")
            runCatching { fileDateFormat().parse(datePart) }.getOrNull()?.let { parsed ->
                currentLogStartDate = parsed
                return parsed
            }
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val startMs = prefs.getLong(KEY_LOG_START_TIME, 0L)
        if (startMs > 0) {
            return Date(startMs).also { currentLogStartDate = it }
        }

        runCatching {
            BufferedReader(FileReader(logFile)).use { reader ->
                repeat(6) {
                    val line = reader.readLine() ?: return@repeat
                    if (line.startsWith("Start date & time: ")) {
                        val timeStr = line.removePrefix("Start date & time: ").trim()
                        val parsed = timeFormat().parse(timeStr)
                        if (parsed != null) {
                            currentLogStartDate = parsed
                            prefs.edit { putLong(KEY_LOG_START_TIME, parsed.time) }
                            return parsed
                        }
                    }
                }
            }
        }

        return Date(logFile.lastModified())
    }

    @Synchronized
    fun getLogInfo(context: Context): CanLogInfo {
        writer?.flush()
        val logFile = getLogFile(context)
        val isRecording = writer != null
        if (logFile == null || !logFile.exists() || logFile.length() == 0L) {
            return CanLogInfo(exists = false, isRecording = isRecording)
        }
        return CanLogInfo(
            exists = true,
            isRecording = isRecording,
            startDate = getLogStartDate(context, logFile),
            fileSizeBytes = logFile.length()
        )
    }

    @Synchronized
    fun getShareUri(context: Context): Uri? {
        writer?.flush()
        val logFile = getLogFile(context)
        if (logFile == null || !logFile.exists() || logFile.length() == 0L) return null
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            logFile
        )
    }
}
