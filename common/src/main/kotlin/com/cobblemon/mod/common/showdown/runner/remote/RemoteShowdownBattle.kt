package com.cobblemon.mod.common.showdown.runner.remote

import com.cobblemon.mod.common.showdown.runner.ShowdownBattle
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject

class RemoteShowdownBattle(
    override val id: String,
    val gameType: String,
    val remoteSide: String,
    val teamObject: JsonObject
) : ShowdownBattle() {
    override fun sendMessages(messages: List<String>) {
        TODO("Not yet implemented")
    }

    class Builder(val id: String) {
        val gson = GsonBuilder().create()

        var gameType: String? = null
        var localSide: String? = null
        var teamStr: String? = null

        fun gameType(gameType: String) = apply { this.gameType = gameType }
        fun remoteSide(localSide: String) = apply { this.localSide = localSide }
        fun teamStr(teamStr: String) = apply { this.teamStr = teamStr }
        fun build(): RemoteShowdownBattle {
            val teamObj = gson.fromJson(teamStr, JsonObject::class.java)
            return RemoteShowdownBattle(id, gameType!!, localSide!!, teamObj)
        }
    }
}