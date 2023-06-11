package com.example.weatherapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.weatherapp.models.ResponseWeather
import com.example.weatherapp.network.WeatherService
import com.google.android.gms.location.*
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import kotlinx.android.synthetic.main.activity_main.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity() {

    private lateinit var mFusedLocationProviderClient: FusedLocationProviderClient
    private var mProgressDialog: Dialog? = null
    private lateinit var mSharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
          mSharedPreferences=getSharedPreferences(Constants.PREFERENCE_NAME, Context.MODE_PRIVATE)
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        setUpUi()
        if (!isLocationEnabled()) {
            Toast.makeText(
                this,
                "Please turn on the location",
                Toast.LENGTH_SHORT
            ).show()
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        } else {
            Dexter.withActivity(this).withPermissions(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ).withListener(object : MultiplePermissionsListener {
                override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                    if (report!!.areAllPermissionsGranted()) {
                        requestNewLocationData()
                    }
                    if (report?.isAnyPermissionPermanentlyDenied) {
                        Toast.makeText(
                            this@MainActivity,
                            "Please turn on the location as it is required for this application",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onPermissionRationaleShouldBeShown(
                    permissions: MutableList<PermissionRequest>?,
                    token: PermissionToken?
                ) {
                    showRationalDialogForPermissions()
                }

            }).onSameThread().check()
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestNewLocationData() {
        var mLocationRequest = LocationRequest()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

        mFusedLocationProviderClient.requestLocationUpdates(
            mLocationRequest, mLocationcallback,
            Looper.myLooper()!!
        )
    }

    private val mLocationcallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation: Location = locationResult.lastLocation
            var mLatitude = mLastLocation.latitude
            Log.i("Latitude", "$mLatitude")
            var mLongitude = mLastLocation.longitude
            Log.i("longitude", "$mLongitude")
            getLocationWeatherData(mLatitude, mLongitude)
        }
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager: LocationManager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

    }

    private fun showCustomProgressDialog() {
        mProgressDialog = Dialog(this)

        /*Set the screen content from a layout resource.
        The resource will be inflated, adding all top-level views to the screen.*/
        mProgressDialog!!.setContentView(R.layout.dialog_custom_progress)

        //Start the dialog and display it on screen.
        mProgressDialog!!.show()
    }

    /**
     * This function is used to dismiss the progress dialog if it is visible to user.
     */
    private fun hideProgressDialog() {
        if (mProgressDialog != null) {
            mProgressDialog!!.dismiss()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main,menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId){
            R.id.action_refresh->{
                requestNewLocationData()
                true
            }else ->super.onOptionsItemSelected(item)

        }

    }

    fun getLocationWeatherData(latitude: Double, longitude: Double) {
        if (Constants.isNetworkAvailable(this)) {
            val retrofit = Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val service: WeatherService =
                retrofit.create<WeatherService>(WeatherService::class.java)
            val listCall: Call<ResponseWeather> = service.getWeather(
                latitude, longitude, Constants.METRIC, Constants.APP_ID
            )
            showCustomProgressDialog()
            listCall.enqueue(object : Callback<ResponseWeather> {
                @RequiresApi(Build.VERSION_CODES.N)
                override fun onResponse(
                    call: Call<ResponseWeather>,
                    response: Response<ResponseWeather>
                ) {
                    if (response.isSuccessful) {
                        val weatherList: ResponseWeather = response.body()!!
                        hideProgressDialog()
                        val weatherResponseJsonString=Gson().toJson(weatherList)
                        val editor=mSharedPreferences.edit()
                        editor.putString(Constants.WEATHER_RESPONSE_DATA,weatherResponseJsonString)
                        editor.apply()
                        Log.i("Response weather", "$weatherList")
                    } else {
                        val rc = response.code()
                        when (rc) {
                            400 -> {
                                Log.e("error", "Bad connection")
                            }
                            404 -> {
                                Log.e("error", "Not Found")
                            }
                            else -> {
                                Log.e("error", "Generic error")
                            }
                        }
                    }
                }

                override fun onFailure(call: Call<ResponseWeather>, t: Throwable) {
                    Log.e("errorrrr", t.message.toString())
                }


            })
        } else {
            Toast.makeText(
                this@MainActivity,
                "Please turn on your internet connection",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun showRationalDialogForPermissions() {
        AlertDialog.Builder(this@MainActivity)
            .setMessage("It Looks like you have turned off permissions required for this feature. It can be enabled under Application Settings")
            .setPositiveButton(
                "GO TO SETTINGS"
            ) { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    e.printStackTrace()
                }
            }
            .setNegativeButton("Cancel") { dialog,
                                           _ ->
                dialog.dismiss()
            }.show()
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun setUpUi() {
        val weatherResponseJsonString=mSharedPreferences.getString(Constants.WEATHER_RESPONSE_DATA,"")
        if(!weatherResponseJsonString.isNullOrEmpty()){
            val weatherList=Gson().fromJson(weatherResponseJsonString,ResponseWeather::class.java)

            for (i in weatherList.weather.indices) {
                Log.i("weather name", weatherList.weather.toString())
                tv_main.text = weatherList.weather[i].main
                tv_main_description.text = weatherList.weather[i].description
                tv_temp.text = weatherList.main.temp.toString() + getUnit(
                    application.resources.configuration
                        .locales.toString()
                )
                tv_sunrise_time.text=unixTime(weatherList.sys.sunrise)
                tv_sunset_time.text=unixTime(weatherList.sys.sunset)
                tv_humidity.text=weatherList.main.humidity.toString()+" per cent"
                tv_speed.text=weatherList.wind.speed.toString()
                tv_name.text = weatherList.name
                tv_country.text = weatherList.sys.country
                tv_min.text = weatherList.main.temp_min.toString() + " min"
                tv_max.text = weatherList.main.temp_max.toString() + " max"

                when(weatherList.weather[i].icon){
                    "01d" ->iv_main.setImageResource(R.drawable.sunny)
                    "02d" ->iv_main.setImageResource(R.drawable.cloud)
                    "03d" ->iv_main.setImageResource(R.drawable.cloud)
                    "04d" ->iv_main.setImageResource(R.drawable.cloud)
                    "04n" ->iv_main.setImageResource(R.drawable.cloud)
                    "10d" -> iv_main.setImageResource(R.drawable.rain)
                    "11d" -> iv_main.setImageResource(R.drawable.storm)
                    "13d" -> iv_main.setImageResource(R.drawable.snowflake)
                    "01n" ->iv_main.setImageResource(R.drawable.cloud)
                    "02n" ->iv_main.setImageResource(R.drawable.cloud)
                    "03n" ->iv_main.setImageResource(R.drawable.cloud)
                    "10n" ->iv_main.setImageResource(R.drawable.cloud)
                    "11n" ->iv_main.setImageResource(R.drawable.rain)
                    "13n" ->iv_main.setImageResource(R.drawable.snowflake)
                    "50d" ->iv_main.setImageResource(R.drawable.mist)
                }
            }
        }

    }

    private fun getUnit(value: String): String? {
        Log.i("unitttttt", value)
        var value = "°C"
        if ("US" == value || "LR" == value || "MM" == value) {
            value = "°F"
        }
        return value

    }

    private fun unixTime(timex:Long):String?{
        val date =Date(timex*1000L)
        val sdf=SimpleDateFormat("HH:mm",Locale.getDefault())
        sdf.timeZone=TimeZone.getDefault()
        return sdf.format(date)
    }
}