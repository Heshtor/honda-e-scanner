package cz.helicode.hondaescanner;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import androidx.core.content.FileProvider;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Diagnostic CAN/OBD traffic logger.
 * Automatically clears the can_log folder and creates a timestamped log file
 * upon each vehicle connection session.
 */
public class CanTrafficLogger {

    private static final String TAG = "CanTrafficLogger";
    private static final CanTrafficLogger INSTANCE = new CanTrafficLogger();

    public static final String DIR_NAME = "can_log";
    private static final String PREFS_NAME = "can_traffic_logger_prefs";
    private static final String KEY_LOG_START_TIME = "log_start_time_ms";
    private static final String KEY_CURRENT_LOG_FILE = "current_log_filename";

    private PrintWriter _writer;
    private File _currentLogFile;
    private Date _currentLogStartDate;
    private final SimpleDateFormat _timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
    private final SimpleDateFormat _fileDateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US);

    public static CanTrafficLogger getInstance() {
        return INSTANCE;
    }

    private CanTrafficLogger() {}

    public File getBaseDir(Context context) {
        File baseDir = context.getExternalFilesDir(DIR_NAME);
        if (baseDir == null) {
            baseDir = new File(context.getFilesDir(), DIR_NAME);
        }
        if (!baseDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            baseDir.mkdirs();
        }
        return baseDir;
    }

    /**
     * Resolves the current or latest session log file in the can_log directory.
     */
    public synchronized File getLogFile(Context context) {
        if (_currentLogFile != null && _currentLogFile.exists() && _currentLogFile.length() > 0) {
            return _currentLogFile;
        }

        File baseDir = getBaseDir(context);
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String savedFilename = prefs.getString(KEY_CURRENT_LOG_FILE, null);
        if (savedFilename != null) {
            File savedFile = new File(baseDir, savedFilename);
            if (savedFile.exists() && savedFile.length() > 0) {
                _currentLogFile = savedFile;
                return _currentLogFile;
            }
        }

        File[] files = baseDir.listFiles((dir, name) -> name.endsWith(".log"));
        if (files != null && files.length > 0) {
            File newest = files[0];
            for (File f : files) {
                if (f.lastModified() > newest.lastModified()) {
                    newest = f;
                }
            }
            if (newest.length() > 0) {
                _currentLogFile = newest;
                return _currentLogFile;
            }
        }

        return null;
    }

    @Deprecated
    public File getLiveLogFile(Context context) {
        return getLogFile(context);
    }

    /**
     * Wipes all existing files in can_log and starts recording into a new timestamped log file.
     */
    public synchronized void startLogging(Context context, String vin) {
        try {
            if (_writer != null) {
                try {
                    _writer.flush();
                    _writer.close();
                } catch (Exception ignored) {}
                _writer = null;
            }

            File baseDir = getBaseDir(context);

            // Wipe entire contents of can_log folder before starting new log
            File[] existingFiles = baseDir.listFiles();
            if (existingFiles != null) {
                for (File file : existingFiles) {
                    //noinspection ResultOfMethodCallIgnored
                    file.delete();
                }
            }

            Date now = new Date();
            _currentLogStartDate = now;

            String filename = "Honda_e_" + _fileDateFormat.format(now) + ".log";
            _currentLogFile = new File(baseDir, filename);

            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit()
                    .putLong(KEY_LOG_START_TIME, now.getTime())
                    .putString(KEY_CURRENT_LOG_FILE, filename)
                    .apply();

            String safeVin = (vin != null && !vin.isEmpty() && !vin.equals("HONDA_E")) ? vin : "HONDA_E";

            _writer = new PrintWriter(new FileWriter(_currentLogFile, false), true);
            _writer.println("================================================================================");
            _writer.println("Honda e Scanner - CAN & OBD Diagnostic Communication Log");
            _writer.println("Start date & time: " + _timeFormat.format(now));
            _writer.println("Vehicle: Honda e (Advance)");
            _writer.println("VIN: " + safeVin);
            _writer.println("Legend: [TX] = query sent to adapter | [RX] = raw response from CAN bus");
            _writer.println("================================================================================");
            _writer.flush();
        } catch (Exception e) {
            Log.e(TAG, "Error starting CAN traffic logger", e);
        }
    }

    public synchronized void updateVin(String vin) {
        if (vin != null && !vin.isEmpty() && !vin.equals("HONDA_E")) {
            logInfo("Vehicle VIN detected: " + vin);
        }
    }

    public synchronized void logTx(String command) {
        if (_writer != null && command != null) {
            _writer.println("[" + _timeFormat.format(new Date()) + "] [TX] " + command);
        }
    }

    public synchronized void logRx(String response) {
        if (_writer != null && response != null) {
            _writer.println("[" + _timeFormat.format(new Date()) + "] [RX] " + response);
        }
    }

    public synchronized void logInfo(String info) {
        if (_writer != null && info != null) {
            _writer.println("[" + _timeFormat.format(new Date()) + "] [INFO] " + info);
        }
    }

    public synchronized void stopLogging() {
        if (_writer != null) {
            Date now = new Date();
            _writer.println("================================================================================");
            _writer.println("Logging stopped: " + _timeFormat.format(now));
            _writer.println("================================================================================");
            _writer.flush();
            _writer.close();
            _writer = null;
        }
    }

    public synchronized boolean isLoggingActive() {
        return _writer != null;
    }

    public synchronized boolean hasActiveLog(Context context) {
        File log = getLogFile(context);
        return log != null && log.exists() && log.length() > 0;
    }

    public Date getLogStartDate(Context context, File logFile) {
        if (_currentLogStartDate != null) {
            return _currentLogStartDate;
        }

        // Try extracting timestamp from filename "Honda_e_yyyy-MM-dd_HH-mm-ss.log"
        if (logFile != null) {
            String name = logFile.getName();
            if (name.startsWith("Honda_e_") && name.endsWith(".log")) {
                try {
                    String datePart = name.substring("Honda_e_".length(), name.length() - ".log".length());
                    Date parsedName = _fileDateFormat.parse(datePart);
                    if (parsedName != null) {
                        _currentLogStartDate = parsedName;
                        return _currentLogStartDate;
                    }
                } catch (Exception ignored) {}
            }
        }

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long startMs = prefs.getLong(KEY_LOG_START_TIME, 0L);
        if (startMs > 0) {
            _currentLogStartDate = new Date(startMs);
            return _currentLogStartDate;
        }

        if (logFile != null && logFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
                String line;
                int count = 0;
                while ((line = reader.readLine()) != null && count < 6) {
                    if (line.startsWith("Start date & time: ")) {
                        String timeStr = line.substring("Start date & time: ".length()).trim();
                        Date parsed = _timeFormat.parse(timeStr);
                        if (parsed != null) {
                            _currentLogStartDate = parsed;
                            prefs.edit().putLong(KEY_LOG_START_TIME, parsed.getTime()).apply();
                            return parsed;
                        }
                    }
                    count++;
                }
            } catch (Exception ignored) {
            }
            return new Date(logFile.lastModified());
        }

        return new Date();
    }

    public static class LogInfo {
        public final boolean exists;
        public final boolean isRecording;
        public final Date startDate;
        public final long fileSizeBytes;

        public LogInfo(boolean exists, boolean isRecording, Date startDate, long fileSizeBytes) {
            this.exists = exists;
            this.isRecording = isRecording;
            this.startDate = startDate;
            this.fileSizeBytes = fileSizeBytes;
        }

        public String getFormattedSize() {
            if (fileSizeBytes < 1024) {
                return fileSizeBytes + " B";
            } else if (fileSizeBytes < 1024 * 1024) {
                return String.format(Locale.ENGLISH, "%.1f KB", fileSizeBytes / 1024.0);
            } else {
                return String.format(Locale.ENGLISH, "%.2f MB", fileSizeBytes / (1024.0 * 1024.0));
            }
        }
    }

    public synchronized LogInfo getLogInfo(Context context) {
        if (_writer != null) {
            _writer.flush();
        }
        File logFile = getLogFile(context);
        boolean exists = (logFile != null && logFile.exists() && logFile.length() > 0);
        boolean isRecording = (_writer != null);
        if (!exists) {
            return new LogInfo(false, isRecording, null, 0);
        }
        Date start = getLogStartDate(context, logFile);
        return new LogInfo(true, isRecording, start, logFile.length());
    }

    public synchronized Uri getShareUri(Context context) {
        if (_writer != null) {
            _writer.flush();
        }
        File logFile = getLogFile(context);
        if (logFile == null || !logFile.exists() || logFile.length() == 0) {
            return null;
        }

        return FileProvider.getUriForFile(
                context,
                context.getPackageName() + ".fileprovider",
                logFile
        );
    }

    /**
     * Copies the active log file to the public Downloads folder.
     * @return Path or descriptive target location for displaying in a message.
     */
    @SuppressWarnings("unused")
    public synchronized String exportToDownloads(Context context) throws IOException {
        if (_writer != null) {
            _writer.flush();
        }
        File logFile = getLogFile(context);
        if (logFile == null || !logFile.exists() || logFile.length() == 0) {
            throw new IOException("No CAN log file recorded yet.");
        }

        String exportName = logFile.getName();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, exportName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Honda_e_Scanner");

            Uri uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                throw new IOException("MediaStore could not create file in Downloads.");
            }
            try (InputStream in = new FileInputStream(logFile);
                 OutputStream out = context.getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new IOException("Cannot open output stream for writing file.");
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                out.flush();
            }
            return "Downloads / Honda_e_Scanner / " + exportName;
        } else {
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File targetDir = new File(downloadsDir, "Honda_e_Scanner");
            if (!targetDir.exists()) {
                //noinspection ResultOfMethodCallIgnored
                targetDir.mkdirs();
            }
            File targetFile = new File(targetDir, exportName);
            try (InputStream in = new FileInputStream(logFile);
                 OutputStream out = new FileOutputStream(targetFile)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                out.flush();
            }
            return targetFile.getAbsolutePath();
        }
    }
}
