package com.cobblemon.mod.common.pokedex.scanner.block

import com.cobblemon.mod.common.api.pokedex.scan.BlockScanDefinition
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.entity.BlockEntity

interface BlockScanProvider {
    val frames: List<BlockScanDefinition.Frame>
    fun getScanKey(be: BlockEntity): ResourceLocation
    fun getValues(be: BlockEntity): Map<String, Any?>
    fun onScanComplete(pos: BlockPos, zoomLevel: Int) {}
}
