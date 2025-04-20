/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.spawning.detail

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.Cobblemon.LOGGER
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.spawning.SpawnBucket
import com.cobblemon.mod.common.api.spawning.position.SpawnablePosition
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.feature.SeasonFeatureHandler
import com.cobblemon.mod.common.util.weightedSelection
import net.minecraft.server.level.ServerPlayer

/**
 * A [SpawnAction] that will spawn a single [PokemonEntity].
 *
 * @author Hiroku
 * @since February 13th, 2022
 */
class PokemonSpawnAction(
    spawnablePosition: SpawnablePosition,
    bucket: SpawnBucket,
    override val detail: PokemonSpawnDetail,
    /** The [PokemonProperties] that are about to be used. */
    var props: PokemonProperties = detail.pokemon.copy()
) : SingleEntitySpawnAction<PokemonEntity>(spawnablePosition, bucket, detail) {
    override fun createEntity(): PokemonEntity {
        if (props.species == null) LOGGER.error("PokemonSpawnAction run with null species - Spawn detail: ${detail.id}")
        if (props.level == null) {
            props.level = detail.getDerivedLevelRange().random()
        }
        val heldItems = detail.heldItems?.takeIf { it.isNotEmpty() }?.toMutableList() ?: mutableListOf()
        val heldItem = if (heldItems.isNotEmpty()) {
            val until100 = 1 - heldItems.sumOf { it.percentage / 100 }
            if (until100 > 0 && spawnablePosition.world.random.nextDouble() < until100) {
                null
            } else {
                heldItems.weightedSelection { it.percentage }
            }
        } else {
            null
        }?.createStack(spawnablePosition)
        val sourcePlayer = spawnablePosition.cause.entity as? ServerPlayer
        val entity = props.createEntity(spawnablePosition.world, sourcePlayer)
        entity.spawnCause = spawnablePosition.cause
        SeasonFeatureHandler.updateSeason(entity.pokemon, Cobblemon.seasonResolver(spawnablePosition.world, spawnablePosition.position))
        if (heldItem != null) {
            entity.pokemon.swapHeldItem(heldItem)
        }
        entity.drops = detail.drops
        // Useful debug code in situations where you want to find spawns
//        val fireworkRocketEntity = FireworkRocketEntity(spawnablePosition.world, spawnablePosition.position.x.toDouble(), spawnablePosition.position.y.toDouble() + 2, spawnablePosition.position.z.toDouble(), ItemStack(Items.FIREWORK_ROCKET))
//        spawnablePosition.world.spawnEntity(fireworkRocketEntity)
        return entity
    }
}