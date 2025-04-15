package com.cobblemon.mod.common.api.spawning.influence

import com.cobblemon.mod.common.api.habitats.Habitats
import com.cobblemon.mod.common.api.spawning.context.SpawningContext
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnDetail
import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail
import net.minecraft.resources.ResourceLocation
import org.slf4j.LoggerFactory

class GrottoInfluence(val structureId: ResourceLocation) : SpawningInfluence {

    companion object {
        private val LOGGER = LoggerFactory.getLogger("GrottoInfluence")
    }

    override fun affectWeight(detail: SpawnDetail, ctx: SpawningContext, weight: Float): Float {
        if (detail !is PokemonSpawnDetail) return weight

        val habitat = Habitats.getHabitat(structureId)
        if (habitat != null) {
            val detailId = ResourceLocation.tryParse(detail.pokemon.species!!) ?: return weight
            if (habitat.spawnPool.contains(detailId)) {
                LOGGER.info("[GrottoInfluence] Boosting spawn weight for ${detailId} (from structure: $structureId)")
                return weight * 10f
            }
        }

        return weight
    }
}
