package br.unasp.boacao.util

import android.content.Context
import android.location.Geocoder
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

object GeocodeUtils {

    /** Converte um endereço textual em coordenadas LatLng usando Android Geocoder (gratuito, sem API key). */
    suspend fun geocodeAddress(context: Context, address: String): LatLng? {
        if (address.isBlank()) return null
        return withContext(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                @Suppress("DEPRECATION")
                val results = geocoder.getFromLocationName(address, 1)
                if (!results.isNullOrEmpty()) {
                    LatLng(results[0].latitude, results[0].longitude)
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }

    /** Converte coordenadas GPS em endereço textual (geocodificação reversa). */
    suspend fun reverseGeocode(context: Context, lat: Double, lng: Double): String? {
        return withContext(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                @Suppress("DEPRECATION")
                val results = geocoder.getFromLocation(lat, lng, 1)
                if (!results.isNullOrEmpty()) {
                    val addr = results[0]
                    buildString {
                        addr.thoroughfare?.let { append(it) }
                        addr.subThoroughfare?.let { append(", $it") }
                        (addr.subLocality ?: addr.locality)?.let { append(", $it") }
                    }.ifBlank { addr.getAddressLine(0) }
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }
}
