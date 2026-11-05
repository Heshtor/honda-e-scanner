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

            String safeVin = (vin != null && !vin.isEmpty() ? vin : "HONDA_E").replaceAll("[^a-zA-Z0-9_-]", "_");
            String fileName = "Honda_e_CAN_" + safeVin + "_" + _fileDateFormat.format(new Date()) + ".log";
            _currentLogFile = new File(baseDir, fileName);

            _writer = new PrintWriter(new FileWriter(_currentLogFile, true), true);
            _writer.println("================================================================================");
            _writer.println("Honda e Scanner - Diagnostický záznam komunikace CAN & OBD");
            _writer.println("Datum a čas spuštění: " + _timeFormat.format(new Date()));
            _writer.println("Vozidlo: Honda e Advance (2021)");
            _writer.println("VIN: " + safeVin);
            _writer.println("Vysvětlivky: [TX] = dotaz odeslaný adaptéru | [RX] = surová odpověď z CAN sběrnice");
            _writer.println("================================================================================");
            _writer.flush();
        } catch (Exception e) {
            Log.e(TAG, "Chyba při spuštění CAN traffic loggeru", e);
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
            _writer.println("================================================================================");
            _writer.println("Záznam ukončen: " + _timeFormat.format(new Date()));
            _writer.println("================================================================================");
            _writer.flush();
            _writer.close();
            _writer = null;
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
        return FileProvider.getUriForFile(
                context,
                context.getPackageName() + ".fileprovider",
                _currentLogFile
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
            throw new IOException("Zatím nebyl zaznamenán žádný CAN log soubor.");
        }

        String exportName = _currentLogFile.getName();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, exportName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Honda_e_Scanner");

            Uri uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                throw new IOException("Systém MediaStore nepovolil vytvořit soubor v Downloads.");
            }
            try (InputStream in = new FileInputStream(_currentLogFile);
                 OutputStream out = context.getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new IOException("Nelze otevřít výstupní proud pro zápis souboru.");
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                out.flush();
            }
            return "Stažené / Download / Honda_e_Scanner / " + exportName;
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
}
