package com.cobblemon.mod.common.api.nest

import com.cobblemon.mod.common.CobblemonBlocks
import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.api.apricorn.Apricorn
import com.cobblemon.mod.common.block.NestBlock
import com.mojang.serialization.Codec
import net.minecraft.util.StringRepresentable
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items


/**
 * Contains the different Nest variants and util methods to get their item, block, biome, and materials from.
 *
 * @author Plastered_Crab
 * @since Feb 7th, 2025
 */
enum class Nest : StringRepresentable {

    STICK,
    LEAF,
    STONE,
    DEEPSLATE,
    SNOW,
    LUSH,
    SEAGRASS,
    WARM_OCEAN,
    SAND,
    RED_SAND,
    NETHER,
    BASALT,
    SOUL_SAND,
    CHORUS,
    HAY;

    // Crab Note: todo maybe we want these to be a List of BlockItems?
    fun item(): BlockItem = when(this) {
        STICK -> CobblemonItems.NEST_STICK
        LEAF -> CobblemonItems.NEST_LEAF
        STONE -> CobblemonItems.NEST_STONE
        DEEPSLATE -> CobblemonItems.NEST_DEEPSLATE
        SNOW -> CobblemonItems.NEST_SNOW
        LUSH -> CobblemonItems.NEST_LUSH
        SEAGRASS -> CobblemonItems.NEST_SEAGRASS
        WARM_OCEAN -> CobblemonItems.NEST_WARM_OCEAN
        SAND -> CobblemonItems.NEST_SAND
        RED_SAND -> CobblemonItems.NEST_RED_SAND
        NETHER -> CobblemonItems.NEST_NETHER
        BASALT -> CobblemonItems.NEST_BASALT
        SOUL_SAND -> CobblemonItems.NEST_SOUL_SAND
        CHORUS -> CobblemonItems.NEST_CHORUS
        HAY -> CobblemonItems.NEST_HAY
    }

    // todo make a map of ideal placement blocks for each nest

    // todo make a map or something for each nest that can be waterlogged and not

    // Crab Note: todo maybe we want these to be a List of Items?
    fun materials(): Item = when(this) {
        STICK -> Items.STICK
        LEAF -> Items.OAK_SAPLING
        STONE -> Items.COBBLESTONE
        DEEPSLATE -> Items.COBBLED_DEEPSLATE
        SNOW -> Items.SNOWBALL
        LUSH -> Items.MOSS_BLOCK
        SEAGRASS -> Items.KELP
        WARM_OCEAN -> Items.SEA_PICKLE
        SAND -> Items.SAND
        RED_SAND -> Items.RED_SAND
        NETHER -> Items.WARPED_ROOTS
        BASALT -> Items.BASALT
        SOUL_SAND -> Items.SOUL_SAND
        CHORUS -> Items.CHORUS_FRUIT
        HAY -> Items.WHEAT
    }

    // Crab Note: todo maybe we want to redo this if we have lists of items needed for each?
    /*fun craftedNestResult(itemStack: ItemStack): BlockItem? = when(itemStack) {
        ItemStack(Items.STICK, 4) -> CobblemonItems.NEST_STICK
        ItemStack(Items.OAK_SAPLING, 4) -> CobblemonItems.NEST_LEAF
        ItemStack(Items.COBBLESTONE, 4) -> CobblemonItems.NEST_STONE
        ItemStack(Items.COBBLED_DEEPSLATE, 4) -> CobblemonItems.NEST_DEEPSLATE
        ItemStack(Items.SNOWBALL, 4) -> CobblemonItems.NEST_SNOW
        ItemStack(Items.MOSS_BLOCK, 4) -> CobblemonItems.NEST_LUSH
        ItemStack(Items.KELP, 4) -> CobblemonItems.NEST_SEAGRASS
        ItemStack(Items.SEA_PICKLE, 4) -> CobblemonItems.NEST_WARM_OCEAN
        ItemStack(Items.SAND, 4) -> CobblemonItems.NEST_SAND
        ItemStack(Items.RED_SAND, 4) -> CobblemonItems.NEST_RED_SAND
        ItemStack(Items.WARPED_ROOTS, 4) -> CobblemonItems.NEST_NETHER
        ItemStack(Items.BASALT, 4) -> CobblemonItems.NEST_BASALT
        ItemStack(Items.SOUL_SAND, 4) -> CobblemonItems.NEST_SOUL_SAND
        ItemStack(Items.CHORUS_FRUIT, 4) -> CobblemonItems.NEST_CHORUS
        ItemStack(Items.WHEAT, 4) -> CobblemonItems.NEST_HAY
        else -> null
    }*/

    override fun getSerializedName() = this.name.lowercase()

    companion object {
        val CODEC: Codec<Nest> = StringRepresentable.fromValues(::values)

        private val materialRequirements: Map<Item, Int> = mapOf(
            Items.STICK to 4,
            Items.OAK_SAPLING to 4,
            Items.COBBLESTONE to 4,
            Items.COBBLED_DEEPSLATE to 4,
            Items.SNOWBALL to 4,
            Items.MOSS_BLOCK to 4,
            Items.KELP to 4,
            Items.SEA_PICKLE to 4,
            Items.SAND to 4,
            Items.RED_SAND to 4,
            Items.WARPED_ROOTS to 4,
            Items.BASALT to 4,
            Items.SOUL_SAND to 4,
            Items.CHORUS_FRUIT to 4,
            Items.WHEAT to 4
        )

        /**
         * Determines if a held stack is sufficient to craft a nest and returns the crafted item and leftover count.
         * @param heldStack The item stack in the Pokémon's hand.
         * @return A Pair of (crafted nest block item, remaining materials in hand) or null if crafting is not possible.
         */
        fun craftedNestResult(heldStack: ItemStack): Pair<BlockItem, Int>? {
            val requiredAmount = materialRequirements[heldStack.item] ?: return null // Check if item is a valid material

            // Ensure Pokémon has enough materials
            if (heldStack.count >= requiredAmount) {
                val remaining = heldStack.count - requiredAmount

                // Find the nest type that matches the material
                val matchingNest = entries.firstOrNull { it.materials() == heldStack.item } ?: return null

                return Pair(matchingNest.item(), remaining)
            }

            return null // Not enough materials
        }
    }
}