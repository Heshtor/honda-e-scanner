package cz.helicode.hondaescanner.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class LocationTracker(private val context: Context) {

    companion object {
        private const val TAG = "LocationTracker"
    }

    @SuppressLint("MissingPermission")
    fun locationUpdates(): Flow<Location> = callbackFlow {
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineGranted && !coarseGranted) {
            close()
            return@callbackFlow
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (locationManager == null) {
            close()
            return@callbackFlow
        }

        val listener = LocationListener { location ->
            trySend(location)
        }

        try {
            val gpsEnabled = runCatching {
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            }.getOrDefault(false)
            val networkEnabled = runCatching {
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            }.getOrDefault(false)

            if (fineGranted && gpsEnabled) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L,
                    0.5f,
                    listener,
                    Looper.getMainLooper()
                )
            }
            if (networkEnabled) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    1000L,
                    0.5f,
                    listener,
                    Looper.getMainLooper()
                )
            }

            // Emit last known location immediately if available
            val lastKnown = (if (fineGranted && gpsEnabled) {
                runCatching { locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull()
            } else null) ?: (if (networkEnabled) {
                runCatching { locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }.getOrNull()
            } else null)

            if (lastKnown != null) {
                trySend(lastKnown)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up location updates", e)
        }

        awaitClose {
            runCatching { locationManager.removeUpdates(listener) }
        }
    }
}
