package com.cobblemon.mod.common.pokedex.scanner.block

import com.cobblemon.mod.common.api.pokedex.scan.BlockScanDefinition
import com.cobblemon.mod.common.block.entity.PrintedPhotoDisplayBlockEntity
import com.cobblemon.mod.common.net.messages.server.pokedex.scanner.FinishPrintedPhotoScanningPacket
import com.cobblemon.mod.common.pokedex.photo.PokedexPhotoRatings
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.level.block.entity.BlockEntity

object PrintedPhotoBlockScanProvider : BlockScanProvider {
    override val frames = listOf(
        BlockScanDefinition.Frame(
            slot = 1,
            text = BlockScanDefinition.ScanText.Translate(
                key = "ui.pokedex.scan.block.printed_photo_rating",
                with = listOf(BlockScanDefinition.ScanText.Key("rating"))
            )
        ),
        BlockScanDefinition.Frame(
            slot = 2,
            text = BlockScanDefinition.ScanText.Translate(
                key = "ui.pokedex.scan.block.printed_photo_score",
                with = listOf(BlockScanDefinition.ScanText.Key("score"))
            )
        ),
        BlockScanDefinition.Frame(
            slot = 3,
            text = BlockScanDefinition.ScanText.Translate(
                key = "ui.pokedex.scan.block.printed_photo_species_count",
                with = listOf(BlockScanDefinition.ScanText.Key("species_count"))
            ),
            scale = 1.0f
        )
    )

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

    override fun onScanComplete(pos: BlockPos, zoomLevel: Int) {
        FinishPrintedPhotoScanningPacket(pos, zoomLevel).sendToServer()
        Minecraft.getInstance().player?.displayClientMessage(
            Component.translatable("cobblemon.ui.pokedex.photo_scan.sent"),
            true
        )
    }
}
