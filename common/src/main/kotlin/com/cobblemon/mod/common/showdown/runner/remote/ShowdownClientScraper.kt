package com.cobblemon.mod.common.showdown.runner.remote

import com.cobblemon.mod.common.showdown.runner.graal.GraalLogger
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.HostAccess
import org.graalvm.polyglot.PolyglotAccess
import org.graalvm.polyglot.Source
import java.net.Socket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Allows us to load abilities and such using the pokemon showdown web client's data
 */
class ShowdownClientScraper(
    private val baseUrl: String
) {
    private val context: Context by lazy { createContext() }
    private val httpClient = HttpClient.newHttpClient()
    private val gson = GsonBuilder().create()

    fun createSocket(): Socket {
        val httpRequest = HttpRequest.newBuilder().uri(URI.create("$baseUrl/config/config.js")).GET().build()
        val httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
        val configurationJs = httpResponse.body()
        context.eval(Source.create("js", configurationJs))
        val serverHost = context.eval(Source.create("js","Config.defaultserver.host")).asString()
        val serverPort = context.eval(Source.create("js","Config.defaultserver.port")).asInt()
        return Socket(serverHost, serverPort)
    }

    fun getAbilityIds(): JsonArray {
        val httpRequest = HttpRequest.newBuilder().uri(URI.create("$baseUrl/data/abilities.js")).GET().build()
        val httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
        val configurationJs = httpResponse.body()

        context.eval(Source.create("js", configurationJs))
        val abilityIds = context.eval("js", "JSON.stringify(Object.keys(exports.BattleAbilities))").asString()
        return gson.fromJson(abilityIds, JsonArray::class.java)
    }

    fun getMoves(): JsonObject {
        val httpRequest = HttpRequest.newBuilder().uri(URI.create("$baseUrl/data/moves.js")).GET().build()
        val httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
        val configurationJs = httpResponse.body()
        context.eval(Source.create("js", configurationJs))
        val moves = context.eval("js", "JSON.stringify(exports.BattleMovedex)").asString()
        return gson.fromJson(moves, JsonObject::class.java)
    }

    fun getItemIds(): JsonArray {
        val httpRequest = HttpRequest.newBuilder().uri(URI.create("$baseUrl/data/items.js")).GET().build()
        val httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
        val configurationJs = httpResponse.body()
        context.eval(Source.create("js", configurationJs))
        val itemIds = context.eval("js", "JSON.stringify(Object.keys(exports.BattleItems))").asString()
        return gson.fromJson(itemIds, JsonArray::class.java)
    }

    private fun createContext(): Context {
        val context = Context.newBuilder("js")
            .allowIO(false)
            .allowExperimentalOptions(true)
            .allowPolyglotAccess(PolyglotAccess.ALL)
            .allowHostAccess(HostAccess.NONE)
            .allowCreateThread(false)
            .logHandler(GraalLogger)
            .allowHostClassLoading(false)
            .allowNativeAccess(false)
            .allowCreateProcess(false)
            .build()
        //This exports obj is set up as a global in the showdown client and referenced in some of the scripts we use later
        context.eval("js", "exports = {}")
        return context
    }
}