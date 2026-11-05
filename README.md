# 🚗 Honda e Scanner

A lightweight Android OBD-II diagnostic and battery health monitoring tool tailored specifically for the **Honda e** electric vehicle.

Connects via Bluetooth to read proprietary UDS parameters and real-time telemetry directly from the vehicle's onboard CAN bus.

---

## ✨ Features

* **Battery & Range:** Real-time SoC (cluster display vs raw BMS cells), cell voltages (min/max and balance delta across 96 cells), battery State of Health (SoH % and Ah), usable net energy (kWh), and cluster range.
* **Power & Charging:** Live battery power (kW), pack voltage (V), current (A), instant consumption (kWh/100km), and automatic AC (Type 2) vs DC (CCS fast charging) detection with charging rate.
* **Temperatures & Diagnostics:** Traction battery temperature (min/max/spread), ambient temperature, and 12V auxiliary battery charging status.
* **Vehicle Telemetry:** Odometer (ODO), speed, electric motor RPM, drive gear (P/D/R/N), and VIN readout.
* **Battery SoH Test Mode:** Real-world capacity measurement during driving (integration of discharged/regenerated energy vs $\Delta\text{SoC}$).
* **CAN Logger & Export:** Records raw CAN traffic with one-tap export and share via Android FileProvider.
* **Offline Demo Mode:** Built-in interactive simulation with synthetic Honda e telemetry (no car needed).
* **Dual Theme & Languages:** Modern Dark & Light modes styled after the Honda e cockpit, with full English and Czech support.

---

## 🔌 Requirements

* **Vehicle:** Honda e (2020+)
* **OBD-II Adapter:** Bluetooth OBD-II adapter supporting **ISO 15765-4 (CAN 29-bit, 500 kbaud)**.
  * *Tested and verified on:* **vLinker MC+ (Bluetooth)**.
* **Android Device:** Android 8.0 (API level 26) or higher with Bluetooth.

---

## 🛠️ Libraries & Credits

* **[Android Bluetooth Serial](https://github.com/harryjph/android-bluetooth-serial)** by HarrySoft (Apache License 2.0) – Bluetooth Classic (RFCOMM/SPP) connection handling.
* **[RxJava 2 & RxAndroid](https://github.com/ReactiveX/RxJava)** (Apache License 2.0) – Reactive stream processing.
* **[AndroidX](https://developer.android.com/jetpack/androidx) & [Material Components](https://github.com/material-components/material-components-android)** by Google (Apache License 2.0) – Architecture components, ViewModel/LiveData, and Material UI.

---

## 📄 License

This project is licensed under the **European Union Public Licence (EUPL v1.2)**.  
See the `LICENSE` file for details.
