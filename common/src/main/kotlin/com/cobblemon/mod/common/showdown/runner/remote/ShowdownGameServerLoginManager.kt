package com.cobblemon.mod.common.showdown.runner.remote

import com.google.gson.JsonObject
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

object ShowdownGameServerLoginManager {
    const val GUEST_LOGIN_URL = "https://play.pokemonshowdown.com/api/getassertion"
    val httpClient = HttpClient.newHttpClient()

    fun loginGuest(username: String, loginState: ShowdownGameServerLoginState): String {
        val loginRequest = JsonObject()
        loginRequest.addProperty("userid", username)
        loginRequest.addProperty("challenge", loginState.challStr)
        loginRequest.addProperty("challengekeyid", loginState.challStrKey)
        val httpRequest = HttpRequest.newBuilder()
            .setHeader("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(loginRequest.toString()))
            .uri(URI.create(GUEST_LOGIN_URL))
            .build()
        val httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
        return httpResponse.body()
    }
}