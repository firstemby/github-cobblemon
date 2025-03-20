package com.cobblemon.mod.common.stadium

import java.util.UUID

object StadiumManager {
    val activeStadiumBattles = mutableListOf<StadiumBattleDefenition>()

    fun playerIsBattling(uuid: UUID) : Boolean {
        for (stadiumBattle in activeStadiumBattles) {
            for (player in stadiumBattle.participants) {
                if (player == uuid) return true
            }
        }
        return false
    }

    fun playerIsSpectating(uuid: UUID) : Boolean {
        for (stadiumBattle in activeStadiumBattles) {
            for (player in stadiumBattle.spectators) {
                if (player == uuid) return true
            }
        }
        return false
    }
}