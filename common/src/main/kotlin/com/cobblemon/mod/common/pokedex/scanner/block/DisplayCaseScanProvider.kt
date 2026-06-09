package com.cobblemon.mod.common.pokedex.scanner.block

import com.cobblemon.mod.common.api.pokedex.scan.BlockScanDefinition
import com.cobblemon.mod.common.api.pokedex.scan.ScannableDefinitions
import com.cobblemon.mod.common.api.pokedex.scan.ScannedDataCategory
import com.cobblemon.mod.common.api.text.bold
import com.cobblemon.mod.common.block.entity.DisplayCaseBlockEntity
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.entity.BlockEntity

/**
 * Makes a Display Case scannable based on the item it holds: it is only a scan target when the
 * contained item is on the universal scannable allow-list (some Items sub-category). Scanning it
 * records that item's id, so the case is the in-world way to "scan" items that can't show an icon
 * as floating entities.
 */
object DisplayCaseScanProvider : BlockScanProvider {
    override val category = ScannedDataCategory.ITEMS

    private fun bucketFor(be: BlockEntity): String? {
        val stack = (be as? DisplayCaseBlockEntity)?.getStack() ?: return null
        if (stack.isEmpty) return null
        return ScannableDefinitions.itemBucket(stack.item)
    }

    override fun isScannable(be: BlockEntity): Boolean = bucketFor(be) != null

    override fun subCategory(be: BlockEntity): String? =
        bucketFor(be)?.let { ScannedDataCategory.subOfBucket(it) }

    override fun getScanKey(be: BlockEntity): ResourceLocation {
        val stack = (be as DisplayCaseBlockEntity).getStack()
        return BuiltInRegistries.ITEM.getKey(stack.item)
    }

    override val frames = listOf(
        BlockScanDefinition.Frame(slot = 1, text = BlockScanDefinition.ScanText.Key("item_name"))
    )

    override fun getValues(be: BlockEntity): Map<String, Any?> {
        val stack = (be as DisplayCaseBlockEntity).getStack()
        return mapOf("item_name" to stack.hoverName.copy().bold())
    }
}
