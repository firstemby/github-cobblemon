package com.cobblemon.mod.common.stadium

import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

object StadiumServerManager {
    val activeStadiumBattles = mutableMapOf<UUID, StadiumBattleInstance>()

    fun createNewStadiumBattle(battle: UUID, managerResource: ResourceLocation, participants: MutableList<UUID> = mutableListOf<UUID>()) {
        val newInstance = StadiumBattleInstance(battle, managerResource)
        newInstance.participants = participants
        activeStadiumBattles[battle] = newInstance
    }

    fun terminateNewStadiumBattle(battle: UUID) {
        activeStadiumBattles.remove(battle)
    }

    fun addPlayerToStadiumBattle(battle: UUID, player: UUID) {
        val instance = activeStadiumBattles[battle]
        if (instance != null) {
            if (!instance.participants.contains(player)) instance.participants.add(player)
        }
    }
}

class StadiumBattleInstance(
    val stadiumBattleId: UUID,
    val managerResource: ResourceLocation
){
    var participants = mutableListOf<UUID>()
}