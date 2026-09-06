/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.pokemon.aspect

import com.cobblemon.mod.common.CobblemonBlocks
import com.cobblemon.mod.common.api.ai.config.task.PollinateFlowerTaskConfig
import com.cobblemon.mod.common.api.spawning.influence.SaccharineLogSlatheredInfluence
import com.cobblemon.mod.common.block.entity.PokeSnackBlockEntity
import com.cobblemon.mod.common.client.entity.PokemonClientDelegate
import com.cobblemon.mod.common.client.render.MatrixWrapper
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.core.particles.BlockParticleOption
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.resources.ResourceLocation

/**
 * Used by [PokemonClientDelegate.spawnAspectParticle] to spawn associated particles for aspects
 */
val aspectParticleMap: Map<String, ParticleData> = mapOf(
    SaccharineLogSlatheredInfluence.HONEY_DRENCHED_ASPECT to ParticleData.MinecraftParticle(ParticleTypes.FALLING_HONEY, 0.075, 1),
    PokeSnackBlockEntity.POKE_SNACK_CRUMBED_ASPECT to ParticleData.MinecraftParticle(
        BlockParticleOption(
            ParticleTypes.BLOCK,
            CobblemonBlocks.POKE_SNACK.defaultBlockState()
        ), 0.05, 3),
    PollinateFlowerTaskConfig.HAS_NECTAR_ASPECT to ParticleData.MinecraftParticle(ParticleTypes.FALLING_NECTAR, 0.075, 1),
    "alpha_eyes" to ParticleData.SnowstormParticle(cobblemonResource("alpha_eyes"), 0.25, 1, LocatorResolvers.containing("eye"))
)

sealed class ParticleData {
    data class SnowstormParticle(
        val particle: ResourceLocation,
        val chance: Double,
        val amount: Int,
        val locatorResolver: (Map<String, MatrixWrapper>) -> List<String>
    ): ParticleData()

    data class MinecraftParticle(
        val particle: ParticleOptions,
        val chance: Double,
        val amount: Int
    ): ParticleData()
}

/**
 * Just a few predefined lambdas for common resolutions.
 * vararg allows for comma seperated entries which is kind of crazy
 */
object LocatorResolvers {
    fun containing(substring: String): (Map<String, MatrixWrapper>) -> List<String> =
        { locatorStates -> locatorStates.keys.filter { substring in it.lowercase() } }

    fun exact(vararg names: String): (Map<String, MatrixWrapper>) -> List<String> =
        { locatorStates -> names.filter { locatorStates[it] != null } }

    fun firstMatch(vararg names: String): (Map<String, MatrixWrapper>) -> List<String> =
        { locatorStates -> listOfNotNull(names.firstOrNull { locatorStates[it] != null }) }
}
