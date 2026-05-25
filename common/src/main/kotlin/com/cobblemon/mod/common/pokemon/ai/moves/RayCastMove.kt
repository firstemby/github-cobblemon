package com.cobblemon.mod.common.pokemon.ai.moves

import com.bedrockk.molang.Expression
import com.cobblemon.mod.common.api.types.ElementalType
import com.cobblemon.mod.common.api.types.ElementalTypes
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.asExpression
import net.minecraft.world.entity.LivingEntity

class RayCastMove(
    name : String = "",
    elementalType: ElementalType = ElementalTypes.NORMAL,
    power : Float = 1f,
    cooldownTicks : Int = 20,
    minRange: Float = 0f,
    maxRange : Float = 10f,
    delayTicks : Int = 0,
    recoveryTicks : Int = 1,
    effect : Expression = "".asExpression(),
    effectChance: Float = 1.0f,
    priority : Int = 1,
    val durationTicks: Int = 1
    // TODO: Explosion
    // TODO: Sticky
    // TODO: selfEffect
    // TODO: targetEffect

) : RealTimeMove(
    name = name,
    elementalType = elementalType,
    power = power,
    cooldownTicks = cooldownTicks,
    minRange = minRange,
    maxRange = maxRange,
    delayTicks = delayTicks,
    recoveryTicks = recoveryTicks,
    effect = effect,
    effectChance = effectChance,
    priority = priority
) {

    override fun createInstance(
        pokemonEntity: PokemonEntity,
        targetEntity: LivingEntity
    ): RealTimeMoveInstance {
        return RayCastMoveInstance(this, pokemonEntity, targetEntity)
    }

}