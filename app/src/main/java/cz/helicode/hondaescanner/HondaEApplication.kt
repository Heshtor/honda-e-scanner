package cz.helicode.hondaescanner

import android.app.Application
import cz.helicode.hondaescanner.data.bluetooth.BluetoothSerialClient
import cz.helicode.hondaescanner.data.location.LocationTracker
import cz.helicode.hondaescanner.data.prefs.SettingsRepository

class HondaEApplication : Application() {

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var bluetoothClient: BluetoothSerialClient
        private set

    lateinit var locationTracker: LocationTracker
        private set

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(this)
        bluetoothClient = BluetoothSerialClient(this)
        locationTracker = LocationTracker(this)
    }
}
