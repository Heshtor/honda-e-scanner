package cz.helicode.hondaescanner;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.os.LocaleListCompat;
import androidx.lifecycle.ViewModelProvider;

import java.io.File;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommunicateActivity extends AppCompatActivity implements LocationListener {

    private static final String TAG = "CommunicateActivity";

    private static final int CAN_BUS_SCAN_INTERVAL = 1000;
    private static final int WAIT_FOR_NEW_MESSAGE_TIMEOUT = 250;
    private static final int WAIT_TIME_BETWEEN_COMMAND_SENDS_MS = 40;

    // Service 0x22 / 0x62 Diagnostic Identifiers (DIDs)
    private static final String VIN_ID = "62F190";
    private static final String AMBIENT_ID = "627028";
    private static final String ODO_ID = "627022";
    private static final String RANGE_DASH_ID = "62702A";
    private static final String SOH_ID = "622021";
    private static final String SOC_ID = "622029";
    private static final String CELLS_V_ID = "622028";
    private static final String BAT_MODULE_TEMPS_ID = "62202C";
    private static final String BATTEMP_ID = "62202A";
    private static final String MOTOR_RPM_ID = "622001";

    private static final int MAX_RETRY = 5;

    private static final String NOTIFICATION_CHANNEL_ID = "SoC";
    private static final int NOTIFICATION_ID = 23;

    // Honda e usable battery capacity when new (net kWh)
    private static final double HONDA_E_NET_CAPACITY_KWH = 28.5;

    private final List<String> _connectionCommands = Arrays.asList(
            "ATWS",
            "ATE0",
            "ATSP7",
            "ATAT1",
            "ATH1",
            "ATL0",
            "ATS0",
            "ATRV",
            "ATAL",
            "ATCAF1",
            "ATSHDA01F1",
            "ATFCSH18DA01F1",
            "ATFCSD300000",
            "ATFCSM1",
            "ATCFC1",
            "ATCP18",
            "ATSHDA07F1",
            "ATFCSH18DA07F1",
            "ATCRA18DAF107",
            "22F190" // VIN
    );

    private final List<String> _loopCommands = Arrays.asList(
            // --- 1. METER / CLUSTER (ECU 60) ---
            "ATSHDA60F1",
            "ATFCSH18DA60F1",
            "ATCRA18DAF160",
            "227028",        // Ambient temperature (°C)
            "227022",        // ODO / Odometer in km
            "22702A",        // Instrument cluster dashboard range in km

            // --- 2. BMS / TRACTION BATTERY (ECU 15) ---
            "ATSHDA15F1",
            "ATFCSH18DA15F1",
            "ATCRA18DAF115",
            "222021",        // SOH (%), Pack Voltage (V), Current (A), Power (kW)
            "222029",        // SOC (%), Charging status (AC/DC)

            // --- 3. POWERTRAIN / VCU / MOTOR (ECU 01) ---
            "ATSHDA01F1",
            "ATFCSH18DA01F1",
            "ATCRA18DAF101",
            "222028",        // 96 individual cell voltages (0.0002 V/bit)
            "22202C",        // 6 battery module temperatures (°C)
            "22202A",        // Traction battery telemetry / temperatures on ECU 01

            // --- 4. 12V AUXILIARY BATTERY ---
            "ATRV"           // 12V auxiliary system voltage
    );

    private TextView _connectionText, _vinText, _messageText;
    private TextView _rangeDashText;
    private TextView _socDashText, _socCellsText, _socDeltaText, _sohText;
    private TextView _energyText, _cellVoltText, _cellDeltaText;
    private TextView _chargingText, _chargePowerText;
    private TextView _kwText, _consumptionText, _ampText, _voltText;
    private TextView _batTempText, _batTempRangeText, _batTempDeltaText, _ambientTempText, _invTempText, _auxBatText;
    private TextView _odoText, _speedText, _gearText;
    private TextView _latText, _lonText, _elevationText;

    private Button _connectButton, _demoButton, _shareLogButton;
    private Button _btnShowCells, _btnShowModuleTemps;
    private boolean _isDemoMode = false;

    // Navigation Tabs
    private View _containerLiveData, _containerSohTest, _containerSettings;
    private LinearLayout _tabOverview, _tabSohTest, _tabSettings;
    private TextView _tabOverviewText, _tabSohTestText, _tabSettingsText;
    private View _tabOverviewIndicator, _tabSohTestIndicator, _tabSettingsIndicator;
    private int _selectedTabIndex = 0;

    // Settings Tab Views
    private RadioGroup _settingsLanguageGroup, _settingsThemeGroup;
    private RadioButton _settingsLangCs, _settingsLangEn;
    private RadioButton _settingsThemeDark, _settingsThemeLight, _settingsThemeSystem;
    private TextView _logStatusText;
    private View _btnIconWhite, _btnIconBlack, _btnIconGrey, _btnIconRed, _btnIconBlue, _btnIconYellow;
    private TextView _textIconWhite, _textIconBlack, _textIconGrey, _textIconRed, _textIconBlue, _textIconYellow;

    // Battery SoH Test Mode
    private TextView _sohTestStatusText;
    private Button _btnToggleSohTest;
    private CardView _cardSohTestLive, _cardSohTestResults;
    private TextView _sohTestLiveTimeText, _sohTestLiveSamplesText, _sohTestLiveEnergyText, _sohTestLiveDeltaSocText, _sohTestLiveDistanceText, _sohTestLivePowerText;
    private TextView _sohTestResultSohText, _sohTestResultCnettoText, _sohTestResultDeltaSocText;
    private TextView _sohTestResultEnergyText, _sohTestResultDischargeRegenText, _sohTestResultDistanceText, _sohTestResultConsumptionText, _sohTestResultDurationText;

    // Live Dialog References for 96 Cells & 6 Module Temps
    private AlertDialog _cellsDialog = null;
    private TextView _cellsDialogTextView = null;
    private AlertDialog _moduleTempsDialog = null;
    private TextView _moduleTempsDialogTextView = null;

    private volatile boolean _isSohTestRunning = false;
    private volatile boolean _isSohTestStopping = false;
    private boolean _isEcu15Configured = false;
    private long _sohTestStartSysTimeMs = 0;
    private long _sohTestLastElapsedRealtimeMs = 0;
    private double _sohTestStartSoc = 0.0;
    private double _sohTestStopSoc = 0.0;
    private int _sohTestStartOdo = 0;
    private int _sohTestStopOdo = 0;
    private double _sohTestTotalNetEnergyKwh = 0.0;
    private double _sohTestTotalDischargeKwh = 0.0;
    private double _sohTestTotalRegenKwh = 0.0;
    private double _sohTestGpsDistanceKm = 0.0;
    private Location _sohTestLastGpsLocation;
    private int _sohTestSampleCount = 0;
    private int _sohTestCycleCounter = 0;
    private PowerManager.WakeLock _wakeLock = null;
    private Handler _demoLoopHandler = null;
    private Runnable _demoLoopRunnable = null;
    private int _demoTickCounter = 0;
    private double _demoSimulatedDistanceKm = 0.0;

    // Telemetry variables
    private double _soc, _socMin, _socMax, _socDelta, _soh, _capacityAh;
    private double _usableEnergyKwh;
    private double _speed, _power, _instantConsumption;
    private double _batTemp = -100.0, _batTempMin = -100.0, _batTempMax = -100.0, _batTempSpread;
    private double _amp, _volt, _auxBat;
    private int _rangeFromDash;
    private int _invTemp, _motorRpm, _speedFromCan;
    private byte _ambientTemp = -100;

    // Cell voltage metrics (real 96 cells from DID 0x2028)
    private final double[] _cellVoltages = new double[96];
    private double _cellMinV = 0.0, _cellMaxV = 0.0, _cellAvgV = 0.0;
    private int _cellMinMv, _cellMaxMv, _cellDeltaMv;

    // Battery module temperature metrics (6 sensors from DID 0x202C)
    private final double[] _moduleTemps = new double[6];
    private int _odo;

    private String _vin = "HONDA_E";
    private String _lat = "", _lon = "";
    private double _elevation;

    private ChargingConnection _chargingConnection = ChargingConnection.NC;
    private boolean _isCharging = false;

    private long _sysTimeMs;
    private long _epoch, _lastEpochNotification;

    private CommunicateViewModel _viewModel;
    private volatile boolean _loopRunning = false;
    private volatile int _retries = 0;
    private boolean _carConnected = false;
    private int _newMessagesCount = 0;

    private NotificationCompat.Builder _notificationBuilder;
    private NotificationManagerCompat _notificationManagerCompat;
    private LocationManager _locationManager;
    private Location _lastLocation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        AppIconHelper.applyTaskDescription(this);
        setContentView(R.layout.activity_communicate);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // Handle modern Back navigation
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (_isSohTestRunning) {
                    AppToast.show(CommunicateActivity.this, R.string.soh_test_locked_toast);
                    return;
                }
                _loopRunning = false;
                finish();
            }
        });

        // Initialize ViewModel
        _viewModel = new ViewModelProvider(this).get(CommunicateViewModel.class);
        String deviceName = getIntent().getStringExtra("device_name");
        String deviceMac = getIntent().getStringExtra("device_mac");
        boolean isDemo = getIntent().getBooleanExtra("is_demo", false);

        if (savedInstanceState != null) {
            _selectedTabIndex = savedInstanceState.getInt("active_tab", 0);
            boolean wasDemo = savedInstanceState.getBoolean("is_demo_mode", false);
            if (wasDemo) {
                isDemo = true;
            }
        }

        if (!isDemo && !_viewModel.setupViewModel(deviceName, deviceMac)) {
            _loopRunning = false;
            finish();
            return;
        }

        // Setup views
        initViews();
        selectTab(_selectedTabIndex);

        SharedPreferences appPrefs = getSharedPreferences(ThemeHelper.PREF_NAME, Context.MODE_PRIVATE);
        String savedVin = appPrefs.getString("vehicle_vin", null);
        if (savedVin != null && !savedVin.isEmpty()) {
            _vin = savedVin;
            setText(_vinText, _vin);
        }

        // Notification setup
        createNotificationChannel();
        int iconRes = AppIconHelper.getSelectedIconDrawableId(this);

        Intent contentIntent = new Intent(this, CommunicateActivity.class);
        contentIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        _notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(iconRes)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.connected))
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW);
        _notificationManagerCompat = NotificationManagerCompat.from(this);

        // ViewModel observers
        if (!isDemo) {
            _viewModel.getConnectionStatus().observe(this, this::onConnectionStatus);
            _viewModel.getDeviceName().observe(this, name -> setTitle(getString(R.string.device_name_format, name)));
        } else {
            if (deviceName != null) {
                setTitle(getString(R.string.device_name_format, deviceName));
            }
        }

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Location manager setup
        setupLocationUpdates();

        if (isDemo) {
            enableDemoMode();
        }
    }

    private void initViews() {
        _connectionText = findViewById(R.id.communicate_connection_text);
        _messageText = findViewById(R.id.communicate_message);
        _connectButton = findViewById(R.id.communicate_connect);
        _demoButton = findViewById(R.id.communicate_demo);
        _shareLogButton = findViewById(R.id.communicate_share_log);
        _logStatusText = findViewById(R.id.communicate_log_status);

        if (_demoButton != null) {
            _demoButton.setOnClickListener(v -> toggleDemoMode());
        }
        if (_shareLogButton != null) {
            _shareLogButton.setOnClickListener(v -> handleShareLog());
        }
        updateLogStatusUI();

        // Navigation Tabs
        _containerLiveData = findViewById(R.id.container_live_data);
        _containerSohTest = findViewById(R.id.container_soh_test);
        _containerSettings = findViewById(R.id.container_settings);
        _tabOverview = findViewById(R.id.tab_overview);
        _tabSohTest = findViewById(R.id.tab_soh_test);
        _tabSettings = findViewById(R.id.tab_settings);
        _tabOverviewText = findViewById(R.id.tab_overview_text);
        _tabSohTestText = findViewById(R.id.tab_soh_test_text);
        _tabSettingsText = findViewById(R.id.tab_settings_text);
        _tabOverviewIndicator = findViewById(R.id.tab_overview_indicator);
        _tabSohTestIndicator = findViewById(R.id.tab_soh_test_indicator);
        _tabSettingsIndicator = findViewById(R.id.tab_settings_indicator);

        if (_tabOverview != null) {
            _tabOverview.setOnClickListener(v -> selectTab(0));
        }
        if (_tabSohTest != null) {
            _tabSohTest.setOnClickListener(v -> selectTab(1));
        }
        if (_tabSettings != null) {
            _tabSettings.setOnClickListener(v -> selectTab(2));
        }

        // Settings Tab Views
        _settingsLanguageGroup = findViewById(R.id.settings_language_group);
        _settingsLangCs = findViewById(R.id.settings_lang_cs);
        _settingsLangEn = findViewById(R.id.settings_lang_en);

        if (_settingsLanguageGroup != null) {
            LocaleListCompat currentLocales = AppCompatDelegate.getApplicationLocales();
            boolean isEnglish = (!currentLocales.isEmpty() && currentLocales.toLanguageTags().startsWith("en"))
                    || (currentLocales.isEmpty() && Locale.getDefault().getLanguage().equalsIgnoreCase("en"));

            if (isEnglish) {
                if (_settingsLangEn != null) _settingsLangEn.setChecked(true);
            } else {
                if (_settingsLangCs != null) _settingsLangCs.setChecked(true);
            }

            _settingsLanguageGroup.setOnCheckedChangeListener((group, checkedId) -> {
                if (checkedId == R.id.settings_lang_en) {
                    if (!isEnglish) {
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"));
                    }
                } else if (checkedId == R.id.settings_lang_cs) {
                    if (isEnglish) {
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("cs"));
                    }
                }
            });
        }

        _settingsThemeGroup = findViewById(R.id.settings_theme_group);
        _settingsThemeDark = findViewById(R.id.settings_theme_dark);
        _settingsThemeLight = findViewById(R.id.settings_theme_light);
        _settingsThemeSystem = findViewById(R.id.settings_theme_system);

        if (_settingsThemeGroup != null) {
            _settingsThemeGroup.setOnCheckedChangeListener(null);

            int currentThemeMode = ThemeHelper.getSavedThemeMode(this);
            if (currentThemeMode == AppCompatDelegate.MODE_NIGHT_NO) {
                if (_settingsThemeLight != null) _settingsThemeLight.setChecked(true);
            } else if (currentThemeMode == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) {
                if (_settingsThemeSystem != null) _settingsThemeSystem.setChecked(true);
            } else {
                if (_settingsThemeDark != null) _settingsThemeDark.setChecked(true);
            }

            _settingsThemeGroup.setOnCheckedChangeListener((group, checkedId) -> {
                int targetMode;
                if (checkedId == R.id.settings_theme_light) {
                    targetMode = AppCompatDelegate.MODE_NIGHT_NO;
                } else if (checkedId == R.id.settings_theme_system) {
                    targetMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
                } else {
                    targetMode = AppCompatDelegate.MODE_NIGHT_YES;
                }

                if (ThemeHelper.getSavedThemeMode(this) != targetMode) {
                    ThemeHelper.setSavedThemeMode(this, targetMode);
                }
            });
        }

        Button githubButton = findViewById(R.id.settings_btn_github);
        if (githubButton != null) {
            githubButton.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Heshtor/honda-e-scanner"));
                    startActivity(intent);
                } catch (Exception e) {
                    Log.e(TAG, "Cannot open browser for GitHub", e);
                }
            });
        }

        View websiteRow = findViewById(R.id.about_website_row);
        if (websiteRow != null) {
            websiteRow.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://helicode.cz"));
                    startActivity(intent);
                } catch (Exception e) {
                    Log.e(TAG, "Cannot open browser for Website", e);
                }
            });
        }

        initAppIconSettings();

        TextView aboutAppText = findViewById(R.id.about_app_value_text);
        if (aboutAppText != null) {
            aboutAppText.setText(getString(R.string.about_app_value_format, BuildConfig.VERSION_NAME));
        }

        // Battery & Range Card
        _rangeDashText = findViewById(R.id.communicate_range_dash);
        _socDashText = findViewById(R.id.communicate_soc_dash);
        _socCellsText = findViewById(R.id.communicate_soc_cells);
        _socDeltaText = findViewById(R.id.communicate_soc_delta);
        _sohText = findViewById(R.id.communicate_soh);
        _energyText = findViewById(R.id.communicate_energy);
        _cellVoltText = findViewById(R.id.communicate_cell_volt);
        _cellDeltaText = findViewById(R.id.communicate_cell_delta);

        _btnShowCells = findViewById(R.id.btn_show_cells);
        if (_btnShowCells != null) {
            _btnShowCells.setOnClickListener(v -> showAllCellsDialog());
        }

        // Power & Charging Card
        _chargingText = findViewById(R.id.communicate_charging_connection);
        _chargePowerText = findViewById(R.id.communicate_charge_power);
        _kwText = findViewById(R.id.communicate_kw);
        _consumptionText = findViewById(R.id.communicate_consumption);
        _ampText = findViewById(R.id.communicate_amp);
        _voltText = findViewById(R.id.communicate_volt);

        // Temperature & Diagnostics Card
        _batTempText = findViewById(R.id.communicate_battemp);
        _batTempRangeText = findViewById(R.id.communicate_battemp_range);
        _batTempDeltaText = findViewById(R.id.communicate_battemp_delta);
        _ambientTempText = findViewById(R.id.communicate_ambient_temp);
        _invTempText = findViewById(R.id.communicate_inv_temp);
        _auxBatText = findViewById(R.id.communicate_aux_bat);

        _btnShowModuleTemps = findViewById(R.id.btn_show_module_temps);
        if (_btnShowModuleTemps != null) {
            _btnShowModuleTemps.setOnClickListener(v -> showModuleTempsDialog());
        }

        // Vehicle Info Card
        _odoText = findViewById(R.id.communicate_odo);
        _speedText = findViewById(R.id.communicate_speed);
        _gearText = findViewById(R.id.communicate_gear);
        _latText = findViewById(R.id.communicate_lat);
        _lonText = findViewById(R.id.communicate_lon);
        _elevationText = findViewById(R.id.communicate_elevation);
        _vinText = findViewById(R.id.communicate_vin);

        // SoH Test Controls
        _sohTestStatusText = findViewById(R.id.soh_test_status_text);
        _btnToggleSohTest = findViewById(R.id.soh_test_btn_toggle);

        if (_btnToggleSohTest != null) {
            _btnToggleSohTest.setOnClickListener(v -> onToggleSohTestClicked());
        }

        // SoH Test Live Card
        _cardSohTestLive = findViewById(R.id.card_soh_test_live);
        _sohTestLiveTimeText = findViewById(R.id.soh_test_live_time);
        _sohTestLiveSamplesText = findViewById(R.id.soh_test_live_samples);
        _sohTestLiveEnergyText = findViewById(R.id.soh_test_live_energy);
        _sohTestLiveDeltaSocText = findViewById(R.id.soh_test_live_deltasoc);
        _sohTestLiveDistanceText = findViewById(R.id.soh_test_live_distance);
        _sohTestLivePowerText = findViewById(R.id.soh_test_live_power);

        // SoH Test Results Card
        _cardSohTestResults = findViewById(R.id.card_soh_test_results);
        _sohTestResultSohText = findViewById(R.id.soh_test_result_soh);
        _sohTestResultCnettoText = findViewById(R.id.soh_test_result_cnetto);
        _sohTestResultDeltaSocText = findViewById(R.id.soh_test_result_deltasoc);
        _sohTestResultEnergyText = findViewById(R.id.soh_test_result_energy);
        _sohTestResultDischargeRegenText = findViewById(R.id.soh_test_result_discharge_regen);
        _sohTestResultDistanceText = findViewById(R.id.soh_test_result_distance);
        _sohTestResultConsumptionText = findViewById(R.id.soh_test_result_consumption);
        _sohTestResultDurationText = findViewById(R.id.soh_test_result_duration);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("active_tab", _selectedTabIndex);
        outState.putBoolean("is_demo_mode", _isDemoMode);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ThemeHelper.applyTheme(this);
        AppIconHelper.applyTaskDescription(this);
        updateLogStatusUI();
        updateAppIconSelectionUI(AppIconHelper.getSelectedIcon(this));
    }

    private void selectTab(int tabIndex) {
        _selectedTabIndex = tabIndex;

        if (_containerLiveData != null) _containerLiveData.setVisibility(tabIndex == 0 ? View.VISIBLE : View.GONE);
        if (_containerSohTest != null) _containerSohTest.setVisibility(tabIndex == 1 ? View.VISIBLE : View.GONE);
        if (_containerSettings != null) _containerSettings.setVisibility(tabIndex == 2 ? View.VISIBLE : View.GONE);

        // Tab 1: Overview
        if (_tabOverviewText != null) {
            _tabOverviewText.setTextColor(ContextCompat.getColor(this, tabIndex == 0 ? R.color.colorAccent : R.color.colorTextSecondary));
        }
        if (_tabOverviewIndicator != null) {
            _tabOverviewIndicator.setBackgroundColor(ContextCompat.getColor(this, tabIndex == 0 ? R.color.colorAccent : android.R.color.transparent));
        }

        // Tab 2: SOH Test
        if (_tabSohTestText != null) {
            _tabSohTestText.setTextColor(ContextCompat.getColor(this, tabIndex == 1 ? R.color.colorAccent : R.color.colorTextSecondary));
        }
        if (_tabSohTestIndicator != null) {
            _tabSohTestIndicator.setBackgroundColor(ContextCompat.getColor(this, tabIndex == 1 ? R.color.colorAccent : android.R.color.transparent));
        }

        // Tab 3: Settings
        if (_tabSettingsText != null) {
            _tabSettingsText.setTextColor(ContextCompat.getColor(this, tabIndex == 2 ? R.color.colorAccent : R.color.colorTextSecondary));
        }
        if (_tabSettingsIndicator != null) {
            _tabSettingsIndicator.setBackgroundColor(ContextCompat.getColor(this, tabIndex == 2 ? R.color.colorAccent : android.R.color.transparent));
        }

        if (tabIndex == 2) {
            updateLogStatusUI();
            updateAppIconSelectionUI(AppIconHelper.getSelectedIcon(this));
        }
    }

    private void updateLogStatusUI() {
        runOnUiThread(() -> {
            if (_logStatusText == null) return;
            CanTrafficLogger.LogInfo info = CanTrafficLogger.getInstance().getLogInfo(this);
            if (info.exists) {
                SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                String dateStr = (info.startDate != null) ? df.format(info.startDate) : "---";
                String sizeStr = info.getFormattedSize();

                if (info.isRecording) {
                    _logStatusText.setText(getString(R.string.settings_log_status_recording, dateStr, sizeStr));
                    _logStatusText.setTextColor(ContextCompat.getColor(this, R.color.colorRegen));
                } else {
                    _logStatusText.setText(getString(R.string.settings_log_status_saved, dateStr, sizeStr));
                    _logStatusText.setTextColor(ContextCompat.getColor(this, R.color.colorText));
                }
                if (_shareLogButton != null) {
                    _shareLogButton.setEnabled(true);
                    _shareLogButton.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.colorAccent)));
                    _shareLogButton.setTextColor(ContextCompat.getColor(this, R.color.colorBtnText));
                }
            } else {
                _logStatusText.setText(R.string.settings_log_none);
                _logStatusText.setTextColor(ContextCompat.getColor(this, R.color.colorTextSecondary));
                if (_shareLogButton != null) {
                    _shareLogButton.setEnabled(false);
                    _shareLogButton.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.colorHighlight)));
                    _shareLogButton.setTextColor(ContextCompat.getColor(this, R.color.colorTextSecondary));
                }
            }
        });
    }

    private void handleShareLog() {
        try {
            Uri uri = CanTrafficLogger.getInstance().getShareUri(this);
            updateLogStatusUI();
            if (uri == null) {
                AppToast.show(this, R.string.no_log_to_export);
                return;
            }
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, getString(R.string.share_chooser_title)));
        } catch (Exception e) {
            Log.e(TAG, "Share log error", e);
            AppToast.show(this, getString(R.string.log_export_error, e.getMessage()), AppToast.LENGTH_LONG);
        }
    }

    private void initAppIconSettings() {
        _btnIconWhite = findViewById(R.id.btn_icon_white);
        _btnIconBlack = findViewById(R.id.btn_icon_black);
        _btnIconGrey = findViewById(R.id.btn_icon_grey);
        _btnIconRed = findViewById(R.id.btn_icon_red);
        _btnIconBlue = findViewById(R.id.btn_icon_blue);
        _btnIconYellow = findViewById(R.id.btn_icon_yellow);

        _textIconWhite = findViewById(R.id.text_icon_white);
        _textIconBlack = findViewById(R.id.text_icon_black);
        _textIconGrey = findViewById(R.id.text_icon_grey);
        _textIconRed = findViewById(R.id.text_icon_red);
        _textIconBlue = findViewById(R.id.text_icon_blue);
        _textIconYellow = findViewById(R.id.text_icon_yellow);

        if (_btnIconWhite != null) {
            _btnIconWhite.setOnClickListener(v -> onAppIconTileClicked(AppIconHelper.ICON_WHITE, getString(R.string.color_white)));
        }
        if (_btnIconBlack != null) {
            _btnIconBlack.setOnClickListener(v -> onAppIconTileClicked(AppIconHelper.ICON_BLACK, getString(R.string.color_black)));
        }
        if (_btnIconGrey != null) {
            _btnIconGrey.setOnClickListener(v -> onAppIconTileClicked(AppIconHelper.ICON_GREY, getString(R.string.color_grey)));
        }
        if (_btnIconRed != null) {
            _btnIconRed.setOnClickListener(v -> onAppIconTileClicked(AppIconHelper.ICON_RED, getString(R.string.color_red)));
        }
        if (_btnIconBlue != null) {
            _btnIconBlue.setOnClickListener(v -> onAppIconTileClicked(AppIconHelper.ICON_BLUE, getString(R.string.color_blue)));
        }
        if (_btnIconYellow != null) {
            _btnIconYellow.setOnClickListener(v -> onAppIconTileClicked(AppIconHelper.ICON_YELLOW, getString(R.string.color_yellow)));
        }

        updateAppIconSelectionUI(AppIconHelper.getSelectedIcon(this));
    }

    private void onAppIconTileClicked(String colorKey, String colorName) {
        String current = AppIconHelper.getSelectedIcon(this);
        if (!current.equalsIgnoreCase(colorKey)) {
            AppIconHelper.setAppIcon(this, colorKey);
            AppIconHelper.applyTaskDescription(this);
            updateAppIconSelectionUI(colorKey);
            updateNotification();
            AppToast.show(this, getString(R.string.icon_changed_toast, colorName));
        }
    }

    private void updateAppIconSelectionUI(String activeKey) {
        runOnUiThread(() -> {
            updateSingleIconTile(_btnIconWhite, _textIconWhite, AppIconHelper.ICON_WHITE.equalsIgnoreCase(activeKey));
            updateSingleIconTile(_btnIconBlack, _textIconBlack, AppIconHelper.ICON_BLACK.equalsIgnoreCase(activeKey));
            updateSingleIconTile(_btnIconGrey, _textIconGrey, AppIconHelper.ICON_GREY.equalsIgnoreCase(activeKey));
            updateSingleIconTile(_btnIconRed, _textIconRed, AppIconHelper.ICON_RED.equalsIgnoreCase(activeKey));
            updateSingleIconTile(_btnIconBlue, _textIconBlue, AppIconHelper.ICON_BLUE.equalsIgnoreCase(activeKey));
            updateSingleIconTile(_btnIconYellow, _textIconYellow, AppIconHelper.ICON_YELLOW.equalsIgnoreCase(activeKey));
        });
    }

    private void updateSingleIconTile(View tile, TextView label, boolean isSelected) {
        if (tile == null) return;
        tile.setBackgroundResource(isSelected ? R.drawable.bg_icon_tile_selected : R.drawable.bg_icon_tile_unselected);
        if (label != null) {
            int colorRes = isSelected ? R.color.colorAccent : R.color.colorText;
            label.setTextColor(ContextCompat.getColor(this, colorRes));
        }
    }

    private void setupLocationUpdates() {
        try {
            boolean fineGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
            boolean coarseGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;

            if (fineGranted || coarseGranted) {
                _locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                if (_locationManager != null) {
                    boolean gpsEnabled = false;
                    boolean networkEnabled = false;
                    try {
                        gpsEnabled = _locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
                    } catch (Exception ignored) {}
                    try {
                        networkEnabled = _locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
                    } catch (Exception ignored) {}

                    if (fineGranted && gpsEnabled) {
                        _locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0.5f, this);
                    }
                    if (networkEnabled) {
                        _locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 0.5f, this);
                    }

                    // Pre-seed location from last known location
                    Location bestLoc = null;
                    if (fineGranted && gpsEnabled) {
                        try {
                            bestLoc = _locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                        } catch (SecurityException ignored) {}
                    }
                    if (bestLoc == null && networkEnabled) {
                        try {
                            bestLoc = _locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                        } catch (SecurityException ignored) {}
                    }
                    if (bestLoc != null) {
                        onLocationChanged(bestLoc);
                    }
                }
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                        101);
            }
        } catch (Exception e) {
            Log.e(TAG, "Location setup error", e);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 101 && grantResults.length > 0) {
            boolean granted = false;
            for (int res : grantResults) {
                if (res == PackageManager.PERMISSION_GRANTED) {
                    granted = true;
                    break;
                }
            }
            if (granted) {
                setupLocationUpdates();
            }
        }
    }

    private void createNotificationChannel() {
        CharSequence name = getString(R.string.channel_name);
        String description = getString(R.string.channel_description);
        int importance = NotificationManager.IMPORTANCE_LOW;
        NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance);
        channel.setDescription(description);
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void connectCAN() {
        try {
            CanTrafficLogger.getInstance().startLogging(this, _vin);
            updateLogStatusUI();
            setText(_messageText, getString(R.string.can_connecting));

            for (String command : _connectionCommands) {
                sendCanCommand(command);
                if (command.length() <= 6) {
                    Thread.sleep(WAIT_TIME_BETWEEN_COMMAND_SENDS_MS);
                }
            }

            // If car responds
            if (!_carConnected) {
                _carConnected = true;
                if (_vin == null || _vin.isEmpty() || _vin.equals("HONDA_E")) {
                    _vin = "HONDA_E";
                    setText(_vinText, "Honda e (Advance)");
                } else {
                    setText(_vinText, _vin);
                }
            }

            Thread.sleep(WAIT_FOR_NEW_MESSAGE_TIMEOUT);
            loop();

        } catch (InterruptedException e) {
            Log.d(TAG, "CAN thread interrupted");
        } catch (Exception e) {
            Log.e(TAG, "Exception during CAN connect", e);
            setText(_messageText, String.format(getString(R.string.can_error), e.getMessage() != null ? e.getMessage() : ""));
        }
    }

    private void loop() {
        _loopRunning = true;
        try {
            while (_loopRunning) {
                if (_isSohTestRunning) {
                    loopSohTest();
                } else {
                    _isEcu15Configured = false;
                    _sysTimeMs = System.currentTimeMillis();
                    loopMessagesToVariables();
                    _epoch = _sysTimeMs / 1000;

                    // Calculate secondary metrics
                    if (_soh > 0 && _soc > 0) {
                        _usableEnergyKwh = Math.round((HONDA_E_NET_CAPACITY_KWH * (_soh / 100.0) * (_soc / 100.0)) * 10.0) / 10.0;
                    }

                    // Determine driving gear
                    String gearStr = determineGear();

                    // Update mobile UI elements
                    updateUI(gearStr);

                    // Log high-level summary to CanTrafficLogger
                    CanTrafficLogger.getInstance().logInfo(String.format(Locale.ENGLISH,
                            "ODO=%dkm, SoC=%.1f%%, kW=%.2f, Volt=%.1fV, Amp=%.2fA, Range=%dkm, Cells min/max=%d/%dmV (Δ%dmV), InvTemp=%d°C, RPM=%d, Gear=%s",
                            _odo, _soc, _power, _volt, _amp, _rangeFromDash, _cellMinMv, _cellMaxMv, _cellDeltaMv, _invTemp, _motorRpm, gearStr));

                    if (_newMessagesCount > 2) {
                        setText(_messageText, String.format(getString(R.string.update_time_format),
                                new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date())));

                        // Status notification every 10s
                        if (_lastEpochNotification + 10 < _epoch) {
                            updateNotification();
                        }
                    } else {
                        setText(_messageText, String.format(getString(R.string.can_waiting), CAN_BUS_SCAN_INTERVAL));
                        Thread.sleep(CAN_BUS_SCAN_INTERVAL);
                    }
                    _newMessagesCount = 0;
                }
            }
        } catch (InterruptedException e) {
            Log.d(TAG, "Loop interrupted");
        } catch (Exception e) {
            Log.e(TAG, "Exception in CAN loop", e);
            setText(_messageText, String.format(getString(R.string.error_format), (e.getMessage() != null ? e.getMessage() : getString(R.string.unknown_error))));
            try {
                Thread.sleep(CAN_BUS_SCAN_INTERVAL);
            } catch (InterruptedException ignored) {}
        }
        _carConnected = false;
    }

    private void sendCanCommand(String command) throws InterruptedException {
        synchronized (_viewModel.getNewMessageParsed()) {
            _viewModel.sendMessage(command + "\r\n");
            _viewModel.getNewMessageParsed().wait(WAIT_FOR_NEW_MESSAGE_TIMEOUT);

            if (_viewModel.isNewMessage()) {
                final String message = _viewModel.getMessage();
                final String messageID = _viewModel.getMessageID();

                if (message.contains(VIN_ID) || messageID.contains(VIN_ID) || messageID.equals("1862F190")) {
                    parseVIN(message);
                    setText(_vinText, _vin);
                    _carConnected = true;
                }

                try {
                    parseResponse(message, messageID);
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing CAN message for " + command, e);
                }

                _viewModel.setNewMessageProcessed();
            }
        }
    }

    private String determineGear() {
        if (!_carConnected && !_isDemoMode) {
            return getString(R.string.gear_placeholder);
        }
        if (_motorRpm > 50 || _speed > 2.0) {
            return getString(R.string.gear_drive);
        } else if (_motorRpm < -50) {
            return getString(R.string.gear_reverse);
        } else if (_speed <= 1.0 && Math.abs(_motorRpm) < 20) {
            return getString(R.string.gear_park);
        } else {
            return getString(R.string.gear_neutral);
        }
    }

    private void queryOdoFromCan() throws InterruptedException {
        sendCanCommand("ATSHDA60F1");
        sendCanCommand("ATFCSH18DA60F1");
        sendCanCommand("ATCRA18DAF160");
        sendCanCommand("227022");
        _isEcu15Configured = false;
    }

    private void queryPeriodicDiagnosticsDuringSohTest() {
        try {
            // 1. ECU 60 (Cluster: ODO, Dashboard Range, Ambient Temperature)
            sendCanCommand("ATSHDA60F1");
            sendCanCommand("ATFCSH18DA60F1");
            sendCanCommand("ATCRA18DAF160");
            sendCanCommand("227022"); // ODO
            sendCanCommand("22702A"); // Dashboard Range
            sendCanCommand("227028"); // Ambient Temp

            // 2. ECU 01 (Powertrain: Inverter temp, 6 module temps, 96 cell voltages)
            sendCanCommand("ATSHDA01F1");
            sendCanCommand("ATFCSH18DA01F1");
            sendCanCommand("ATCRA18DAF101");
            sendCanCommand("22202A"); // Inverter / PCU / Traction temps
            sendCanCommand("22202C"); // 6 module temps
            sendCanCommand("222028"); // 96 cell voltages

            // 3. 12V Auxiliary Battery
            sendCanCommand("ATRV");

            setText(_messageText, String.format(getString(R.string.update_time_format),
                    new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date())));
        } catch (Exception e) {
            Log.e(TAG, "Error querying periodic diagnostics in SoH test", e);
        } finally {
            _isEcu15Configured = false;
        }
    }

    private void loopSohTest() throws InterruptedException {
        if (_isSohTestStopping) {
            try {
                queryOdoFromCan();
                // Also query latest SoC
                sendCanCommand("ATSHDA15F1");
                sendCanCommand("ATFCSH18DA15F1");
                sendCanCommand("ATCRA18DAF115");
                sendCanCommand("222029");
            } catch (Exception ignored) {}
            _sohTestStopOdo = _odo;
            _sohTestStopSoc = _soc;
            finishSohTestCalculations();
            return;
        }

        // Ensure ECU 15 header is set
        if (!_isEcu15Configured) {
            sendCanCommand("ATSHDA15F1");
            sendCanCommand("ATFCSH18DA15F1");
            sendCanCommand("ATCRA18DAF115");
            _isEcu15Configured = true;
        }

        // 1. Query voltage & current (DID 0x2021 on ECU 15)
        sendCanCommand("222021");

        long now = SystemClock.elapsedRealtime();
        if (_sohTestLastElapsedRealtimeMs > 0) {
            double dtSeconds = (now - _sohTestLastElapsedRealtimeMs) / 1000.0;
            if (dtSeconds > 0 && dtSeconds < 10.0) {
                // Power in Watts: volt * amp (in Honda e, positive amp is discharge, negative is regen)
                double powerWatts = _volt * _amp;
                // Convert W*s to kWh: (W * s) / 3,600,000
                double deltaKwh = (powerWatts * dtSeconds) / 3600000.0;
                _sohTestTotalNetEnergyKwh += deltaKwh;
                if (deltaKwh > 0) {
                    _sohTestTotalDischargeKwh += deltaKwh;
                } else {
                    _sohTestTotalRegenKwh += Math.abs(deltaKwh);
                }
                _sohTestSampleCount++;

                CanTrafficLogger.getInstance().logInfo(String.format(Locale.ENGLISH,
                        "[SOH_TEST] dt=%.3fs, V=%.1fV, A=%.2fA, kW=%.2f, netKwh=%.4f, samples=%d",
                        dtSeconds, _volt, _amp, _power, _sohTestTotalNetEnergyKwh, _sohTestSampleCount));
            }
        }
        _sohTestLastElapsedRealtimeMs = now;

        _sohTestCycleCounter++;

        // 2. Periodically query other vehicle values every ~40 samples (~15-16s)
        if (_sohTestCycleCounter % 40 == 0) {
            queryPeriodicDiagnosticsDuringSohTest();
        }
        // 3. Query SoC periodically (~every 10 samples, roughly 3.5-4 seconds)
        else if (_sohTestCycleCounter % 10 == 0) {
            sendCanCommand("222029");
        }

        // Update live test UI
        updateSohTestLiveUI();

        // Update overview UI too so dashboard remains responsive
        String gearStr = determineGear();
        updateUI(gearStr);

        // Notification update every 10s
        long epoch = System.currentTimeMillis() / 1000;
        if (_lastEpochNotification + 10 < epoch) {
            updateNotification();
        }

        // Sleep to achieve ~350-400ms sample interval
        Thread.sleep(300);
    }

    private void onToggleSohTestClicked() {
        if (!_isSohTestRunning) {
            if (!_carConnected && !_isDemoMode) {
                AppToast.show(this, R.string.soh_test_not_connected);
                return;
            }
            startSohTest();
        } else {
            stopSohTest();
        }
    }

    private void startSohTest() {
        if (!_carConnected && !_isDemoMode) {
            AppToast.show(this, R.string.soh_test_not_connected);
            return;
        }
        // Acquire wake lock to keep CPU awake even if screen turns off
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null && (_wakeLock == null || !_wakeLock.isHeld())) {
            _wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HondaEScanner:SohTestWakeLock");
            _wakeLock.acquire(4 * 3600 * 1000L); // Max 4 hours safety timeout
        }

        _sohTestStartSysTimeMs = System.currentTimeMillis();
        _sohTestLastElapsedRealtimeMs = 0;
        if (_isDemoMode) {
            if (_soc < 25.0) _soc = 76.50;
            if (_odo <= 0) _odo = 15000;
        }
        _sohTestStartSoc = _soc > 0 ? _soc : 76.5;
        _sohTestStartOdo = _odo > 0 ? _odo : 0;
        _sohTestGpsDistanceKm = 0.0;
        _sohTestLastGpsLocation = _lastLocation;
        _sohTestTotalNetEnergyKwh = 0.0;
        _sohTestTotalDischargeKwh = 0.0;
        _sohTestTotalRegenKwh = 0.0;
        _sohTestSampleCount = 0;
        _sohTestCycleCounter = 0;
        _isEcu15Configured = false;
        _isSohTestStopping = false;
        _isSohTestRunning = true;

        CanTrafficLogger.getInstance().logInfo(String.format(Locale.ENGLISH,
                "[SOH_TEST_STARTED] StartSoC=%.2f%%, StartOdo=%dkm", _sohTestStartSoc, _sohTestStartOdo));

        runOnUiThread(() -> {
            if (_tabOverview != null) {
                _tabOverview.setEnabled(true);
                _tabOverview.setAlpha(1.0f);
            }
            if (_tabSettings != null) {
                _tabSettings.setEnabled(true);
                _tabSettings.setAlpha(1.0f);
            }
            if (_btnToggleSohTest != null) {
                _btnToggleSohTest.setEnabled(true);
                _btnToggleSohTest.setText(R.string.soh_test_btn_stop);
                _btnToggleSohTest.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.colorWarning)));
            }
            if (_sohTestStatusText != null) {
                _sohTestStatusText.setText(R.string.soh_test_status_running);
            }
            if (_cardSohTestResults != null) {
                _cardSohTestResults.setVisibility(View.GONE);
            }
            if (_cardSohTestLive != null) {
                _cardSohTestLive.setVisibility(View.VISIBLE);
            }
            updateSohTestLiveUI();
        });

        if (_isDemoMode) {
            if (_demoLoopHandler != null && _demoLoopRunnable != null) {
                _demoLoopHandler.removeCallbacks(_demoLoopRunnable);
                _demoLoopHandler.post(_demoLoopRunnable);
            }
        }
    }

    private void stopSohTest() {
        if (_wakeLock != null && _wakeLock.isHeld()) {
            _wakeLock.release();
        }

        if (_isDemoMode) {
            _isSohTestRunning = false;

            // If the user stopped the demo test immediately (< 10 samples or < 0.2 km),
            // provide realistic defaults (1.3 km / 1 km ODO: 15000 → 15001 km)
            if (_sohTestSampleCount < 10 || _sohTestGpsDistanceKm < 0.2) {
                _sohTestSampleCount = Math.max(_sohTestSampleCount, 38);
                _sohTestGpsDistanceKm = 1.3;
                _sohTestTotalNetEnergyKwh = 0.22;
                _sohTestTotalRegenKwh = 0.03;
                _sohTestTotalDischargeKwh = 0.25;
            }

            // Odometer advances by the simulated distance: 15000 + distance km
            int demoDeltaOdo = Math.max(1, (int) Math.round(_sohTestGpsDistanceKm));
            _sohTestStopOdo = _sohTestStartOdo + demoDeltaOdo;
            _odo = _sohTestStopOdo;

            // Target net capacity for realistic Honda e battery health: 26.39 kWh (92.60% SoH on 28.5 kWh nominal)
            double targetNetCapacity = 26.39;
            double calculatedDeltaSoc = (_sohTestTotalNetEnergyKwh / targetNetCapacity) * 100.0;
            calculatedDeltaSoc = Math.max(0.60, Math.round(calculatedDeltaSoc * 100.0) / 100.0);
            _sohTestStopSoc = Math.round((_sohTestStartSoc - calculatedDeltaSoc) * 100.0) / 100.0;
            _soc = _sohTestStopSoc;

            finishSohTestCalculations();
        } else if (_loopRunning && _carConnected) {
            // Signal background loop to query fresh CAN ODO & SoC, then finish calculations
            _isSohTestStopping = true;
            runOnUiThread(() -> {
                if (_btnToggleSohTest != null) {
                    _btnToggleSohTest.setEnabled(false);
                }
                if (_sohTestStatusText != null) {
                    _sohTestStatusText.setText(R.string.soh_test_status_finalizing);
                }
            });
        } else {
            // Not connected or loop stopped
            _sohTestStopSoc = _soc;
            _sohTestStopOdo = _odo;
            finishSohTestCalculations();
        }
    }

    private void finishSohTestCalculations() {
        _isSohTestRunning = false;
        _isSohTestStopping = false;

        long rawDurationMs = System.currentTimeMillis() - _sohTestStartSysTimeMs;
        if (_isDemoMode && _sohTestSampleCount >= 40 && rawDurationMs < 15000L) {
            rawDurationMs = _sohTestSampleCount * 400L;
        }
        final long durationMs = rawDurationMs;
        double deltaSocPct = _sohTestStartSoc - _sohTestStopSoc;
        int deltaKmCan = (_sohTestStopOdo > 0 && _sohTestStartOdo > 0 && _sohTestStopOdo >= _sohTestStartOdo)
                ? (_sohTestStopOdo - _sohTestStartOdo) : 0;
        double netEnergyKwh = _sohTestTotalNetEnergyKwh;

        double calculatedDist = deltaKmCan;
        if (_sohTestGpsDistanceKm >= 0.2) {
            calculatedDist = _sohTestGpsDistanceKm;
        } else if (calculatedDist <= 0 && _sohTestGpsDistanceKm > 0.05) {
            calculatedDist = _sohTestGpsDistanceKm;
        }
        final double effectiveDistKm = calculatedDist;

        CanTrafficLogger.getInstance().logInfo(String.format(Locale.ENGLISH,
                "[SOH_TEST_STOPPED] StopSoC=%.2f%%, StopOdo=%dkm, ΔSoC=%.2f%%, ΔkmCan=%dkm, gpsKm=%.2f, netKwh=%.4f, samples=%d",
                _sohTestStopSoc, _sohTestStopOdo, deltaSocPct, deltaKmCan, _sohTestGpsDistanceKm, netEnergyKwh, _sohTestSampleCount));

        runOnUiThread(() -> {
            if (_tabOverview != null) {
                _tabOverview.setEnabled(true);
                _tabOverview.setAlpha(1.0f);
            }
            if (_tabSettings != null) {
                _tabSettings.setEnabled(true);
                _tabSettings.setAlpha(1.0f);
            }
            if (_btnToggleSohTest != null) {
                _btnToggleSohTest.setEnabled(true);
                _btnToggleSohTest.setText(R.string.soh_test_btn_start);
                _btnToggleSohTest.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.colorAccent)));
            }
            if (_sohTestStatusText != null) {
                _sohTestStatusText.setText(R.string.soh_test_status_finished);
            }

            if (deltaSocPct >= 0.5) {
                double deltaSocRatio = deltaSocPct / 100.0;
                double cNettoKwh = netEnergyKwh / deltaSocRatio;
                double calculatedSohPct = (cNettoKwh / HONDA_E_NET_CAPACITY_KWH) * 100.0;

                if (_cardSohTestResults != null) {
                    _cardSohTestResults.setVisibility(View.VISIBLE);
                }

                setText(_sohTestResultSohText, String.format(Locale.ENGLISH, "%.2f %%", calculatedSohPct));
                setText(_sohTestResultCnettoText, String.format(Locale.ENGLISH, getString(R.string.soh_test_cnetto_format), cNettoKwh));
                setText(_sohTestResultDeltaSocText, String.format(Locale.ENGLISH, "%.2f %%\n(%.2f %% → %.2f %%)",
                        deltaSocPct, _sohTestStartSoc, _sohTestStopSoc));
                setText(_sohTestResultEnergyText, String.format(Locale.ENGLISH, "%.2f kWh", netEnergyKwh));
                setText(_sohTestResultDischargeRegenText, String.format(Locale.ENGLISH, "+%.2f kWh / -%.2f kWh",
                        _sohTestTotalDischargeKwh, _sohTestTotalRegenKwh));

                if (_sohTestStartOdo > 0 && _sohTestStopOdo >= _sohTestStartOdo) {
                    setText(_sohTestResultDistanceText, String.format(Locale.ENGLISH, "%.1f km / %d km\n(%d km → %d km)",
                            _sohTestGpsDistanceKm, deltaKmCan, _sohTestStartOdo, _sohTestStopOdo));
                } else if (_sohTestStartOdo > 0) {
                    setText(_sohTestResultDistanceText, String.format(Locale.ENGLISH, "%.1f km / %d km\n(%d km)",
                            _sohTestGpsDistanceKm, deltaKmCan, _sohTestStartOdo));
                } else {
                    setText(_sohTestResultDistanceText, String.format(Locale.ENGLISH, "%.1f km / %d km",
                            _sohTestGpsDistanceKm, deltaKmCan));
                }

                if (effectiveDistKm > 0.05) {
                    double avgConsumptionKwh100km = (netEnergyKwh / effectiveDistKm) * 100.0;
                    setText(_sohTestResultConsumptionText, String.format(Locale.ENGLISH, "%.2f kWh/100 km", avgConsumptionKwh100km));
                } else {
                    setText(_sohTestResultConsumptionText, getString(R.string.soh_test_result_consumption_placeholder));
                }

                long hours = (durationMs / (1000 * 60 * 60));
                long minutes = (durationMs / (1000 * 60)) % 60;
                long seconds = (durationMs / 1000) % 60;
                setText(_sohTestResultDurationText, String.format(Locale.ENGLISH, getString(R.string.soh_test_samples_format), hours, minutes, seconds, _sohTestSampleCount));
            } else {
                AppToast.show(this, R.string.soh_test_not_enough_soc, AppToast.LENGTH_LONG);
            }
        });
    }

    private void startDemoSimulationLoop() {
        stopDemoSimulationLoop();
        _demoTickCounter = 0;
        _demoSimulatedDistanceKm = 0.0;
        _demoLoopHandler = new Handler(Looper.getMainLooper());
        _demoLoopRunnable = new Runnable() {
            @Override
            public void run() {
                if (!_isDemoMode) return;

                _demoTickCounter++;
                if (_isSohTestRunning) {
                    stepDemoSohSimulation();
                } else {
                    stepDemoOverviewSimulation();
                }

                if (_demoTickCounter % 6 == 0) {
                    updateNotification();
                }

                if (_demoLoopHandler != null) {
                    _demoLoopHandler.postDelayed(this, _isSohTestRunning ? 400 : 600);
                }
            }
        };
        _demoLoopHandler.postDelayed(_demoLoopRunnable, 600);
    }

    private void stopDemoSimulationLoop() {
        if (_demoLoopHandler != null && _demoLoopRunnable != null) {
            _demoLoopHandler.removeCallbacks(_demoLoopRunnable);
            _demoLoopHandler = null;
            _demoLoopRunnable = null;
        }
    }

    private void stepDemoSohSimulation() {
        if (!_isSohTestRunning) return;

        double currentAmp;
        if ((_sohTestSampleCount % 20) >= 16) {
            // Periodic regenerative braking phase (green power, -16 to -25 kW)
            currentAmp = -(45.0 + Math.random() * 25.0);
        } else {
            // Acceleration / cruising phase (normal power color, +41 to +55 kW)
            currentAmp = 115.0 + (Math.random() * 35.0);
        }
        _volt = 360.0 + (Math.random() * 6.0);
        _amp = currentAmp;
        _power = Math.round((_volt * _amp / 1000.0) * 100.0) / 100.0;

        double dtSeconds = 0.4;
        double deltaKwh = (_power * 1000.0 * dtSeconds) / 3600000.0;
        _sohTestTotalNetEnergyKwh += deltaKwh;
        if (deltaKwh > 0) {
            _sohTestTotalDischargeKwh += deltaKwh;
        } else {
            _sohTestTotalRegenKwh += Math.abs(deltaKwh);
        }
        _sohTestSampleCount++;

        // Faster, responsive demo distance progression (~0.035 km per 400ms tick)
        _sohTestGpsDistanceKm += 0.035;

        // Sync simulated vehicle telemetry during test
        _odo = _sohTestStartOdo + (int) Math.round(_sohTestGpsDistanceKm);
        _speed = 125.0 + (Math.random() * 12.0);
        _speedFromCan = (int) Math.round(_speed);

        // Smoothly update SoC in lockstep with net energy consumed (~26.39 kWh net capacity = 92.60% SoH)
        double deltaSocPct = (_sohTestTotalNetEnergyKwh / 26.39) * 100.0;
        _soc = Math.max(10.0, Math.round((_sohTestStartSoc - deltaSocPct) * 100.0) / 100.0);

        // Update Overview UI and open dialogs in real time
        String gearStr = determineGear();
        updateUI(gearStr);

        if (_sohTestSampleCount % 38 == 0) {
            setText(_messageText, String.format(getString(R.string.update_time_demo_format),
                    new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date())));
        }

        updateSohTestLiveUI();
    }

    private void stepDemoOverviewSimulation() {
        if (!_isDemoMode || _isSohTestRunning) return;

        int cycleTick = _demoTickCounter % 60; // 60 ticks * 600ms = 36-second driving cycle

        double targetSpeed;
        double targetPower;
        double targetVolt;

        if (cycleTick < 8) {
            // 1. Stopped at red light (~4.8s): HVAC/accessories running
            targetSpeed = 0.0;
            targetPower = 0.32 + (Math.random() * 0.08);
            targetVolt = 368.4 + (Math.random() * 0.3);
            _motorRpm = 0;
        } else if (cycleTick < 22) {
            // 2. Smooth acceleration up to city speed (~8.4s)
            double progress = (cycleTick - 8) / 14.0;
            targetSpeed = Math.round(52.0 * progress * (0.95 + Math.random() * 0.1));
            targetPower = 12.0 + (16.0 * Math.sin(progress * Math.PI)) + (Math.random() * 2.5);
            targetVolt = 365.2 - (progress * 1.5) + (Math.random() * 0.4);
            _motorRpm = (int) (targetSpeed * 50);
        } else if (cycleTick < 40) {
            // 3. Cruising around 48 - 54 km/h (~10.8s)
            targetSpeed = 50.0 + (Math.sin(cycleTick * 0.5) * 3.5) + ((Math.random() - 0.5) * 1.5);
            targetPower = 6.5 + (Math.sin(cycleTick * 0.4) * 2.5) + (Math.random() * 1.2);
            targetVolt = 367.2 + (Math.random() * 0.4);
            _motorRpm = (int) (targetSpeed * 50);
        } else if (cycleTick < 54) {
            // 4. Deceleration with regenerative braking (~8.4s)
            double regenProgress = (cycleTick - 40) / 14.0;
            targetSpeed = Math.max(0.0, Math.round(50.0 * (1.0 - regenProgress)));
            if (targetSpeed > 3.0) {
                targetPower = -(7.5 + (12.0 * Math.sin(regenProgress * Math.PI)) + (Math.random() * 2.0));
                targetVolt = 370.5 + (regenProgress * 1.5) + (Math.random() * 0.4);
            } else {
                targetPower = 0.35;
                targetVolt = 368.4;
            }
            _motorRpm = (int) (targetSpeed * 50);
        } else {
            // 5. Coming to a standstill (~3.6s)
            targetSpeed = 0.0;
            targetPower = 0.35 + (Math.random() * 0.05);
            targetVolt = 368.4 + (Math.random() * 0.2);
            _motorRpm = 0;
        }

        _speed = Math.max(0.0, Math.round(targetSpeed * 10.0) / 10.0);
        _speedFromCan = (int) Math.round(_speed);
        _power = Math.round(targetPower * 100.0) / 100.0;
        _volt = Math.round(targetVolt * 10.0) / 10.0;
        _amp = Math.round((_power * 1000.0 / _volt) * 100.0) / 100.0;

        // Distance accumulation
        if (_speed > 0) {
            _demoSimulatedDistanceKm += (_speed * (0.6 / 3600.0));
            if (_demoSimulatedDistanceKm >= 1.0) {
                _odo += (int) _demoSimulatedDistanceKm;
                _demoSimulatedDistanceKm -= (int) _demoSimulatedDistanceKm;
            }
        }

        // 96 Individual cell voltages: realistic distribution & jitter
        double avgCellV = _volt / 96.0;
        double minCell = avgCellV;
        double maxCell = avgCellV;
        for (int i = 0; i < 96; i++) {
            double offset = (((i * 7) % 11) - 5) * 0.0006;
            double jitter = (Math.random() - 0.5) * 0.002;
            double cellV = Math.round((avgCellV + offset + jitter) * 1000.0) / 1000.0;
            _cellVoltages[i] = cellV;
            if (cellV < minCell) minCell = cellV;
            if (cellV > maxCell) maxCell = cellV;
        }
        _cellMinV = minCell;
        _cellMaxV = maxCell;
        _cellAvgV = avgCellV;
        _cellMinMv = (int) Math.round(_cellMinV * 1000.0);
        _cellMaxMv = (int) Math.round(_cellMaxV * 1000.0);
        _cellDeltaMv = _cellMaxMv - _cellMinMv;

        // 6 battery module temperatures with slow realistic thermal drift
        double tBase = 20.2;
        _moduleTemps[0] = Math.round((tBase + 0.1 + Math.sin(_demoTickCounter * 0.06) * 0.2) * 10.0) / 10.0;
        _moduleTemps[1] = Math.round((tBase - 0.4 + Math.cos(_demoTickCounter * 0.05) * 0.2) * 10.0) / 10.0;
        _moduleTemps[2] = Math.round((tBase + 0.5 + Math.sin(_demoTickCounter * 0.07) * 0.2) * 10.0) / 10.0;
        _moduleTemps[3] = Math.round((tBase - 0.1 + Math.cos(_demoTickCounter * 0.06) * 0.2) * 10.0) / 10.0;
        _moduleTemps[4] = Math.round((tBase + 0.3 + Math.sin(_demoTickCounter * 0.05) * 0.2) * 10.0) / 10.0;
        _moduleTemps[5] = Math.round((tBase - 0.3 + Math.cos(_demoTickCounter * 0.07) * 0.2) * 10.0) / 10.0;

        _batTempMin = _moduleTemps[1];
        _batTempMax = _moduleTemps[2];
        for (int m = 0; m < 6; m++) {
            if (_moduleTemps[m] < _batTempMin) _batTempMin = _moduleTemps[m];
            if (_moduleTemps[m] > _batTempMax) _batTempMax = _moduleTemps[m];
        }
        _batTempSpread = Math.round((_batTempMax - _batTempMin) * 10.0) / 10.0;
        _batTemp = Math.round(((_batTempMin + _batTempMax) / 2.0) * 10.0) / 10.0;

        // 12V Auxiliary battery
        _auxBat = Math.round((14.2 + (Math.sin(_demoTickCounter * 0.1) * 0.15) + (Math.random() * 0.05)) * 10.0) / 10.0;

        // GPS Location & Elevation
        double simLat = 35.6653 + (Math.sin(_demoTickCounter * 0.03) * 0.00035);
        double simLon = 139.7214 + (Math.cos(_demoTickCounter * 0.03) * 0.00035);
        _lat = String.format(Locale.ENGLISH, "%.7f", simLat);
        _lon = String.format(Locale.ENGLISH, "%.7f", simLon);
        _elevation = Math.round(25.0 + Math.sin(_demoTickCounter * 0.08) * 3.0);

        // Update time heartbeat
        setText(_messageText, String.format(getString(R.string.update_time_demo_format),
                new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date())));

        // Update Overview UI and open dialogs in real time
        String gearStr = determineGear();
        updateUI(gearStr);
    }

    private void updateSohTestLiveUI() {
        runOnUiThread(() -> {
            long elapsedMs = _sohTestStartSysTimeMs > 0 ? System.currentTimeMillis() - _sohTestStartSysTimeMs : 0;
            long hours = (elapsedMs / (1000 * 60 * 60));
            long minutes = (elapsedMs / (1000 * 60)) % 60;
            long seconds = (elapsedMs / 1000) % 60;

            setText(_sohTestLiveTimeText, String.format(Locale.ENGLISH, "%02d:%02d:%02d", hours, minutes, seconds));
            setText(_sohTestLiveSamplesText, String.format(Locale.ENGLISH, "%,d", _sohTestSampleCount));
            if (_sohTestLiveEnergyText != null) {
                setText(_sohTestLiveEnergyText, String.format(Locale.ENGLISH, "%.2f kWh (+%.2f / -%.2f)",
                        _sohTestTotalNetEnergyKwh, _sohTestTotalDischargeKwh, _sohTestTotalRegenKwh));
            }

            double liveDeltaSoc = (_sohTestStartSoc > 0 && _soc > 0) ? Math.max(0.0, _sohTestStartSoc - _soc) : 0.0;
            if (_sohTestLiveDeltaSocText != null) {
                setText(_sohTestLiveDeltaSocText, String.format(Locale.ENGLISH, "%.2f %%", liveDeltaSoc));
            }

            int canDeltaKm = (_odo > 0 && _sohTestStartOdo > 0 && _odo >= _sohTestStartOdo) ? (_odo - _sohTestStartOdo) : 0;
            if (_sohTestLiveDistanceText != null) {
                setText(_sohTestLiveDistanceText, String.format(Locale.ENGLISH, "%.1f km / %d km",
                        _sohTestGpsDistanceKm, canDeltaKm));
            }

            if (_volt > 0) {
                setText(_sohTestLivePowerText, String.format(Locale.ENGLISH, "%+.2f kW (%.1f A, %.1f V)", _power, _amp, _volt));
                if (_power < -0.05 || _isCharging) {
                    _sohTestLivePowerText.setTextColor(ContextCompat.getColor(this, R.color.colorRegen));
                } else {
                    _sohTestLivePowerText.setTextColor(ContextCompat.getColor(this, R.color.colorText));
                }
            } else {
                setText(_sohTestLivePowerText, getString(R.string.live_power_placeholder));
                _sohTestLivePowerText.setTextColor(ContextCompat.getColor(this, R.color.colorText));
            }
        });
    }

    private void updateUI(String gearStr) {
        // 1. Battery & Range Card
        if (_soc > 0) {
            setText(_socDashText, String.format(Locale.ENGLISH, "%.2f %%", _soc));
        } else {
            setText(_socDashText, getString(R.string.soc_placeholder));
        }

        if (_socMin > 0 && _socMax > 0) {
            setText(_socCellsText, String.format(Locale.ENGLISH, "%.2f %% / %.2f %%", _socMin, _socMax));
            setText(_socDeltaText, String.format(Locale.ENGLISH, "%.2f %%", _socDelta));
        } else {
            setText(_socCellsText, getString(R.string.soc_range_placeholder));
            setText(_socDeltaText, getString(R.string.soc_delta_placeholder));
        }

        // Cell Voltages: real 96-cell readings from DID 0x2028 (with fallback to pack V / 96 if not yet received)
        if (_cellMinV > 0.0 && _cellMaxV > 0.0) {
            setText(_cellVoltText, String.format(Locale.ENGLISH, "%.3f V / %.3f V", _cellMinV, _cellMaxV));
            setText(_cellDeltaText, String.format(Locale.ENGLISH, "%d mV", _cellDeltaMv));
        } else if (_volt > 0) {
            double avgCellV = _volt / 96.0;
            if (_socDelta > 0) {
                _cellDeltaMv = (int) Math.round(_socDelta * 6.5);
                double halfDeltaV = (_cellDeltaMv / 2000.0);
                double minV = avgCellV - halfDeltaV;
                double maxV = avgCellV + halfDeltaV;
                _cellMinMv = (int) Math.round(minV * 1000.0);
                _cellMaxMv = (int) Math.round(maxV * 1000.0);
                setText(_cellVoltText, String.format(Locale.ENGLISH, "%.3f V / %.3f V", minV, maxV));
                setText(_cellDeltaText, String.format(Locale.ENGLISH, "%d mV", _cellDeltaMv));
            } else {
                setText(_cellVoltText, String.format(Locale.ENGLISH, "%.3f V / %.3f V", avgCellV, avgCellV));
                setText(_cellDeltaText, "0 mV");
            }
        } else {
            setText(_cellVoltText, getString(R.string.cell_volt_placeholder));
            setText(_cellDeltaText, getString(R.string.cell_delta_placeholder));
        }

        if (_soh > 0) {
            _capacityAh = _soh; // in Honda e, CAN DID 0x2021 reports Ah capacity * 0.01 (nominal 100 Ah)
            setText(_sohText, String.format(Locale.ENGLISH, "%.2f %% (%.2f Ah)", _soh, _capacityAh));
        } else {
            setText(_sohText, getString(R.string.soh_placeholder));
        }

        if (_usableEnergyKwh > 0) {
            setText(_energyText, String.format(Locale.ENGLISH, "%.1f kWh", _usableEnergyKwh));
        } else {
            setText(_energyText, getString(R.string.remaining_energy_placeholder));
        }

        // Exact dashboard range from Cluster DID 0x702A
        if (_rangeFromDash > 0) {
            setText(_rangeDashText, _rangeFromDash + " km");
        } else {
            setText(_rangeDashText, getString(R.string.range_placeholder));
        }

        // 2. Power & Charging Card
        if (_isCharging) {
            int chargingRes = _chargingConnection == ChargingConnection.DC ? R.string.charging_dc : R.string.charging_ac;
            setText(_chargingText, getString(chargingRes));
            runOnUiThread(() -> _chargingText.setTextColor(ContextCompat.getColor(this, R.color.colorCharging)));
        } else {
            setText(_chargingText, getString(R.string.not_charging));
            runOnUiThread(() -> _chargingText.setTextColor(ContextCompat.getColor(this, R.color.colorTextSecondary)));
        }

        // Charging input power (kW into battery)
        if (_isCharging || (_power < -0.1 && (_chargingConnection != ChargingConnection.NC || _isDemoMode))) {
            double chargeKw = Math.abs(_power);
            setText(_chargePowerText, String.format(Locale.ENGLISH, "+%.2f kW", chargeKw));
            runOnUiThread(() -> _chargePowerText.setTextColor(ContextCompat.getColor(this, R.color.colorCharging)));
        } else {
            setText(_chargePowerText, getString(R.string.kw_placeholder));
            runOnUiThread(() -> _chargePowerText.setTextColor(ContextCompat.getColor(this, R.color.colorTextSecondary)));
        }

        if (_power != 0.0 || _volt > 0) {
            String pwrSign = _power > 0 ? "+" : "";
            setText(_kwText, String.format(Locale.ENGLISH, "%s%.2f kW", pwrSign, _power));
        } else {
            setText(_kwText, getString(R.string.kw_placeholder));
        }

        // Instantaneous consumption (kWh / 100 km)
        double currentSpeed = _speedFromCan > 0 ? _speedFromCan : _speed;
        if (currentSpeed > 2.0) {
            _instantConsumption = (_power / currentSpeed) * 100.0;
            setText(_consumptionText, String.format(Locale.ENGLISH, "%+.1f kWh/100km", _instantConsumption));
            if (_instantConsumption < -0.1) {
                runOnUiThread(() -> _consumptionText.setTextColor(ContextCompat.getColor(this, R.color.colorRegen)));
            } else {
                runOnUiThread(() -> _consumptionText.setTextColor(ContextCompat.getColor(this, R.color.colorText)));
            }
        } else if (_carConnected || _isDemoMode) {
            _instantConsumption = 0.0;
            setText(_consumptionText, getString(R.string.consumption_stopped));
            runOnUiThread(() -> _consumptionText.setTextColor(ContextCompat.getColor(this, R.color.colorTextSecondary)));
        } else {
            _instantConsumption = 0.0;
            setText(_consumptionText, getString(R.string.consumption_placeholder));
            runOnUiThread(() -> _consumptionText.setTextColor(ContextCompat.getColor(this, R.color.colorTextSecondary)));
        }

        if (_volt > 0) {
            setText(_ampText, String.format(Locale.ENGLISH, "%.2f A", _amp));
            setText(_voltText, String.format(Locale.ENGLISH, getString(R.string.battery_voltage_format), _volt, _volt / 96.0));
        } else {
            setText(_ampText, getString(R.string.amp_placeholder));
            setText(_voltText, getString(R.string.battery_voltage_placeholder));
        }

        // Colorize power: green for regen/charging (negative value), normal text color for discharge (positive value)
        runOnUiThread(() -> {
            if (_power < -0.05 || _isCharging) {
                _kwText.setTextColor(ContextCompat.getColor(this, R.color.colorRegen));
            } else {
                _kwText.setTextColor(ContextCompat.getColor(this, R.color.colorText));
            }
        });

        // 3. Temperature & Diagnostics Card
        if (_batTemp > -50.0 && (_batTempMin > -50.0 || _batTempMax > -50.0)) {
            setText(_batTempText, String.format(Locale.ENGLISH, "%.1f °C", _batTemp));
            if (_batTempMin != _batTempMax) {
                setText(_batTempRangeText, String.format(Locale.ENGLISH, "%.1f °C / %.1f °C", _batTempMin, _batTempMax));
                setText(_batTempDeltaText, String.format(Locale.ENGLISH, "%.1f °C", _batTempSpread));
            } else {
                setText(_batTempRangeText, String.format(Locale.ENGLISH, "%.1f °C / %.1f °C", _batTemp, _batTemp));
                setText(_batTempDeltaText, "0.0 °C");
            }
        } else {
            setText(_batTempText, getString(R.string.temp_placeholder));
            setText(_batTempRangeText, getString(R.string.temp_range_placeholder));
            setText(_batTempDeltaText, getString(R.string.temp_delta_placeholder));
        }

        if (_invTemp > 0) {
            setText(_invTempText, String.format(Locale.ENGLISH, "%d °C", _invTemp));
        } else {
            setText(_invTempText, getString(R.string.temp_placeholder));
        }
        if (_ambientTemp > -50) {
            setText(_ambientTempText, String.format(Locale.ENGLISH, "%d.0 °C", _ambientTemp));
        } else {
            setText(_ambientTempText, getString(R.string.ambient_temp_placeholder));
        }

        if (_auxBat >= 13.8) {
            setText(_auxBatText, String.format(Locale.ENGLISH, getString(R.string.aux_bat_charging), _auxBat));
            runOnUiThread(() -> _auxBatText.setTextColor(ContextCompat.getColor(this, R.color.colorText)));
        } else if (_auxBat >= 12.2) {
            setText(_auxBatText, String.format(Locale.ENGLISH, getString(R.string.aux_bat_ok), _auxBat));
            runOnUiThread(() -> _auxBatText.setTextColor(ContextCompat.getColor(this, R.color.colorText)));
        } else if (_auxBat > 0) {
            setText(_auxBatText, String.format(Locale.ENGLISH, getString(R.string.aux_bat_low), _auxBat));
            runOnUiThread(() -> _auxBatText.setTextColor(ContextCompat.getColor(this, R.color.colorWarning)));
        } else {
            setText(_auxBatText, getString(R.string.aux_bat_placeholder));
            runOnUiThread(() -> _auxBatText.setTextColor(ContextCompat.getColor(this, R.color.colorTextSecondary)));
        }

        // 4. Vehicle Info Card
        if (_odo > 0) {
            setText(_odoText, _odo + " km");
        } else {
            setText(_odoText, getString(R.string.odo_placeholder));
        }
        if (_carConnected || _isDemoMode) {
            double displaySpeed = _speedFromCan > 0 ? _speedFromCan : _speed;
            if (displaySpeed > 1.0) {
                setText(_speedText, String.format(Locale.ENGLISH, "%.0f km/h", displaySpeed));
            } else {
                setText(_speedText, "0 km/h");
            }
        } else {
            setText(_speedText, getString(R.string.speed_unit_placeholder));
        }
        setText(_gearText, gearStr);

        if (!TextUtils.isEmpty(_lat) && !TextUtils.isEmpty(_lon)) {
            String formattedLat = _lat;
            String formattedLon = _lon;
            try {
                formattedLat = String.format(Locale.ENGLISH, "%.7f", Double.parseDouble(_lat));
                formattedLon = String.format(Locale.ENGLISH, "%.7f", Double.parseDouble(_lon));
            } catch (Exception ignored) {}
            setText(_latText, formattedLat);
            setText(_lonText, formattedLon);
            runOnUiThread(() -> {
                if (_lonText != null) {
                    _lonText.setVisibility(View.VISIBLE);
                }
            });
        } else {
            setText(_latText, getString(R.string.dash_placeholder));
            setText(_lonText, getString(R.string.dash_placeholder));
            runOnUiThread(() -> {
                if (_lonText != null) {
                    _lonText.setVisibility(View.VISIBLE);
                }
            });
        }
        if (_elevation != 0) {
            setText(_elevationText, String.format(Locale.ENGLISH, "%.0f m", _elevation));
        } else {
            setText(_elevationText, getString(R.string.elevation_placeholder));
        }

        setText(_vinText, !TextUtils.isEmpty(_vin) && !_vin.equals("HONDA_E") ? _vin : "---");
        updateOpenDialogs();
    }

    private void updateNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }
        try {
            if (_notificationBuilder == null || _notificationManagerCompat == null) {
                return;
            }
            int iconRes = AppIconHelper.getSelectedIconDrawableId(this);
            _notificationBuilder.setSmallIcon(iconRes);

            if (_isSohTestRunning) {
                _notificationBuilder.setContentText(String.format(Locale.ENGLISH, getString(R.string.notification_soh_test_format),
                        _sohTestTotalNetEnergyKwh, Math.max(0, _sohTestStartSoc - _soc), _power));
            } else {
                _notificationBuilder.setContentText(String.format(Locale.ENGLISH, getString(R.string.notification_status_format),
                        _soc, _power, _rangeFromDash));
            }
            _notificationManagerCompat.notify(NOTIFICATION_ID, _notificationBuilder.build());
            _lastEpochNotification = _epoch;
        } catch (Exception e) {
            Log.e(TAG, "Notification post error", e);
        }
    }

    private void loopMessagesToVariables() throws InterruptedException {
        for (String command : _loopCommands) {
            sendCanCommand(command);
            if (command.length() <= 7) {
                Thread.sleep(WAIT_TIME_BETWEEN_COMMAND_SENDS_MS);
            }
        }
    }

    private void parseResponse(String message, String messageID) {
        // 1. Ambient Temperature (DID 0x7028 on ECU 60)
        if (message.contains(AMBIENT_ID) || messageID.contains(AMBIENT_ID)) {
            if (message.length() >= 44) {
                _ambientTemp = Integer.valueOf(message.substring(42, 44), 16).byteValue();
                _newMessagesCount++;
            }
        }
        // 2. Battery SOH, Pack Volts, Pack Amps, kW (DID 0x2021 on ECU 15)
        else if (message.contains(SOH_ID) || messageID.contains(SOH_ID)) {
            if (message.length() >= 284) {
                _soh = Integer.parseInt(message.substring(198, 202), 16) / 100.0;
                _amp = Math.round((Integer.valueOf(message.substring(280, 284), 16).shortValue() / 34.0) * 100.0) / 100.0;
                _volt = Integer.parseInt(message.substring(76, 80), 16) / 10.0;
                _power = Math.round(_amp * _volt / 1000.0 * 100.0) / 100.0;
                _newMessagesCount++;
            }
        }
        // 3. Battery SOC, Min/Max cell SOC, Charging Status (DID 0x2029 on ECU 15)
        else if (message.contains(SOC_ID) || messageID.contains(SOC_ID)) {
            if (message.length() >= 278) {
                _socMin = Integer.parseInt(message.substring(142, 146), 16) / 100.0;
                _socMax = Integer.parseInt(message.substring(138, 142), 16) / 100.0;
                _socDelta = Math.round((_socMax - _socMin) * 100.0) / 100.0;
                _soc = Integer.parseInt(message.substring(156, 160), 16) / 100.0;
                _isCharging = message.charAt(161) == '1';

                switch (message.substring(277, 278)) {
                    case "2":
                        _chargingConnection = ChargingConnection.AC;
                        break;
                    case "3":
                        _chargingConnection = ChargingConnection.DC;
                        break;
                    default:
                        _chargingConnection = ChargingConnection.NC;
                        break;
                }
                _newMessagesCount++;
            }
        }
        // 4. Battery Individual Cell Voltages (DID 0x2028 on ECU 01) - 96 cells
        else if (message.contains(CELLS_V_ID) || messageID.contains(CELLS_V_ID)) {
            int idx = message.indexOf(CELLS_V_ID);
            int start = idx + 60; // 30 bytes (60 hex chars) header offset before Cell 1
            if (idx >= 0 && message.length() >= start + (96 * 4)) {
                double minV = 999.0;
                double maxV = 0.0;
                double sumV = 0.0;
                for (int i = 0; i < 96; i++) {
                    int offset = start + (i * 4);
                    try {
                        int rawWord = Integer.parseInt(message.substring(offset, offset + 4), 16);
                        double cellV = rawWord * 0.0002;
                        _cellVoltages[i] = cellV;
                        if (cellV > 0.5 && cellV < 5.0) {
                            if (cellV < minV) minV = cellV;
                            if (cellV > maxV) maxV = cellV;
                            sumV += cellV;
                        }
                    } catch (Exception ignored) {
                    }
                }
                if (minV < 100.0 && maxV > 0.0) {
                    _cellMinV = minV;
                    _cellMaxV = maxV;
                    _cellAvgV = sumV / 96.0;
                    _cellMinMv = (int) Math.round(_cellMinV * 1000.0);
                    _cellMaxMv = (int) Math.round(_cellMaxV * 1000.0);
                    _cellDeltaMv = (int) Math.round((_cellMaxV - _cellMinV) * 1000.0);
                    _newMessagesCount++;
                    updateOpenDialogs();
                }
            }
        }
        // 5. Battery Module Temperatures (DID 0x202C on ECU 01) - 6 module sensors
        else if (message.contains(BAT_MODULE_TEMPS_ID) || messageID.contains(BAT_MODULE_TEMPS_ID)) {
            int idx = message.indexOf(BAT_MODULE_TEMPS_ID);
            int start = idx + 124; // 62 bytes (124 hex chars) header offset
            if (idx >= 0 && message.length() >= start + (6 * 4)) {
                double minT = 999.0;
                double maxT = -999.0;
                double sumT = 0.0;
                int validCount = 0;
                for (int i = 0; i < 6; i++) {
                    int offset = start + (i * 4);
                    try {
                        short rawVal = (short) Integer.parseInt(message.substring(offset, offset + 4), 16);
                        double t = rawVal / 10.0;
                        _moduleTemps[i] = t;
                        if (t > -40.0 && t < 100.0) {
                            if (t < minT) minT = t;
                            if (t > maxT) maxT = t;
                            sumT += t;
                            validCount++;
                        }
                    } catch (Exception ignored) {
                    }
                }
                if (validCount > 0) {
                    _batTempMin = minT;
                    _batTempMax = maxT;
                    _batTemp = Math.round((sumT / validCount) * 10.0) / 10.0;
                    _batTempSpread = Math.round((_batTempMax - _batTempMin) * 10.0) / 10.0;
                    _newMessagesCount++;
                    updateOpenDialogs();
                }
            }
        }
        // 6. Battery Pack Telemetry / Temperatures fallback (DID 0x202A on ECU 01)
        else if (message.contains(BATTEMP_ID) || messageID.contains(BATTEMP_ID)) {
            if (_batTempMin <= -50.0) {
                if (message.length() >= 418) {
                    double t1 = Integer.valueOf(message.substring(410, 414), 16).shortValue() / 10.0;
                    double t2 = Integer.valueOf(message.substring(414, 418), 16).shortValue() / 10.0;
                    _batTempMax = Math.max(t1, t2);
                    _batTempMin = Math.min(t1, t2);
                    _batTemp = Math.round(((_batTempMin + _batTempMax) / 2.0) * 10.0) / 10.0;
                    _batTempSpread = Math.round((_batTempMax - _batTempMin) * 10.0) / 10.0;
                    _newMessagesCount++;
                } else if (message.length() >= 414) {
                    _batTemp = Integer.valueOf(message.substring(410, 414), 16).shortValue() / 10.0;
                    _batTempMax = _batTemp;
                    _batTempMin = _batTemp;
                    _batTempSpread = 0.0;
                    _newMessagesCount++;
                }
            }
        }
        // 5. Electric Motor RPM (DID 0x2001 on ECU 01)
        else if (message.contains(MOTOR_RPM_ID) || messageID.contains(MOTOR_RPM_ID)) {
            int idx = message.indexOf(MOTOR_RPM_ID);
            if (idx >= 0 && message.length() >= idx + 10) {
                _motorRpm = (short) Integer.parseInt(message.substring(idx + 6, idx + 10), 16);
                _newMessagesCount++;
            }
        }
        // 6. ODO / Tachometer (DID 0x7022 on ECU 60)
        else if (message.contains(ODO_ID) || messageID.contains(ODO_ID)) {
            if (message.length() >= 26) {
                _odo = Integer.parseInt(message.substring(18, 26), 16);
                _newMessagesCount++;
                if (_isSohTestRunning && _sohTestStartOdo <= 0 && _odo > 0) {
                    _sohTestStartOdo = _odo;
                }
            }
        }
        // 7. Dashboard Estimated Range (DID 0x702A on ECU 60)
        else if (message.contains(RANGE_DASH_ID) || messageID.contains(RANGE_DASH_ID)) {
            int idx = message.indexOf(RANGE_DASH_ID);
            if (idx >= 0 && message.length() >= idx + 38) {
                try {
                    _rangeFromDash = Integer.parseInt(message.substring(idx + 34, idx + 38), 16);
                    _newMessagesCount++;
                } catch (NumberFormatException ignored) {}
            }
        }
        // 8. 12V Auxiliary Battery Voltage
        else if (message.matches(".*\\d+\\.\\dV.*")) {
            parseAuxBat(message);
            _newMessagesCount++;
        }
    }

    private void parseAuxBat(String message) {
        Matcher matcher = Pattern.compile("(\\d+\\.\\d)V").matcher(message);
        if (matcher.find()) {
            try {
                _auxBat = Double.parseDouble(matcher.group(1));
            } catch (NumberFormatException ignored) {}
        }
    }

    private String buildAllCellsContent() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ENGLISH, "%-14s %.3f V\n", getString(R.string.dialog_label_min), _cellMinV));
        sb.append(String.format(Locale.ENGLISH, "%-14s %.3f V\n", getString(R.string.dialog_label_avg), _cellAvgV));
        sb.append(String.format(Locale.ENGLISH, "%-14s %.3f V\n", getString(R.string.dialog_label_max), _cellMaxV));
        sb.append(String.format(Locale.ENGLISH, "%-14s %d mV\n", getString(R.string.dialog_label_delta), _cellDeltaMv));
        sb.append("────────────────────────────\n\n");

        for (int i = 0; i < 96; i += 2) {
            String cell1 = String.format(Locale.ENGLISH, "#%02d: %.3f V", i + 1, _cellVoltages[i]);
            String cell2 = (i + 1 < 96) ? String.format(Locale.ENGLISH, "#%02d: %.3f V", i + 2, _cellVoltages[i + 1]) : "";
            sb.append(String.format(Locale.ENGLISH, "%-15s   %-15s\n", cell1, cell2));
        }
        return sb.toString();
    }

    private void showAllCellsDialog() {
        if (_cellVoltages[0] <= 0.0 && _cellMinV <= 0.0) {
            AppToast.show(this, R.string.cells_no_data);
            return;
        }

        if (_cellsDialog != null && _cellsDialog.isShowing()) {
            _cellsDialog.dismiss();
        }

        _cellsDialogTextView = new TextView(this);
        _cellsDialogTextView.setText(buildAllCellsContent());
        _cellsDialogTextView.setTypeface(Typeface.MONOSPACE);
        _cellsDialogTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        int pad = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics());
        _cellsDialogTextView.setPadding(pad, pad, pad, pad);
        _cellsDialogTextView.setTextColor(ContextCompat.getColor(this, R.color.colorText));

        ScrollView sv = new ScrollView(this);
        sv.addView(_cellsDialogTextView);

        _cellsDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.cell_voltages_dialog_title)
                .setView(sv)
                .setPositiveButton(android.R.string.ok, null)
                .setOnDismissListener(dialog -> {
                    _cellsDialog = null;
                    _cellsDialogTextView = null;
                })
                .create();
        _cellsDialog.show();
    }

    private String buildModuleTempsContent() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ENGLISH, "%-14s %.1f °C\n", getString(R.string.dialog_label_min), _batTempMin));
        sb.append(String.format(Locale.ENGLISH, "%-14s %.1f °C\n", getString(R.string.dialog_label_avg), _batTemp));
        sb.append(String.format(Locale.ENGLISH, "%-14s %.1f °C\n", getString(R.string.dialog_label_max), _batTempMax));
        sb.append(String.format(Locale.ENGLISH, "%-14s %.1f °C\n", getString(R.string.dialog_label_delta), _batTempSpread));
        sb.append("──────────────────────────\n\n");

        for (int i = 0; i < 6; i++) {
            double temp = _moduleTemps[i];
            String marker = "";
            if (Math.abs(temp - _batTempMin) < 0.05 && _batTempSpread > 0.1) {
                marker = "  (Min)";
            } else if (Math.abs(temp - _batTempMax) < 0.05 && _batTempSpread > 0.1) {
                marker = "  (Max)";
            }
            sb.append(String.format(Locale.ENGLISH, getString(R.string.dialog_module_format), i + 1, temp, marker));
        }
        return sb.toString();
    }

    private void showModuleTempsDialog() {
        boolean hasData = false;
        for (double t : _moduleTemps) {
            if (t > -40.0 && t < 100.0 && t != 0.0) {
                hasData = true;
                break;
            }
        }
        if (!hasData && _batTempMin <= -50.0) {
            AppToast.show(this, R.string.module_temps_no_data);
            return;
        }

        if (_moduleTempsDialog != null && _moduleTempsDialog.isShowing()) {
            _moduleTempsDialog.dismiss();
        }

        _moduleTempsDialogTextView = new TextView(this);
        _moduleTempsDialogTextView.setText(buildModuleTempsContent());
        _moduleTempsDialogTextView.setTypeface(Typeface.MONOSPACE);
        _moduleTempsDialogTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        int pad = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics());
        _moduleTempsDialogTextView.setPadding(pad, pad, pad, pad);
        _moduleTempsDialogTextView.setTextColor(ContextCompat.getColor(this, R.color.colorText));

        ScrollView sv = new ScrollView(this);
        sv.addView(_moduleTempsDialogTextView);

        _moduleTempsDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.module_temps_dialog_title)
                .setView(sv)
                .setPositiveButton(android.R.string.ok, null)
                .setOnDismissListener(dialog -> {
                    _moduleTempsDialog = null;
                    _moduleTempsDialogTextView = null;
                })
                .create();
        _moduleTempsDialog.show();
    }

    private void updateOpenDialogs() {
        runOnUiThread(() -> {
            if (_cellsDialog != null && _cellsDialog.isShowing() && _cellsDialogTextView != null) {
                _cellsDialogTextView.setText(buildAllCellsContent());
            }
            if (_moduleTempsDialog != null && _moduleTempsDialog.isShowing() && _moduleTempsDialogTextView != null) {
                _moduleTempsDialogTextView.setText(buildModuleTempsContent());
            }
        });
    }

    private void setText(final TextView text, final String value) {
        if (text != null) {
            runOnUiThread(() -> text.setText(value));
        }
    }

    private void parseVIN(String message) {
        int idx = message.indexOf(VIN_ID);
        if (idx >= 0 && message.length() >= idx + 42) {
            _vin = hexToASCII(message.substring(idx + 8, idx + 42));
        } else if (message.length() >= 44) {
            _vin = hexToASCII(message.substring(10, 44));
        }
        if (_vin != null && !_vin.isEmpty() && !_vin.equals("HONDA_E")) {
            getSharedPreferences(ThemeHelper.PREF_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString("vehicle_vin", _vin)
                    .apply();
            CanTrafficLogger.getInstance().updateVin(_vin);
        }
    }

    private static String hexToASCII(String hexStr) {
        StringBuilder output = new StringBuilder();
        for (int i = 0; i < hexStr.length(); i += 2) {
            if (i + 2 <= hexStr.length()) {
                String str = hexStr.substring(i, i + 2);
                try {
                    char c = (char) Integer.parseInt(str, 16);
                    if (c >= 32 && c <= 126) {
                        output.append(c);
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
        return output.toString();
    }

    private void onConnectionStatus(CommunicateViewModel.ConnectionStatus connectionStatus) {
        if (_isDemoMode) {
            return;
        }
        switch (connectionStatus) {
            case CONNECTED:
                _connectionText.setText(R.string.status_connected);
                _connectButton.setEnabled(true);
                _connectButton.setText(R.string.disconnect);
                _connectButton.setOnClickListener(v -> _viewModel.disconnect());
                new Thread(this::connectCAN, "CAN-Worker").start();
                break;

            case CONNECTING:
                _connectionText.setText(R.string.status_connecting);
                _connectButton.setEnabled(true);
                _connectButton.setText(R.string.stop);
                _viewModel.setRetry(true);
                _connectButton.setOnClickListener(v -> _viewModel.setRetry(false));
                break;

            case DISCONNECTED:
                _loopRunning = false;
                _connectionText.setText(R.string.status_disconnected);
                _connectButton.setEnabled(true);
                _connectButton.setText(R.string.connect);
                _connectButton.setOnClickListener(v -> {
                    if (_isDemoMode) {
                        disableDemoMode();
                    }
                    _viewModel.connect();
                });
                CanTrafficLogger.getInstance().stopLogging();
                updateLogStatusUI();
                _retries = 0;
                break;

            case RETRY:
                _retries = _retries + 1;
                if (_viewModel.isRetry() && _retries < MAX_RETRY) {
                    _viewModel.connect();
                } else {
                    _viewModel.disconnect();
                }
                break;
        }
    }

    private void toggleDemoMode() {
        if (!_isDemoMode) {
            enableDemoMode();
        } else {
            disableDemoMode();
        }
    }

    private void enableDemoMode() {
        _isDemoMode = true;
        _loopRunning = false;
        _carConnected = false;

        // Realistic, anonymous Honda e simulation values for public demo
        _vin = "JHMZC7DEMO000001";
        _odo = 15000;
        _rangeFromDash = 142;
        _soc = 76.50;
        _socMin = 78.20;
        _socMax = 78.70;
        _socDelta = 0.50;
        _soh = 93.40;
        _capacityAh = 93.4;
        _usableEnergyKwh = 20.4;

        _power = 0.35;
        _amp = 0.95;
        _volt = 368.4;
        _instantConsumption = 0.0;

        _cellMinMv = 3834;
        _cellMaxMv = 3840;
        _cellDeltaMv = 6;
        _cellMinV = 3.834;
        _cellMaxV = 3.840;
        _cellAvgV = 3.837;
        for (int i = 0; i < 96; i++) {
            _cellVoltages[i] = 3.834 + (Math.random() * 0.006);
        }
        _cellVoltages[0] = 3.834;
        _cellVoltages[95] = 3.840;

        _batTempMin = 19.8;
        _batTempMax = 20.6;
        _batTemp = 20.2;
        _batTempSpread = 0.8;
        _moduleTemps[0] = 20.2;
        _moduleTemps[1] = 19.8;
        _moduleTemps[2] = 20.6;
        _moduleTemps[3] = 20.1;
        _moduleTemps[4] = 20.4;
        _moduleTemps[5] = 19.9;
        _ambientTemp = 21;
        _auxBat = 14.3;

        _speed = 0.0;
        _motorRpm = 0;
        _chargingConnection = ChargingConnection.NC;
        _isCharging = false;

        // Fictional neutral location (Honda Global HQ, Aoyama, Tokyo)
        _lat = String.format(Locale.ENGLISH, "%.7f", 35.6653);
        _lon = String.format(Locale.ENGLISH, "%.7f", 139.7214);
        _elevation = 25.0;

        updateUI(getString(R.string.gear_park));

        _connectionText.setText(R.string.status_demo);
        _demoButton.setText(R.string.demo_button_stop);
        setText(_messageText, String.format(getString(R.string.update_time_demo_format), new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date())));
        AppToast.show(this, R.string.demo_active_toast);

        startDemoSimulationLoop();
    }

    private void disableDemoMode() {
        stopDemoSimulationLoop();
        if (_isSohTestRunning) {
            stopSohTest();
        }

        _isDemoMode = false;
        _demoButton.setText(R.string.demo_button);
        _connectionText.setText(R.string.status_disconnected);
        _soc = 0;
        _socMin = 0;
        _socMax = 0;
        _socDelta = 0;
        _soh = 0;
        _capacityAh = 0;
        _usableEnergyKwh = 0;
        _power = 0;
        _amp = 0;
        _volt = 0;
        _instantConsumption = 0;
        _cellMinMv = 0;
        _cellMaxMv = 0;
        _cellDeltaMv = 0;
        _cellMinV = 0.0;
        _cellMaxV = 0.0;
        _cellAvgV = 0.0;
        Arrays.fill(_cellVoltages, 0.0);
        Arrays.fill(_moduleTemps, 0.0);
        _batTemp = -100.0;
        _batTempMin = -100.0;
        _batTempMax = -100.0;
        _batTempSpread = 0;
        _ambientTemp = -100;
        _auxBat = 0;
        _odo = 0;
        _rangeFromDash = 0;
        _speed = 0;
        _speedFromCan = 0;
        _motorRpm = 0;
        _invTemp = 0;
        _chargingConnection = ChargingConnection.NC;
        _isCharging = false;
        _vin = "";
        _lat = "";
        _lon = "";
        _elevation = 0;

        updateUI(getString(R.string.gear_placeholder));
        setText(_socDeltaText, getString(R.string.soc_delta_placeholder));
        setText(_cellDeltaText, getString(R.string.cell_delta_placeholder));
        setText(_batTempRangeText, getString(R.string.temp_range_placeholder));
        setText(_batTempDeltaText, getString(R.string.temp_delta_placeholder));
        setText(_elevationText, getString(R.string.elevation_placeholder));
        setText(_sohTestLiveTimeText, getString(R.string.soh_test_live_time_placeholder));
        setText(_sohTestLiveSamplesText, getString(R.string.soh_test_live_samples_placeholder));
        setText(_sohTestLiveEnergyText, getString(R.string.soh_test_live_energy_placeholder));
        setText(_sohTestLiveDeltaSocText, getString(R.string.soh_test_live_deltasoc_placeholder));
        setText(_sohTestLiveDistanceText, getString(R.string.soh_test_live_distance_placeholder));
        setText(_sohTestLivePowerText, getString(R.string.live_power_placeholder));
        setText(_sohTestResultDistanceText, getString(R.string.soh_test_result_distance_placeholder));
        setText(_sohTestResultDeltaSocText, getString(R.string.soh_test_result_deltasoc_placeholder));
        setText(_messageText, getString(R.string.messages));
        AppToast.show(this, R.string.demo_stopped_toast);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            getOnBackPressedDispatcher().onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        _loopRunning = false;
        if (_isSohTestRunning) {
            _isSohTestRunning = false;
        }
        stopDemoSimulationLoop();
        if (_wakeLock != null && _wakeLock.isHeld()) {
            _wakeLock.release();
            _wakeLock = null;
        }
        CanTrafficLogger.getInstance().stopLogging();
        if (_locationManager != null) {
            _locationManager.removeUpdates(this);
        }
        if (_cellsDialog != null) {
            try { _cellsDialog.dismiss(); } catch (Exception ignored) {}
            _cellsDialog = null;
            _cellsDialogTextView = null;
        }
        if (_moduleTempsDialog != null) {
            try { _moduleTempsDialog.dismiss(); } catch (Exception ignored) {}
            _moduleTempsDialog = null;
            _moduleTempsDialogTextView = null;
        }
        if (_notificationManagerCompat != null) {
            try {
                _notificationManagerCompat.cancel(NOTIFICATION_ID);
            } catch (Exception ignored) {}
        }
        super.onDestroy();
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        _lat = String.format(Locale.ENGLISH, "%.7f", location.getLatitude());
        _lon = String.format(Locale.ENGLISH, "%.7f", location.getLongitude());
        if (location.hasAltitude()) {
            _elevation = Math.round(location.getAltitude() * 10.0) / 10.0;
        }

        double speedKmh = 0.0;
        if (location.hasSpeed()) {
            speedKmh = location.getSpeed() * 3.6;
        } else if (_lastLocation != null) {
            long dtMs = location.getTime() - _lastLocation.getTime();
            if (dtMs > 400 && dtMs < 10000) {
                float distMeters = _lastLocation.distanceTo(location);
                speedKmh = (distMeters / (dtMs / 1000.0)) * 3.6;
            }
        }
        _lastLocation = location;
        if (speedKmh >= 0.0 && speedKmh < 220.0) {
            _speed = Math.round(speedKmh * 10.0) / 10.0;
        }

        // Live distance accumulation during SoH test
        if (_isSohTestRunning) {
            if (_sohTestLastGpsLocation != null) {
                float d = _sohTestLastGpsLocation.distanceTo(location);
                if (d > 1.0f && d < 600.0f && location.getAccuracy() < 60.0f) {
                    _sohTestGpsDistanceKm += (d / 1000.0);
                }
            }
            _sohTestLastGpsLocation = location;
        }

        // Fast live UI update for speed and instant consumption when driving
        if (_speed > 1.0) {
            runOnUiThread(() -> {
                if (_speedText != null) {
                    _speedText.setText(String.format(Locale.ENGLISH, "%.0f km/h", _speed));
                }
                if (_power != 0.0 && _consumptionText != null) {
                    _instantConsumption = (_power / _speed) * 100.0;
                    _consumptionText.setText(String.format(Locale.ENGLISH, "%+.1f kWh/100km", _instantConsumption));
                    if (_instantConsumption < -0.1) {
                        _consumptionText.setTextColor(ContextCompat.getColor(this, R.color.colorRegen));
                    } else {
                        _consumptionText.setTextColor(ContextCompat.getColor(this, R.color.colorText));
                    }
                }
            });
        }
    }

    enum ChargingConnection {
        NC("None"),
        AC("AC"),
        DC("DC (CCS)");

        private final String _name;

        ChargingConnection(String name) {
            _name = name;
        }

        public String getName() {
            return _name;
        }
    }
}
