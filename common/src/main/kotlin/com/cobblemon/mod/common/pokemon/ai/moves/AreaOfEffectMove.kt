/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.ai.moves

import com.bedrockk.molang.Expression
import com.cobblemon.mod.common.api.types.ElementalType
import com.cobblemon.mod.common.api.types.ElementalTypes
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.asExpression
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity

class AreaOfEffectMove(
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
    val radius : Float = 0.4f,
    val innerRadius : Float = 0.0f, // Allows for big aoes with an inner "safe" zone
    val height : Float = 2.0f,
    val durationTicks: Int = 20, // How long the aoe is active
    val damageTickRate: Int = 4, // Deals damage once every four ticks
    val anchoredToGround: Boolean = true, // If started in air, drops to the ground
    val shape : AOEShape = AOEShape.CYLINDER
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
        targetEntity: LivingEntity,
        parentEntity: Entity?,
        ): RealTimeMoveInstance {
        val instance = AreaOfEffectMoveInstance(this, pokemonEntity, targetEntity, parentEntity)
        RealTimeMoveManager.addInstance(instance)
        return instance
    }

    companion object {
        enum class AOEShape(val shape: Int) {
            SPHERE(1),
            CYLINDER(2),
        }
    }
}