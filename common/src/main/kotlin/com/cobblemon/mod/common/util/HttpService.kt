package com.cobblemon.mod.common.util

import com.cobblemon.mod.common.Cobblemon
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI

object HttpService {

    val gson: Gson = GsonBuilder().create()

    inline fun <reified T> get(url: String): HttpResponse<T> {
        val connection = (URI.create(url).toURL().openConnection() as HttpURLConnection).also {
            it.requestMethod = "GET"
            it.connectTimeout = 5000
            it.readTimeout = 5000
            it.useCaches = false
        }
        return readFromUrlConnection<T>(connection)
    }

    inline fun <reified T> post(url: String, body: Any? = null): HttpResponse<T> {
        val bodyJson = gson.toJson(body)
        val bodyBytes = bodyJson.toByteArray(Charsets.UTF_8)

        val connection = (URI.create(url).toURL().openConnection() as HttpURLConnection).also {
            it.requestMethod = "POST"
            it.doOutput = true
            it.connectTimeout = 5000
            it.readTimeout = 5000
            it.useCaches = false
            it.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            it.setRequestProperty("Content-Length", bodyBytes.size.toString())
        }

        connection.connect()
        connection.outputStream.use {
            it.write(bodyBytes)
        }

        return readFromUrlConnection<T>(connection)
    }

    inline fun <reified T> readFromUrlConnection(connection: HttpURLConnection): HttpResponse<T> {
        try {
            val status = connection.responseCode

            if (status >= 400) {
                connection.errorStream.use {
                    val error = readFromStream(it)
                    return HttpResponse(error = HttpError(status, error), body = null)
                }
            }
            else {
                connection.inputStream.use {
                    val bodyStr = readFromStream(it)
                    return when {
                        T::class == String::class -> HttpResponse(error = null, body = bodyStr as T)
                        else -> HttpResponse(error = null, body = gson.fromJson(bodyStr, T::class.java))
                    }
                }
            }
        }
        catch (e: Exception) {
            Cobblemon.LOGGER.error("Failed to read from connection url at ${connection.url}", e)
            return HttpResponse(error = HttpError(500, null), body = null)
        }
    }

    fun readFromStream(stream: InputStream): String {
        ByteArrayOutputStream().use { result ->
            val buffer = ByteArray(1024)
            var length: Int
            while (stream.read(buffer).also { length = it } != -1) {
                result.write(buffer, 0, length)
            }
            return result.toString("UTF-8")
        }
    }
}

data class HttpResponse<T>(val error: HttpError?, val body: T?)
data class HttpError(val statusCode: Int, val details: String?)
