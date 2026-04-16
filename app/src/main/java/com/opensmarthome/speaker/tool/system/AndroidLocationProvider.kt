package com.opensmarthome.speaker.tool.system

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import kotlin.coroutines.resume

/**
 * Android implementation of LocationProvider using LocationManager.
 * No Google Play Services dependency.
 */
class AndroidLocationProvider(
    private val context: Context
) : LocationProvider {

    private val locationManager: LocationManager
        get() = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    @SuppressLint("MissingPermission") // We check hasPermission() above
    override suspend fun getCurrent(
        desiredAccuracy: LocationProvider.Accuracy
    ): LocationResult? {
        if (!hasPermission()) return null

        val providerName = selectProvider(desiredAccuracy) ?: return null

        // First try the last-known location (instant)
        val lastKnown = try {
            locationManager.getLastKnownLocation(providerName)
        } catch (e: Exception) {
            Timber.w(e, "getLastKnownLocation failed")
            null
        }
        if (lastKnown != null && isFresh(lastKnown)) {
            return lastKnown.toResult()
        }

        // Otherwise request a single update with a timeout
        return withTimeoutOrNull(TIMEOUT_MS) {
            suspendCancellableCoroutine<LocationResult?> { cont ->
                val listener = android.location.LocationListener { loc ->
                    if (cont.isActive) cont.resume(loc.toResult())
                }

                try {
                    @SuppressLint("MissingPermission")
                    locationManager.requestSingleUpdate(providerName, listener, context.mainLooper)
                } catch (e: Exception) {
                    Timber.w(e, "requestSingleUpdate failed")
                    if (cont.isActive) cont.resume(lastKnown?.toResult())
                }

                cont.invokeOnCancellation {
                    try {
                        locationManager.removeUpdates(listener)
                    } catch (_: Exception) { }
                }
            }
        } ?: lastKnown?.toResult()
    }

    override fun hasPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun selectProvider(accuracy: LocationProvider.Accuracy): String? {
        val lm = locationManager
        return when (accuracy) {
            LocationProvider.Accuracy.PRECISE -> when {
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> null
            }
            LocationProvider.Accuracy.BALANCED -> when {
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                else -> null
            }
            LocationProvider.Accuracy.COARSE -> when {
                lm.isProviderEnabled(LocationManager.PASSIVE_PROVIDER) -> LocationManager.PASSIVE_PROVIDER
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> null
            }
        }
    }

    private fun isFresh(loc: Location): Boolean {
        val ageMs = System.currentTimeMillis() - loc.time
        return ageMs < MAX_AGE_MS
    }

    private fun Location.toResult(): LocationResult = LocationResult(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracy,
        timestampMs = time,
        provider = provider ?: "unknown"
    )

    companion object {
        private const val TIMEOUT_MS = 10_000L
        private const val MAX_AGE_MS = 60_000L // 1 minute
    }
}
