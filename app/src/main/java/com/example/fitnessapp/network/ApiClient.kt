package com.example.fitnessapp.network

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

data class ApiResult(
    val success: Boolean,
    val message: String,
    val data: JSONObject = JSONObject()
)

object ApiClient {
    // 10.0.2.2 points from the Android emulator to the computer running XAMPP.
    const val BASE_URL = "http://192.168.100.44/fitness_api/"

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun post(
        endpoint: String,
        parameters: Map<String, String>,
        callback: (ApiResult) -> Unit
    ) {
        executor.execute {
            val result = try {
                val body = parameters.entries.joinToString("&") { (key, value) ->
                    "${encode(key)}=${encode(value)}"
                }
                val connection = (URL(BASE_URL + endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 8000
                    readTimeout = 8000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                }

                connection.outputStream.bufferedWriter().use { it.write(body) }
                val stream = if (connection.responseCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }
                val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                connection.disconnect()

                val json = JSONObject(responseText)
                ApiResult(
                    success = json.optBoolean("success"),
                    message = json.optString("message", "Request completed"),
                    data = json
                )
            } catch (error: Exception) {
                ApiResult(false, "Cannot reach the server. Check XAMPP and the API URL.")
            }

            mainHandler.post { callback(result) }
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
