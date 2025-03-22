package com.cobblemon.mod.common.stadium

import net.minecraft.server.level.ServerPlayer
import java.util.*

abstract class StadiumBattleDefenition {
    var stadiumBattleID: UUID = UUID.randomUUID()

    var participants: MutableList<UUID> = mutableListOf<UUID>()
    var spectators: MutableList<UUID> = mutableListOf<UUID>()

    abstract fun readyForBattle(): Boolean

    abstract fun canAddPlayer(player: ServerPlayer): Boolean

    abstract fun addPlayer(player: ServerPlayer)

    abstract fun getPlayers(): MutableList<ServerPlayer>

    abstract fun initializeBattle()

    abstract fun getCurrentCamera(): StadiumCameraDefenition
}