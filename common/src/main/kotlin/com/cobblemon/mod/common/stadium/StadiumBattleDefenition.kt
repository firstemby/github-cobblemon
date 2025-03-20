package com.cobblemon.mod.common.stadium

import net.minecraft.server.level.ServerPlayer
import java.util.*

interface StadiumBattleDefenition {
    var participants: MutableList<UUID>
    var spectators: MutableList<UUID>

    fun readyForBattle(): Boolean

    fun canAddPlayer(player: ServerPlayer): Boolean

    fun addPlayer(player: ServerPlayer)

    fun getPlayers(): MutableList<ServerPlayer>

    fun initializeBattle()

    fun getCurrentCamera(): StadiumCameraDefenition
}