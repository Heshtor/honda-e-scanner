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
 * Continuously records raw CAN communication into a single live file (Honda_e_live.log).
 * Reclaims disk space by overwriting on new vehicle connections and avoiding unneeded file clones.
 */
public class CanTrafficLogger {

    private static final String TAG = "CanTrafficLogger";
    private static final CanTrafficLogger INSTANCE = new CanTrafficLogger();

    public static final String LIVE_LOG_FILENAME = "Honda_e_live.log";
    private static final String PREFS_NAME = "can_traffic_logger_prefs";
    private static final String KEY_LOG_START_TIME = "log_start_time_ms";

    private PrintWriter _writer;
    private File _currentLogFile;
    private Date _currentLogStartDate;
    private final SimpleDateFormat _timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
    private final SimpleDateFormat _fileDateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US);

    public static CanTrafficLogger getInstance() {
        return INSTANCE;
    }

    private CanTrafficLogger() {}

    private File getBaseDir(Context context) {
        File baseDir = context.getExternalFilesDir("can_logs");
        if (baseDir == null) {
            baseDir = context.getFilesDir();
        }
        if (!baseDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            baseDir.mkdirs();
        }
        return baseDir;
    }

    public File getLiveLogFile(Context context) {
        return new File(getBaseDir(context), LIVE_LOG_FILENAME);
    }

    public synchronized void startLogging(Context context, String vin) {
        try {
            if (_writer != null) {
                return;
            }

            File baseDir = getBaseDir(context);
            _currentLogFile = new File(baseDir, LIVE_LOG_FILENAME);

            Date now = new Date();
            _currentLogStartDate = now;

            // Save start time to SharedPreferences so it persists across app restarts / crashes
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putLong(KEY_LOG_START_TIME, now.getTime()).apply();

            String safeVin = (vin != null && !vin.isEmpty() && !vin.equals("HONDA_E")) ? vin : "HONDA_E";

            // Overwrite Honda_e_live.log from scratch (append = false)
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
            // Note: Honda_e_live.log is NOT renamed. It remains intact for sharing until the next vehicle connection.
        }
    }

    public synchronized boolean isLoggingActive() {
        return _writer != null;
    }

    public synchronized boolean hasActiveLog(Context context) {
        File live = getLiveLogFile(context);
        return live.exists() && live.length() > 0;
    }

    public Date getLogStartDate(Context context, File liveFile) {
        if (_currentLogStartDate != null) {
            return _currentLogStartDate;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long startMs = prefs.getLong(KEY_LOG_START_TIME, 0L);
        if (startMs > 0) {
            _currentLogStartDate = new Date(startMs);
            return _currentLogStartDate;
        }

        // Fallback: Try reading start time from file header
        if (liveFile != null && liveFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(liveFile))) {
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
            return new Date(liveFile.lastModified());
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
        File live = getLiveLogFile(context);
        boolean exists = live.exists() && live.length() > 0;
        boolean isRecording = (_writer != null);
        if (!exists) {
            return new LogInfo(false, isRecording, null, 0);
        }
        Date start = getLogStartDate(context, live);
        return new LogInfo(true, isRecording, start, live.length());
    }

    public synchronized Uri getShareUri(Context context) {
        if (_writer != null) {
            _writer.flush();
        }
        File liveFile = getLiveLogFile(context);
        if (!liveFile.exists() || liveFile.length() == 0) {
            return null;
        }

        Date startDate = getLogStartDate(context, liveFile);
        File baseDir = getBaseDir(context);
        String exportName = "Honda_e_" + _fileDateFormat.format(startDate) + ".log";
        File exportFile = new File(baseDir, exportName);

        File fileToShare = liveFile;
        try {
            copyFile(liveFile, exportFile);
            fileToShare = exportFile;
        } catch (IOException e) {
            Log.e(TAG, "Error copying log for share", e);
        }

        return FileProvider.getUriForFile(
                context,
                context.getPackageName() + ".fileprovider",
                fileToShare
        );
    }

    /**
     * Copies the active log file to the public Downloads folder with the log's start date.
     * @return Path or descriptive target location for displaying in a message.
     */
    @SuppressWarnings("unused")
    public synchronized String exportToDownloads(Context context) throws IOException {
        if (_writer != null) {
            _writer.flush();
        }
        File liveFile = getLiveLogFile(context);
        if (!liveFile.exists() || liveFile.length() == 0) {
            throw new IOException("No CAN log file recorded yet.");
        }

        Date startDate = getLogStartDate(context, liveFile);
        String exportName = "Honda_e_" + _fileDateFormat.format(startDate) + ".log";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, exportName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Honda_e_Scanner");

            Uri uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                throw new IOException("MediaStore could not create file in Downloads.");
            }
            try (InputStream in = new FileInputStream(liveFile);
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
            try (InputStream in = new FileInputStream(liveFile);
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

    private void copyFile(File src, File dst) throws IOException {
        try (InputStream in = new FileInputStream(src);
             OutputStream out = new FileOutputStream(dst)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
        }
    }
}
