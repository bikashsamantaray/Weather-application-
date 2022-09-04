package com.bikash.weather

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.location.LocationRequest
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.content.ContextCompat.startActivity
import com.bikash.weather.models.Weather
import com.bikash.weather.models.WeatherResponse
import com.bikash.weather.network.WeatherService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private var tvMain: TextView? = null
    private var tvTemp: TextView? = null
    private var tvMainDescription: TextView? = null
    private var tvSunRiseTime: TextView? = null
    private var tvSunSetTime: TextView? = null
    private var tvHumidity: TextView? = null
    private var minTemp: TextView? = null
    private var maxTemp: TextView? = null
    private var tvSpeed: TextView? = null
    private var tvName: TextView? = null
    private var tvCountry: TextView? = null
    private var ivMain: ImageView? = null
    private lateinit var mFusedLocationClient: FusedLocationProviderClient
    private var mProgressDialog: Dialog? = null
    private lateinit var mSharedPreferences: SharedPreferences

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        tvMain = findViewById(R.id.tv_main)
        tvTemp = findViewById(R.id.tv_temp)
        tvMainDescription = findViewById(R.id.tv_main_description)
        tvSunRiseTime = findViewById(R.id.tv_sunrise_time)
        tvSunSetTime = findViewById(R.id.tv_sunset_time)
        tvHumidity = findViewById(R.id.tv_humidity)
        minTemp = findViewById(R.id.tv_min)
        maxTemp = findViewById(R.id.tv_max)
        tvSpeed = findViewById(R.id.tv_speed)
        tvName = findViewById(R.id.tv_name)
        tvCountry = findViewById(R.id.tv_country)
        ivMain = findViewById(R.id.iv_main)


        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        mSharedPreferences = getSharedPreferences(Constants.PREFERENCE_NAME,Context.MODE_PRIVATE)

        //this setupui is for preferred reference basically it stores for previous data
        setupUI()

        if (!isLocationEnabled()){
            Toast.makeText(this,"Your location provider is turned off. Please turn on the gps",Toast.LENGTH_SHORT).show()
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        }else{
            Dexter.withActivity(this).withPermissions(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION)
                .withListener(object : MultiplePermissionsListener{
                    @RequiresApi(Build.VERSION_CODES.S)
                    override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                        if (report!!.areAllPermissionsGranted()){
                            requestLocationData()
                        }
                        if (report.isAnyPermissionPermanentlyDenied) {
                            Toast.makeText(
                                this@MainActivity,
                                "You have denied location permission. Please allow it is mandatory.",
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
                }).onSameThread()
                .check()
        }
    }

    private fun getLocationWeatherDetails(latitude: Double, longitude: Double){
        if (Constants.isNetworkAvailable(this)){
            val retrofit : Retrofit = Retrofit.Builder().baseUrl(Constants.BASE_URL).addConverterFactory(GsonConverterFactory.create()).build()

            val service: WeatherService = retrofit.create(WeatherService::class.java)

            val listCall: Call<WeatherResponse> = service.getWeather(
                latitude,longitude, Constants.METRIC_UNIT, Constants.APP_ID
            )


            showCustomProgressDialog()

            listCall.enqueue(object : Callback<WeatherResponse>{
                @RequiresApi(Build.VERSION_CODES.N)
                override fun onResponse(
                    call: Call<WeatherResponse>,
                    response: Response<WeatherResponse>
                ) {
                    if (response.isSuccessful){
                        hideProgressDialog()
                        val weatherList: WeatherResponse = response.body()!!
                        //start
                        //from start to end this uses shared preferences for data storing of previous api call
                        val weatherResponseJsonString = Gson().toJson(weatherList)
                        val editor = mSharedPreferences.edit()
                        editor.putString(Constants.WEATHER_RESPONSE_DATA, weatherResponseJsonString )
                        editor.apply()
                        //end
                        //if we don't use shared preferences then we can use weatherList in setupui
                        setupUI()
                        Log.i("Response Result", "$weatherList")
                    }else{
                        hideProgressDialog()
                        val rc = response.code()
                        when(rc){
                            400 -> {
                                Log.e("Error 400","bad connection")
                            }
                            404 -> {
                                Log.e("Error 404","not found")
                            }
                            else -> {
                                Log.e("Error ","Generic error")
                            }
                        }
                    }
                }

                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {

                    Log.e("Errorrrrr",t.message.toString())
                    hideProgressDialog()
                }

            })
        }else{
            Toast.makeText(this@MainActivity,"You are not connected to internet",Toast.LENGTH_SHORT).show()
        }
    }

    private fun showRationalDialogForPermissions() {
        AlertDialog.Builder(this)
            .setMessage("It Looks like you have turned off permissions required for this feature." +
                    " It can be enabled under Application Settings only").setPositiveButton("Go to Settings")
            { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package",packageName,null)
                    intent.data = uri
                    startActivity(intent)
                }catch (e: ActivityNotFoundException){
                    e.printStackTrace()
                }
            }.setNegativeButton("Cancel"){
                dialog, _ ->
                dialog.dismiss()
            }.show()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    @SuppressLint("MissingPermission")
    private fun requestLocationData() {
        val mLocationRequest = com.google.android.gms.location.LocationRequest()
        mLocationRequest.priority = LocationRequest.QUALITY_HIGH_ACCURACY

        mFusedLocationClient.requestLocationUpdates(
            mLocationRequest,mLocationCallback,
            Looper.myLooper()
        )

    }
    private val mLocationCallback = object : LocationCallback(){
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation: Location? = locationResult.lastLocation
            val latitude = mLastLocation?.latitude
            Log.i("current latitude", "$latitude")
            val longitude = mLastLocation?.longitude
            Log.i("current latitude", "$longitude")
            if (latitude != null) {
                if (longitude != null) {
                    getLocationWeatherDetails(latitude, longitude)

                }
            }
            //this is also required to refresh the page
            mFusedLocationClient.removeLocationUpdates(this)


        }
    }

    private fun isLocationEnabled(): Boolean{
        val locationManager: LocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun showCustomProgressDialog(){
        mProgressDialog = Dialog(this)
        mProgressDialog!!.setContentView(R.layout.dialog_custom_progress)
        mProgressDialog!!.show()
    }

    private fun hideProgressDialog(){
        if (mProgressDialog != null){
            mProgressDialog!!.dismiss()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {

        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {

            R.id.action_refresh -> {
                requestLocationData()
                true
            }
            else -> super.onOptionsItemSelected(item)

        }
    }

    @SuppressLint("SetTextI18n")
    @RequiresApi(Build.VERSION_CODES.N)

    //if we dont use shared preferences then we can use weatherList: WeatherResponse in setupui call
    private fun setupUI(){
        val weatherResponseJsonString = mSharedPreferences.getString(Constants.WEATHER_RESPONSE_DATA,"")
        if (!weatherResponseJsonString.isNullOrEmpty()){
            val weatherList = Gson().fromJson(weatherResponseJsonString,WeatherResponse::class.java)

            for( i in weatherList.weather.indices){
                Log.i("Weather name", weatherList.weather.toString())
                tvMain?.text = weatherList.weather[i].main
                tvMainDescription?.text = weatherList.weather[i].description
                when(weatherList.weather[i].icon){
                    "01d" -> ivMain?.setImageResource(R.drawable.sunny)
                    "02d" -> ivMain?.setImageResource(R.drawable.cloud)
                    "03d" -> ivMain?.setImageResource(R.drawable.cloud)
                    "04d" -> ivMain?.setImageResource(R.drawable.cloud)
                    "04n" -> ivMain?.setImageResource(R.drawable.cloud)
                    "10d" -> ivMain?.setImageResource(R.drawable.rain)
                    "11d" -> ivMain?.setImageResource(R.drawable.storm)
                    "13d" -> ivMain?.setImageResource(R.drawable.snowflake)
                    "01n" -> ivMain?.setImageResource(R.drawable.cloud)
                    "02n" -> ivMain?.setImageResource(R.drawable.cloud)
                    "03n" -> ivMain?.setImageResource(R.drawable.cloud)
                    "10n" -> ivMain?.setImageResource(R.drawable.cloud)
                    "11n" -> ivMain?.setImageResource(R.drawable.rain)
                    "13n" -> ivMain?.setImageResource(R.drawable.snowflake)
                }
            }
            tvTemp?.text = weatherList.main.temp.toString() + getUnit(application.resources.configuration.locales.toString())
            tvSunRiseTime?.text = unixTime(weatherList.sys.sunrise)
            tvSunSetTime?.text = unixTime(weatherList.sys.sunset)
            tvHumidity?.text = weatherList.main.humidity.toString() + "%"
            minTemp?.text = weatherList.main.temp_min.toString() + " min"
            maxTemp?.text = weatherList.main.feels_like.toString() + " max"
            val weatherInKMPH = (weatherList.wind.speed * 1.609).toFloat()
            tvSpeed?.text = weatherInKMPH.toString()
            tvName?.text = weatherList.name
            if (weatherList.sys.country == "IN"){
                tvCountry?.text = "INDIA"
            }

        }







    }

    private fun getUnit(value: String):String{
        var value = "°C"
        if("US" == value || "LR" == value || "MM" == value){
            value = "°F"
        }
        return value
    }

    @SuppressLint("SimpleDateFormat")
    private fun unixTime(timex: Long):String{
        val date = Date(timex*1000)
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)

    }


}