package com.cobblemon.mod.common.block

import net.minecraft.core.BlockPos
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.loot.LootContext
import net.minecraft.world.level.storage.loot.LootParams

class PotionBlock(
    private val dropItem: Item,
    properties: BlockBehaviour.Properties
) : Block(properties) {
    override fun getCloneItemStack(level: LevelReader, pos: BlockPos, state: BlockState): ItemStack? {
        return ItemStack(dropItem)
    }

    override fun getDrops(state: BlockState, params: LootParams.Builder): List<ItemStack?>? {
        return listOf(ItemStack(dropItem))
    }
}
