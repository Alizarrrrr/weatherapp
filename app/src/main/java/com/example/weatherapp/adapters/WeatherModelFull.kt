package com.example.weatherapp.adapters

data class WeatherModelFull (
    val city: String,
    val latitude: String,
    val longitude: String,
    val time: String,
    val condition: String,
    val currentTemp: String,
    val maxTemp: String,
    val minTemp: String,
    val imageUrl: String,
    val pressure: String,
    val humidity: String,
    val clouds: String,
    val windSpeed: String,
    val windDeg: String,
    val windGust: String,
    val visibility: String,
    val probabilityPrecipitation: String,
    val rain3h: String,
    val snow3h: String,
    val timeZone: String,
    val sunrise: String,
    val sunset: String,

)