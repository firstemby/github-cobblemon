package com.cobblemon.mod.common.pokedex.scanner.block

import com.cobblemon.mod.common.api.text.bold
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.block.BerryBlock
import com.cobblemon.mod.common.block.entity.BerryBlockEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity

object BerryBlockScanProvider : BlockScanProvider {
    override fun getValues(be: BlockEntity): Map<String, Any?> {
        val berry = be as BerryBlockEntity

        val stage = runCatching { berry.blockState.getValue(BerryBlock.AGE) }.getOrDefault(0)
        val berryName =
            berry.berry()?.item()?.let { ItemStack(it).hoverName.copy().bold() }
                ?: berry.berryIdentifier.toString().text().bold()

        return mapOf(
            "stage" to stage,
            "mulch_variant" to berry.mulchVariant.toString(),
            "berry_identifier" to berry.berryIdentifier.toString(),
            "berry_name" to berryName,
            "next_stage_seconds" to (berry.stageTimer / 20)
        )
    }
}