package com.cobblemon.mod.common.stadium

import net.minecraft.server.level.ServerPlayer

interface StadiumBattleDefenition {
    fun readyForBattle(): Boolean

    fun canAddPlayer(player: ServerPlayer): Boolean

    fun addPlayer(player: ServerPlayer)

    fun getPlayers(): MutableList<ServerPlayer>

    fun initializeBattle()

    fun getCurrentCamera(): StadiumCameraDefenition
}