package com.cobblemon.mod.common.stadium

import net.minecraft.client.Camera
import java.util.UUID

object StadiumClientManager {
    var currentStadiumBattleUUID: UUID? = null
    var stadiumBattleDefenition: StadiumBattleDefenition? = null

    val camera = Camera()

    fun handleJoin(battleUUID: UUID) {
        currentStadiumBattleUUID = battleUUID
    }

    fun inStadiumBattle(): Boolean {
        return currentStadiumBattleUUID != null
    }

    fun shouldUseStadiumCamera(): Boolean {
        return inStadiumBattle()
    }
}