package com.cobblemon.mod.common.pokemon.ai.moves.events

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity

abstract class MoveEventAction {

    abstract fun execute(owner: PokemonEntity, target: LivingEntity, parentEntity: Entity)

    companion object {
        val types = mutableMapOf(
            cobblemonResource("createmove") to CreateMoveEventAction::class.java,
        )
    }
}