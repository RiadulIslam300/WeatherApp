package com.example.weatherapp

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

object Constants {
    const val BASE_URL: String = "https://api.openweathermap.org/data/"
    const val APP_ID: String = "4d38657be2880b0bb55d16f97d402584"
    const val METRIC = "metric"
    const val WEATHER_RESPONSE_DATA="weather_response_data"
    const val PREFERENCE_NAME="weatherAppPreference"
    fun isNetworkAvailable(context: Context): Boolean {
        val connectionManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
                as ConnectivityManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectionManager.activeNetwork ?: return false
            val activeNetwork = connectionManager.getNetworkCapabilities(network) ?: return false
            return when {
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                else -> false
            }
        } else {
            val networkInfo = connectionManager.activeNetworkInfo
            return networkInfo != null && networkInfo.isConnectedOrConnecting
        }


    }
}