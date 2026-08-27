/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.spawning.detail

import com.cobblemon.mod.common.Cobblemon.LOGGER
import com.cobblemon.mod.common.api.drop.DropTable
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.spawning.position.AreaSpawnablePosition
import com.cobblemon.mod.common.api.spawning.position.SpawnablePosition
import com.cobblemon.mod.common.api.spawning.selection.SpawnSelectionData
import com.cobblemon.mod.common.util.asIdentifierDefaultingNamespace
import com.cobblemon.mod.common.util.weightedSelection
import com.google.gson.annotations.SerializedName
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth.clamp
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import kotlin.math.ceil

/**
 * A spawn detail that spawns a herd of Pokémon. This relies on the same [PokemonSpawnAction]s as
 * regular [PokemonSpawnDetail]s, but takes advantage of selection context to 'take over' the spawn
 * pass and spawn many for the price of one.
 *
 * Selection will treat this like a single spawn until it chooses this detail once, at which point
 * all the other spawns are removed, a level is calculated for the herd based on [levelRange], and
 * the selector will be left to fit in as many herd members as possible with the remaining spawnable
 * positions.
 *
 * @author Hiroku
 * @since April 22nd, 2025
 */
class PokemonHerdSpawnDetail : SpawnDetail() {
    companion object {
        val TYPE = "pokemon-herd"
    }

    override val type: String = TYPE
    var herdablePokemon = mutableListOf<Herdable>()
    @SerializedName("level", alternate = ["levelRange"])
    var levelRange: IntRange = 1..100
    var maxHerdSize = 10
    var minDistanceBetweenSpawns = 1F

    val herdLevelKey: String
        get() = "${id}__LEVEL"

    class Herdable {
        var pokemon = PokemonProperties()
        val levelRange: IntRange? = null
        var levelRangeOffset: IntRange? = null
        val herdLevelRange: IntRange? = null
        var dropTable: DropTable? = null
        var isLeader = false
        var isFollower = true
        var weight = 1F
        var maxTimes = 10
        var heldItem: ResourceLocation? = null
    }

    override fun isValid() = super.isValid() && herdablePokemon.all { it.pokemon.hasSpecies() && it.weight > 0F && it.maxTimes > 0 }
    override fun onSelection(
        spawnablePosition: SpawnablePosition,
        spawnAction: SpawnAction<*>,
        selectionData: SpawnSelectionData
    ) {
        selectionData.removeSpawnDetails { it != this }
        selectionData.guaranteedBucket = bucket
        selectionData.removeSpawnablePositions { _, pos -> pos.distanceTo(spawnablePosition) < minDistanceBetweenSpawns }

        val herdSpawnCount = selectionData.spawnActions.count { it.detail == this } + 1
        val lacksPossibleLeader = lacksPossibleLeader(selectionData)

        if (herdSpawnCount >= maxHerdSize || getValidHerdMembersForRole(selectionData, useLeaderRole = lacksPossibleLeader).isEmpty()) {
            // We can't spawn any more in the herd so remove this spawn from the selection pool.
            selectionData.removeSpawnDetails { it == this }
        }
    }

    fun lacksPossibleLeader(selectionData: SpawnSelectionData): Boolean {
        val herdable = getValidHerdMembers(selectionData)
        val herdCounts = getHerdMemberCounts(selectionData)

        val leaderIsPossible = herdable.any { it.isLeader }
        val leaderIsSelected = herdCounts.any { it.key?.isLeader == true && it.value > 0 }
        return leaderIsPossible && !leaderIsSelected
    }

    private fun getOrSetHerdLevel(selectionData: SpawnSelectionData): Int {
        return (selectionData.context[herdLevelKey] as? Int) ?: run {
            val level = levelRange.random()
            selectionData.context[herdLevelKey] = level
            return level
        }
    }

    private fun getValidHerdMembersForRole(selectionData: SpawnSelectionData, useLeaderRole: Boolean): List<Herdable> {
        return getValidHerdMembers(selectionData).filter { (useLeaderRole && it.isLeader) || (!useLeaderRole && it.isFollower) }
    }

    override fun createSpawnAction(
        spawnablePosition: SpawnablePosition,
        bucket: String,
        selectionData: SpawnSelectionData
    ): SpawnAction<*> {
        val herdable = getValidHerdMembersForRole(
            selectionData = selectionData,
            useLeaderRole = lacksPossibleLeader(selectionData)
        ).weightedSelection { it.weight } ?: herdablePokemon[0]

        val level = selectionData.context[herdLevelKey] as? Int ?: 1
        var levelRange = herdable.levelRangeOffset?.let { offset ->
            val min = level + offset.first
            val max = level + offset.last
            min..max
        } ?: level..level

        // Attempt to do more detailed anti-wall-spawn logic. Herd members could spawn in walls because
        // we've just been assuming they're 1 block wide (or at least, all in the herd have the same size).
        // Some pokemon are not that though.
        if (spawnablePosition is AreaSpawnablePosition) {
            val species = herdable.pokemon.species?.let { PokemonSpecies.getByIdentifier(it.asIdentifierDefaultingNamespace()) }
            if (species != null) {
                val height = ceil(species.hitbox.height * species.baseScale).toInt()
                val width = ceil(species.hitbox.width * species.baseScale).toInt()
                if (!spawnablePosition.hasSpace(width, height)) {
                    return EmptySpawnAction(spawnablePosition, bucket, EmptySpawnDetail.EMPTY)
                }
            }
        }

        herdable.levelRange?.let {
            val clampedMin = clamp(it.first, levelRange.first, levelRange.last)
            val clampedMax = clamp(it.last, levelRange.first, levelRange.last)
            levelRange = clampedMin..clampedMax
        }

        val heldItem = herdable.heldItem?.let { heldItemId ->
            val item = spawnablePosition.world.registryAccess().registryOrThrow(Registries.ITEM).get(heldItemId)
            if (item == Items.AIR) {
                LOGGER.error("Unable to find matching herd held item for ID: $heldItemId")
                null
            } else {
                ItemStack(item, 1)
            }
        }

        return PokemonSpawnAction(
            spawnablePosition = spawnablePosition,
            bucket = bucket,
            detail = this,
            props = herdable.pokemon,
            drops = herdable.dropTable,
            heldItem = heldItem,
            levelRange = levelRange
        )
    }

    fun getHerdMemberCounts(selectionData: SpawnSelectionData): Map<Herdable?, Int> {
        val relevantActions = selectionData.spawnActions.filterIsInstance<PokemonSpawnAction>().filter { it.detail == this }
        return relevantActions.groupBy { action -> herdablePokemon.find { it.pokemon == action.props } }.mapValues { it.value.size }
    }

    private fun getValidHerdMembers(selectionData: SpawnSelectionData): List<Herdable> {
        val level = getOrSetHerdLevel(selectionData)
        val relevantActions = selectionData.spawnActions.filterIsInstance<PokemonSpawnAction>().filter { it.detail == this }
        val counts = relevantActions.groupBy { action -> herdablePokemon.find { it.pokemon == action.props } }.mapValues { it.value.size }
        return herdablePokemon.filter { herdable ->
            val count = counts[herdable] ?: 0
            return@filter when {
                count >= herdable.maxTimes -> false
                herdable.herdLevelRange != null && level !in herdable.herdLevelRange -> false
                else -> true
            }
        }
    }
}