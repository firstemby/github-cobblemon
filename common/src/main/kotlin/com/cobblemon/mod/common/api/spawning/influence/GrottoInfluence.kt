package com.cobblemon.mod.common.api.spawning.influence

import com.cobblemon.mod.common.api.habitats.Habitats
import com.cobblemon.mod.common.api.spawning.context.SpawningContext
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnDetail
import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail
import net.minecraft.resources.ResourceLocation
import org.slf4j.LoggerFactory

class GrottoInfluence(
        private val structureId: ResourceLocation,
        private val allowedSpawns: List<ResourceLocation>? = null // null = allow all from habitat
) : SpawningInfluence {

    /*companion object {
        private val LOGGER = LoggerFactory.getLogger("GrottoInfluence")
    }

    override fun affectSpawnable(detail: SpawnDetail, ctx: SpawningContext): Boolean {
        if (detail !is PokemonSpawnDetail) return true
        val detailId = ResourceLocation.tryParse(detail.pokemon.species!!) ?: return false

        // If strict filtering enabled, reject anything not in allowed list
        if (allowedSpawns != null && !allowedSpawns.contains(detailId)) {
            LOGGER.info("[GrottoInfluence] Rejecting ${detailId.path} — not in current moon phase group.")
            return false
        }

        return true
    }

    override fun affectWeight(detail: SpawnDetail, ctx: SpawningContext, weight: Float): Float {
        if (detail !is PokemonSpawnDetail) return weight
        val detailId = ResourceLocation.tryParse(detail.pokemon.species!!) ?: return weight

        val boostTargetList = allowedSpawns ?: Habitats.getHabitat(structureId)?.spawnPool?.flatten() ?: emptyList()
        return if (boostTargetList.contains(detailId)) {
            LOGGER.info("[GrottoInfluence] Boosting spawn weight for ${detailId.path} (structure: $structureId)")
            weight * 100f
        } else {
            weight
        }
    }*/
}