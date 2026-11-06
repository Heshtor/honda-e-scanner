package cz.helicode.hondaescanner;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
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
 * Continuously records all raw TX/RX commands and parsed telemetry events with millisecond timestamps.
 * Provides instant export to the user's public Downloads directory.
 */
public class CanTrafficLogger {

    private static final String TAG = "CanTrafficLogger";
    private static final CanTrafficLogger INSTANCE = new CanTrafficLogger();

    private PrintWriter _writer;
    private File _currentLogFile;
    private final SimpleDateFormat _timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
    private final SimpleDateFormat _fileDateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US);

    public static CanTrafficLogger getInstance() {
        return INSTANCE;
    }

    private CanTrafficLogger() {}

    public synchronized void startLogging(Context context, String vin) {
        try {
            if (_writer != null) {
                return;
            }
            File baseDir = context.getExternalFilesDir("can_logs");
            if (baseDir == null) {
                baseDir = context.getFilesDir();
            }
            if (!baseDir.exists()) {
                //noinspection ResultOfMethodCallIgnored
                baseDir.mkdirs();
            }

            // Archive any unfinalized live log from previous session with its last modified time
            File liveFile = new File(baseDir, "Honda_e_live.log");
            if (liveFile.exists() && liveFile.length() > 0) {
                Date prevDate = new Date(liveFile.lastModified());
                File archived = new File(baseDir, "Honda_e_" + _fileDateFormat.format(prevDate) + ".log");
                //noinspection ResultOfMethodCallIgnored
                liveFile.renameTo(archived);
            }

            _currentLogFile = liveFile;
            String safeVin = (vin != null && !vin.isEmpty() && !vin.equals("HONDA_E")) ? vin : "HONDA_E";

            _writer = new PrintWriter(new FileWriter(_currentLogFile, false), true);
            _writer.println("================================================================================");
            _writer.println("Honda e Scanner - CAN & OBD Diagnostic Communication Log");
            _writer.println("Start date & time: " + _timeFormat.format(new Date()));
            _writer.println("Vehicle: Honda e Advance");
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

            if (_currentLogFile != null && _currentLogFile.exists()) {
                File baseDir = _currentLogFile.getParentFile();
                File finalizedFile = new File(baseDir, "Honda_e_" + _fileDateFormat.format(now) + ".log");
                if (_currentLogFile.renameTo(finalizedFile)) {
                    _currentLogFile = finalizedFile;
                }
            }
        }
    }

    @SuppressWarnings("unused")
    public synchronized boolean hasActiveLog() {
        return _currentLogFile != null && _currentLogFile.exists() && _currentLogFile.length() > 0;
    }

    public synchronized Uri getShareUri(Context context) {
        if (_writer != null) {
            _writer.flush();
        }
        if (_currentLogFile == null || !_currentLogFile.exists()) {
            return null;
        }

        File fileToShare = _currentLogFile;
        Date now = new Date();
        File baseDir = _currentLogFile.getParentFile();
        File exportFile = new File(baseDir, "Honda_e_" + _fileDateFormat.format(now) + ".log");

        try {
            copyFile(_currentLogFile, exportFile);
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
     * Copies the active log file to the public Downloads folder so the user can easily access it.
     * @return Path or descriptive target location for displaying in a message.
     */
    @SuppressWarnings("unused")
    public synchronized String exportToDownloads(Context context) throws IOException {
        if (_writer != null) {
            _writer.flush();
        }
        if (_currentLogFile == null || !_currentLogFile.exists()) {
            throw new IOException("No CAN log file recorded yet.");
        }

        String exportName = "Honda_e_" + _fileDateFormat.format(new Date()) + ".log";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, exportName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Honda_e_Scanner");

            Uri uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                throw new IOException("MediaStore could not create file in Downloads.");
            }
            try (InputStream in = new FileInputStream(_currentLogFile);
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
            try (InputStream in = new FileInputStream(_currentLogFile);
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
