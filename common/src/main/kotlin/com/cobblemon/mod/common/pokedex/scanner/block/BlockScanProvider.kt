package com.cobblemon.mod.common.pokedex.scanner.block

import com.cobblemon.mod.common.api.pokedex.scan.BlockScanDefinition
import net.minecraft.world.level.block.entity.BlockEntity

interface BlockScanProvider {
    val frames: List<BlockScanDefinition.Frame>
    fun getValues(be: BlockEntity): Map<String, Any?>
}
