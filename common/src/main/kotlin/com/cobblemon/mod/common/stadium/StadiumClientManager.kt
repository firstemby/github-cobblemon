package com.cobblemon.mod.common.stadium

import java.util.UUID

object StadiumClientManager {
    var currentStadiumBattleUUID: UUID? = null
    var stadiumBattleDefenition: StadiumBattleDefenition? = null

    fun handleJoin(battleUUID: UUID) {
        currentStadiumBattleUUID = battleUUID
    }
}