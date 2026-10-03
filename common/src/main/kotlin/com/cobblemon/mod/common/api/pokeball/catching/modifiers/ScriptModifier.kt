/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.pokeball.catching.modifiers

import com.bedrockk.molang.runtime.MoLangRuntime
import com.cobblemon.mod.common.api.molang.ExpressionLike
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asBiomeMoLangValue
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asStruct
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asWorldMoLangValue
import com.cobblemon.mod.common.api.molang.MoLangFunctions.setup
import com.cobblemon.mod.common.api.pokeball.catching.CatchRateModifier
import com.cobblemon.mod.common.api.scripting.CobblemonScripts
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.asExpressionLike
import com.cobblemon.mod.common.util.toBlockPos
import com.cobblemon.mod.common.util.withPlayerValue
import com.cobblemon.mod.common.util.withQueryValue
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity

class ScriptModifier(
    val location: ResourceLocation
) : CatchRateModifier {
    companion object {
        const val VARIANT = "script"
        val runtime = MoLangRuntime().setup()
    }

    override fun isGuaranteed(): Boolean = false

    override fun value(
        thrower: LivingEntity,
        pokemon: Pokemon
    ): Float {
        val worldValue =
            thrower
                .level()
                .registryAccess()
                .registryOrThrow(Registries.DIMENSION)
                .wrapAsHolder(thrower.level())
                .asWorldMoLangValue()

        val biomeValue = thrower.level().getBiome(thrower.position().toBlockPos()).asBiomeMoLangValue()

        var ballRuntime = runtime
            .withQueryValue("world", worldValue)
            .withQueryValue("biome", biomeValue)
            .withQueryValue("pokemon", pokemon.asStruct())

        if (thrower is ServerPlayer) {
            ballRuntime = ballRuntime.withPlayerValue("player", thrower)
        }

        return CobblemonScripts.run(location, ballRuntime)?.asDouble()?.toFloat() ?: 0.0f
    }

    override fun behavior(
        thrower: LivingEntity,
        pokemon: Pokemon
    ): CatchRateModifier.Behavior = CatchRateModifier.Behavior.MULTIPLY

    override fun isValid(
        thrower: LivingEntity,
        pokemon: Pokemon
    ): Boolean = true

    override fun modifyCatchRate(
        currentCatchRate: Float,
        thrower: LivingEntity,
        pokemon: Pokemon
    ): Float = this.behavior(thrower, pokemon).mutator(currentCatchRate, this.value(thrower, pokemon))
}
