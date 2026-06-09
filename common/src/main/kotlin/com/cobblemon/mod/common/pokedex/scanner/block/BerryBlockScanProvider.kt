package com.cobblemon.mod.common.pokedex.scanner.block

import com.cobblemon.mod.common.api.pokedex.scan.BlockScanDefinition
import com.cobblemon.mod.common.api.pokedex.scan.ScannedDataCategory
import com.cobblemon.mod.common.api.text.bold
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.block.BerryBlock
import com.cobblemon.mod.common.block.entity.BerryBlockEntity
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity

object BerryBlockScanProvider : BlockScanProvider {
    override val category = ScannedDataCategory.BERRIES
    override val frames = listOf(
        BlockScanDefinition.Frame(
            slot = 1,
            text = BlockScanDefinition.ScanText.Translate(
                key = "ui.pokedex.scan.block.berry_stage",
                with = listOf(BlockScanDefinition.ScanText.Key("stage"))
            )
        ),
        BlockScanDefinition.Frame(
            slot = 2,
            text = BlockScanDefinition.ScanText.Translate(
                key = "ui.pokedex.scan.block.berry",
                with = listOf(BlockScanDefinition.ScanText.Key("berry_name"))
            )
        ),
        BlockScanDefinition.Frame(
            slot = 3,
            text = BlockScanDefinition.ScanText.Translate(
                key = "ui.pokedex.scan.block.berry_mulch",
                with = listOf(BlockScanDefinition.ScanText.Key("mulch_variant"))
            ),
            scale = 1.0f
        )
    )

    override fun getScanKey(be: BlockEntity): ResourceLocation = (be as BerryBlockEntity).berryIdentifier

    override fun getValues(be: BlockEntity): Map<String, Any?> {
        val berry = be as BerryBlockEntity
        val stage = runCatching { berry.blockState.getValue(BerryBlock.AGE) }.getOrDefault(0)
        val berryName = berry.berry()?.item()?.let { ItemStack(it).hoverName.copy().bold() }
            ?: berry.berryIdentifier.toString().text().bold()

        return mapOf(
            "stage" to stage,
            "mulch_variant" to berry.mulchVariant.toString(),
            "berry_identifier" to berry.berryIdentifier.toString(),
            "berry_name" to berryName
        )
    }
}
