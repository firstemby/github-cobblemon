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
import net.minecraft.world.phys.Vec3


class DashMove(
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
    val durationTicks: Int = 1, // Max duration, if we don't reach the max distance before the tick count ends, the move will end
    val hitboxInflate: Vec3 = Vec3(1.0, 1.0, 1.0),
    val hitboxInflateY: Float = 1.0f,
    val piecing: Boolean = false,
    val stopAtLedges: Boolean = false,
    val knockbackForce: Vec3? = null,
    val speed: Float = 1.0f,
    val distance: Float = 1.0f,

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
        val instance = DashMoveInstance(this, pokemonEntity, targetEntity)
        RealTimeMoveManager.addInstance(instance)
        return instance
    }

}