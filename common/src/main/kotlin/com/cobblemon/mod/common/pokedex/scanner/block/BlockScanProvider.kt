package com.cobblemon.mod.common.pokedex.scanner.block

import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.block.BerryBlock
import com.cobblemon.mod.common.block.entity.BerryBlockEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity

interface BlockScanProvider {
    /** Return the values for keys used in the JSONs we create for each scannable Block Entity */
    fun getValues(be: BlockEntity): Map<String, Any?>
}

