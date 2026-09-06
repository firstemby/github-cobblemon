/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.ai.moves

import com.bedrockk.molang.Expression
import com.bedrockk.molang.runtime.value.DoubleValue
import com.bedrockk.molang.runtime.value.StringValue
import com.cobblemon.mod.common.api.molang.ObjectValue
import com.cobblemon.mod.common.api.types.ElementalType
import com.cobblemon.mod.common.api.types.ElementalTypes
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.ai.moves.events.CollisionMoveEvent
import com.cobblemon.mod.common.pokemon.ai.moves.events.TickMoveEvent
import com.cobblemon.mod.common.util.asExpression
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity

abstract class RealTimeMove(
    val name: String = "",
    val elementalType: ElementalType = ElementalTypes.NORMAL, // Not deserializing
    val power: Float = 1f,
    val cooldownTicks: Int = 20,
    val minRange: Float = 0f,
    val maxRange: Float = 10f,
    val delayTicks: Int = 0,
    val recoveryTicks: Int = 1,
    val effect: Expression = "".asExpression(),
    val effectChance: Float = 1.0f,
    val priority: Int = 1,
    val anchoredToOwner: Boolean = false,
    val targetOpponent: Boolean = true,
    val tickEvents: List<TickMoveEvent> = emptyList(),
    val collisionEvents: List<CollisionMoveEvent> = emptyList()

) {
    lateinit var id: ResourceLocation

    @Transient
    val struct = ObjectValue(this).also {
        it.addFunction("name") { StringValue(name) }
        it.addFunction("power") { DoubleValue(power) }
        it.addFunction("minRange") { DoubleValue(minRange) }
        it.addFunction("maxRange") { DoubleValue(maxRange) }
        it.addFunction("cooldown_ticks") { DoubleValue(cooldownTicks) }
        it.addFunction("delay_ticks") { DoubleValue(delayTicks) }
        it.addFunction("recovery_ticks") { DoubleValue(recoveryTicks) }
        it.addFunction("priority") { DoubleValue(priority) }
    }


    abstract fun createInstance(pokemonEntity: PokemonEntity, targetEntity: LivingEntity, parentEntity: Entity? = null ): RealTimeMoveInstance

    companion object {
        val defaultMelee: RealTimeMove = RayCastMove(
            name = "Tackle",
            power = 1f,
            cooldownTicks = 15,
            minRange = 0f,
            maxRange = 0f,
            priority = 9999,
        ).also {
            it.id = cobblemonResource("tackle")
        }

        val defaultRange: RealTimeMove = ProjectileMove(
            name = "Swift",
            power = 3f,
            cooldownTicks = 40,
            minRange = 0f,
            maxRange = 10f,
            priority = 9999,
        ).also {
            it.id = cobblemonResource("swift")
        }

        val types = mutableMapOf(
            cobblemonResource("projectile") to ProjectileMove::class.java,
            cobblemonResource("raycast") to RayCastMove::class.java,
            cobblemonResource("dash") to DashMove::class.java,
            cobblemonResource("aoe") to AreaOfEffectMove::class.java,
            )
    }
}