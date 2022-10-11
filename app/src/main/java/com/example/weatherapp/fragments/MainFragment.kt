package com.example.weatherapp.fragments

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.activityViewModels
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.example.weatherapp.DialogManager
import com.example.weatherapp.MainViewModel
import com.example.weatherapp.R
import com.example.weatherapp.adapters.VpAdapter
import com.example.weatherapp.adapters.WeatherModel
import com.example.weatherapp.databinding.FragmentMainBinding
import com.example.weatherapp.isPermissionGranted
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.material.tabs.TabLayoutMediator
import com.squareup.picasso.Picasso
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.collections.ArrayList

const val API_KEY = "3a87e67803b9e72b62dfcf7fe3975409"
//const val API_KEY = "375225f1d82845a38fb82302221509"

class MainFragment : Fragment() {
    private lateinit var fLocationClient: FusedLocationProviderClient
    private val fList = listOf(
        HoursFragment.newInstance(),
        DaysFragment.newInstance()
    )
    private val tList = listOf(
        "Hours",
        "Days"
    )
    private lateinit var pLauncher: ActivityResultLauncher<String>
    private lateinit var binding: FragmentMainBinding
    private val model: MainViewModel by activityViewModels()
    private val language: String = "en"
    private var currentLatitude: String = ""
    private val currentLongitude: String = ""


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        checkPermission()
        init()
        updateCurrentCard()

    }

    override fun onResume() {
        super.onResume()
        checkLocation()
    }

    private fun init() = with(binding) {
        fLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())
        val adapter = VpAdapter(activity as FragmentActivity, fList)
        vp.adapter = adapter
        TabLayoutMediator(tabLayout, vp) { tab, pos ->
            tab.text = tList[pos]
        }.attach()
        ibSync.setOnClickListener {
            tabLayout.selectTab(tabLayout.getTabAt(0))
            checkLocation()
        }
        ibSearch.setOnClickListener {
            DialogManager.searchByNameDialog(requireContext(), object : DialogManager.Listener {
                override fun onClick(name: String?) {
                    name?.let { it1 ->
                        pCoordinatesFromCity(it1)
                        searchCity = true
                        city = it1
                    }
                    //requestWeatherData(it1) }
                }
            })
        }
    }

    private fun checkLocation() {
        if (isLocationEnabled()) {
            getLocation()
        } else {
            DialogManager.locationSettingsDialog(requireContext(), object : DialogManager.Listener {
                override fun onClick(name: String?) {
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }

            })
        }
    }

    private fun isLocationEnabled(): Boolean {
        val lm = activity?.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    private fun getLocation() {
        val ct = CancellationTokenSource()

        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            return
        }
        fLocationClient
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, ct.token)
            .addOnCompleteListener {
                requestWeatherData(it.result.latitude.toString() , it.result.longitude.toString())

            }
    }

    private fun updateCurrentCard() = with(binding) {
        model.liveDataCurrent.observe(viewLifecycleOwner) {
            val maxMinTemp = "${it.maxTemp}°С/${it.minTemp}°С"
            tvData.text = it.time
            Picasso.get().load(it.imageUrl).into(imWeather)
            tvCiti.text = it.city
            tvCurrentTemp.text = if (it.currentTemp.isEmpty()) {
                maxMinTemp
            } else "${it.currentTemp}°С"

            //"${it.currentTemp}°С"
            tvCondition.text = it.condition
            tvMaxMin.text = if (it.currentTemp.isEmpty()) "" else maxMinTemp
        }
    }

    private fun permissionListener() {
        pLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {
            Toast.makeText(activity, "Permission is $it", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkPermission() {
        if (!isPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            permissionListener()
            pLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun checkCityCoordinate(latitude: String, longitude: String) {
        if (searchCity) {
            pCoordinatesFromCity(city)
        } else {
            lat = latitude
            lon = longitude
            pCityFromCoordinates(lat, lon)

        }
    }

    private fun dateConverter(dateUTC: String): String {
        val shiftTime = System.currentTimeMillis() - Calendar.getInstance().timeInMillis
        return DateTimeFormatter.ISO_INSTANT
            .format(Instant.ofEpochSecond(dateUTC.toLong() + shiftTime/1000)).toString()
    }

    private fun requestWeatherData(latitude: String, longitude: String) {
        checkCityCoordinate(latitude, longitude)
        val urlCurrent = "https://api.openweathermap.org/data/2.5/weather" +
                "?lat=" +
                latitude +
                "&lon=" +
                longitude +
                "&appid=" +
                API_KEY +
                "&units=metric" +
                "&lang=" +
                language

        val urlForecast = "https://api.openweathermap.org/data/2.5/forecast" +
                "?lat=" +
                latitude +
                "&lon=" +
                longitude +
                "&appid=" +
                API_KEY +
                "&units=metric" +
                "&lang=" +
                language

        val queue = Volley.newRequestQueue(context)
        val requestCurrent = StringRequest(
            Request.Method.GET,
            urlCurrent,
            { result ->
                parseCurrentData(result)

            },
            { error ->
                Log.d("MyLog", "Error: $error Current")
            }
        )
        val requestForecast = StringRequest(
            Request.Method.GET,
            urlForecast,
            { result ->
                parseWeatherData(result)

            },
            { error ->
                Log.d("MyLog", "Error: $error Forecast")
            }
        )

        queue.add(requestCurrent)
        queue.add(requestForecast)
    }

    private fun parseWeatherCurrentData(mainObject: JSONObject) {
        val item = WeatherModel(
            city,
            lat,
            lon,
            dateConverter(mainObject.getString("dt")),
            (mainObject.getJSONArray("weather")[0] as JSONObject).getString("description"),
            mainObject.getJSONObject("main").getString("temp"),
            mainObject.getJSONObject("main").getString("temp_max"),
            mainObject.getJSONObject("main").getString("temp_min"),
            "http://openweathermap.org/img/wn/${
                (mainObject.getJSONArray("weather")[0] as JSONObject)
                    .getString("icon")
            }.png",
            ""
        )
        model.liveDataCurrent.value = item
    }

    private fun parseWeatherForecastData(mainObject: JSONObject): List<WeatherModel> {
        val list = ArrayList<WeatherModel>()
        val daysArray = mainObject.getJSONArray("list")
        for (i in 0 until daysArray.length()) {
            val day = daysArray[i] as JSONObject
            val item = WeatherModel(
                city,
                lat,
                lon,
                dateConverter(day.getString("dt")),
                (day.getJSONArray("weather")[0] as JSONObject).getString("description"),
                day.getJSONObject("main").getString("temp"),
                day.getJSONObject("main").getString("temp_max"),
                day.getJSONObject("main").getString("temp_min"),
                "http://openweathermap.org/img/wn/${
                    (day.getJSONArray("weather")[0] as JSONObject)
                        .getString("icon")
                }.png",
                day.getString("dt_txt")

                )
            list.add(item)
        }

        model.liveDataList.value = list
        return list

    }

    private fun pCityFromCoordinates(latitude: String, longitude: String): String {
        val url = "http://api.openweathermap.org/geo/1.0/reverse" +
                "?lat=" +
                latitude +
                "&lon=" +
                longitude +
                "&limit=5&appid=" +
                API_KEY
        val queue = Volley.newRequestQueue(context)
        val requestCity = StringRequest(
            Request.Method.GET,
            url,
            { result ->
                val mainObject = JSONArray(result)[0] as JSONObject
                city = mainObject.getJSONObject("local_names").getString(language)

            },
            { error ->
                Log.d("MyLog", "Error: $error Forecast")
            }
        )
        queue.add(requestCity)
        return city
    }

    private fun pCoordinatesFromCity(cCity: String): Pair<String, String> {
        val url = "http://api.openweathermap.org/geo/1.0/direct" +
                "?q=" +
                cCity +
                "&limit=5&appid=" +
                API_KEY
        val queue = Volley.newRequestQueue(context)
        val requestCoordinates = StringRequest(
            Request.Method.GET,
            url,
            { result ->
                val mainObject = JSONArray(result)[0] as JSONObject
                lat = mainObject.getString("lat")
                lon = mainObject.getString("lon")

            },
            { error ->
                Log.d("MyLog", "Error: $error Forecast")
            }
        )
        queue.add(requestCoordinates)
        return Pair(lat, lon)

    }

    private fun parseWeatherData(result: String) {
        val mainObject = JSONObject(result)
        parseWeatherForecastData(mainObject)
        //val list = parseDays(mainObject)
        //parseWeatherCurrentData(mainObject, list[0])
    }
    private fun parseCurrentData(result: String){
        val mainObject = JSONObject(result)
        parseWeatherCurrentData(mainObject)
    }
/*
    private fun parseDays(mainObject: JSONObject): List<WeatherModel> {
        val list = ArrayList<WeatherModel>()
        val daysArray = mainObject.getJSONObject("forecast")
            .getJSONArray("forecastday")
        val name = mainObject.getJSONObject("location").getString("name")
        for (i in 0 until daysArray.length()) {
            val day = daysArray[i] as JSONObject
            val item = WeatherModel(
                name,
                day.getString("date"),
                day.getJSONObject("day")
                    .getJSONObject("condition")
                    .getString("text"),
                "",
                day.getJSONObject("day")
                    .getString("maxtemp_c").toFloat().toInt().toString(),
                day.getJSONObject("day")
                    .getString("mintemp_c").toFloat().toInt().toString(),
                day.getJSONObject("day")
                    .getJSONObject("condition")
                    .getString("icon"),
                day.getJSONArray("hour").toString()
            )
            list.add(item)
        }
        model.liveDataList.value = list
        return list
    }

 */
/*
    private fun parseCurrentData(mainObject: JSONObject, weatherItem: WeatherModel) {
        val item = WeatherModel(
            mainObject.getJSONObject("location").getString("name"),
            mainObject.getJSONObject("current").getString("last_updated"),
            mainObject.getJSONObject("current")
                .getJSONObject("condition")
                .getString("text"),
            mainObject.getJSONObject("current").getString("temp_c"),
            weatherItem.maxTemp,
            weatherItem.minTemp,
            mainObject.getJSONObject("current")
                .getJSONObject("condition")
                .getString("icon"),
            weatherItem.hours
        )
        model.liveDataCurrent.value = item


    }

 */


    companion object {
        @JvmStatic
        fun newInstance() = MainFragment()
        var city: String = ""
        var searchCity: Boolean = false
        var lat: String = ""
        var lon: String = ""

    }

}