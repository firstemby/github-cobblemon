package com.cobblemon.mod.common.pokedex.scanner.block

import com.cobblemon.mod.common.api.pokedex.scan.BlockScanDefinition
import com.cobblemon.mod.common.api.pokedex.scan.ScannedDataCategory
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.entity.BlockEntity

interface BlockScanProvider {
    /** Top-level scanned-data category a successful scan of this block is recorded into. */
    val category: ScannedDataCategory
    /** Whether this specific block-entity instance is currently a valid scan target. */
    fun isScannable(be: BlockEntity): Boolean = true
    /** Optional sub-category (e.g. for items: "pokeballs"); may depend on the block's contents. */
    fun subCategory(be: BlockEntity): String? = null
    /** The bucket path this scan records into, derived from [category] + [subCategory]. */
    fun bucket(be: BlockEntity): String = category.bucket(subCategory(be))
    val frames: List<BlockScanDefinition.Frame>
    fun getScanKey(be: BlockEntity): ResourceLocation
    fun getValues(be: BlockEntity): Map<String, Any?>
    fun onScanComplete(pos: BlockPos, zoomLevel: Int) {}
}
