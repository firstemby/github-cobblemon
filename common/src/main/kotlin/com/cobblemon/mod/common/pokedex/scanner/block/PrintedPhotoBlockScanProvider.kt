package com.cobblemon.mod.common.pokedex.scanner.block

import com.cobblemon.mod.common.block.entity.PrintedPhotoDisplayBlockEntity
import com.cobblemon.mod.common.pokedex.photo.PokedexPhotoRatings
import net.minecraft.world.level.block.entity.BlockEntity

object PrintedPhotoBlockScanProvider : BlockScanProvider {
    override fun getValues(be: BlockEntity): Map<String, Any?> {
        val component = (be as? PrintedPhotoDisplayBlockEntity)?.component
        val score = component?.qualityScore ?: 0
        val speciesCount = component?.detectedSpecies?.size ?: 0

        return mapOf(
            "rating" to PokedexPhotoRatings.ratingText(score),
            "score" to "$score/5000",
            "species_count" to speciesCount
        )
    }
}
