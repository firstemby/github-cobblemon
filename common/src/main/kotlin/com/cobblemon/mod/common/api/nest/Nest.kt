package com.cobblemon.mod.common.api.nest

import com.cobblemon.mod.common.CobblemonBlocks
import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.api.apricorn.Apricorn
import com.cobblemon.mod.common.api.tags.CobblemonBiomeTags
import com.cobblemon.mod.common.block.NestBlock
import com.cobblemon.mod.common.util.cobblemonResource
import com.mojang.serialization.Codec
import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.BiomeTags
import net.minecraft.tags.TagKey
import net.minecraft.util.StringRepresentable
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import kotlin.jvm.optionals.getOrNull


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
    CHORUS;
    //HAY;

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
        //HAY -> CobblemonItems.NEST_HAY
    }

    // todo make a map of ideal placement blocks for each nest
    fun placementBlocks(): List<Block> = when (this) {
        STICK -> listOf(Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.PODZOL, Blocks.COARSE_DIRT)
        LEAF -> listOf(Blocks.OAK_LEAVES, Blocks.JUNGLE_LEAVES, Blocks.DARK_OAK_LEAVES, Blocks.BIRCH_LEAVES, Blocks.ACACIA_LEAVES, Blocks.AZALEA_LEAVES, Blocks.MANGROVE_LEAVES, Blocks.CHERRY_LEAVES)
        STONE -> listOf(Blocks.STONE, Blocks.ANDESITE, Blocks.DIORITE, Blocks.GRANITE, Blocks.DRIPSTONE_BLOCK, Blocks.COBBLESTONE, Blocks.ICE, Blocks.PACKED_ICE, Blocks.BLUE_ICE)
        DEEPSLATE -> listOf(Blocks.DEEPSLATE, Blocks.COBBLED_DEEPSLATE)
        SNOW -> listOf(Blocks.SNOW_BLOCK, Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.COARSE_DIRT) // todo dirt, grass, and coarse dirt ONLY if replacing the location of snowlayer
        LUSH -> listOf(Blocks.MOSS_BLOCK, Blocks.CLAY)
        SEAGRASS -> listOf(Blocks.DIRT, Blocks.STONE, Blocks.ANDESITE, Blocks.DIORITE, Blocks.MUD, Blocks.CLAY, Blocks.ANDESITE, Blocks.SAND, Blocks.GRAVEL) // todo (all only if surrounded by water AND not in any of: Warm Ocean, Lukewarm Ocean, or Deep Lukewarm Ocean biomes)
        WARM_OCEAN -> listOf(Blocks.SAND, Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.FIRE_CORAL_BLOCK, Blocks.HORN_CORAL_BLOCK, Blocks.TUBE_CORAL_BLOCK, Blocks.BRAIN_CORAL_BLOCK, Blocks.BRAIN_CORAL_BLOCK, Blocks.BUBBLE_CORAL_BLOCK) // todo  (all only if surrounded by water AND only when in any of: Warm Ocean, Lukewarm Ocean, or Deep Lukewarm Ocean biomes)
        SAND -> listOf(Blocks.SAND, Blocks.SANDSTONE, Blocks.SMOOTH_SANDSTONE) // todo (only if NOT surrounded by water)
        RED_SAND -> listOf(Blocks.RED_SAND, Blocks.RED_SANDSTONE, Blocks.SMOOTH_RED_SANDSTONE)
        NETHER -> listOf(Blocks.NETHERRACK, Blocks.WARPED_NYLIUM, Blocks.CRIMSON_NYLIUM, Blocks.NETHER_WART_BLOCK, Blocks.WARPED_WART_BLOCK, Blocks.MAGMA_BLOCK)
        BASALT -> listOf(Blocks.BASALT, Blocks.BLACKSTONE, Blocks.SMOOTH_BASALT)
        SOUL_SAND -> listOf(Blocks.SOUL_SAND, Blocks.SOUL_SOIL)
        CHORUS -> listOf(Blocks.END_STONE, Blocks.PURPUR_BLOCK, Blocks.END_STONE_BRICKS)
        //HAY -> listOf(Blocks.HAY_BLOCK, Blocks.GRASS_BLOCK)
    }

    // todo make a map or something for each nest that can be waterlogged and not
    /**
     * Determines if a nest can be waterlogged.
     * Some nests, like those made of water-related materials, can be placed in water.
     */
    fun isWaterloggable(): Boolean = when (this) {
        SEAGRASS, WARM_OCEAN -> true
        else -> false
    }

    /**
     * Determines if this nest type can be placed based on **biome tags**.
     */
    fun isValidBiome(world: ServerLevel, pos: BlockPos): Boolean {
        val biomeHolder: Holder<Biome> = world.getBiome(pos)

        return when (this) {
            STICK -> biomeHolder.`is`(CobblemonBiomeTags.HAS_STICK_NEST)
            LEAF -> biomeHolder.`is`(CobblemonBiomeTags.HAS_LEAF_NEST_TREES)
            STONE -> biomeHolder.`is`(CobblemonBiomeTags.HAS_STONE_NEST)
            DEEPSLATE -> biomeHolder.`is`(CobblemonBiomeTags.HAS_DEEPSLATE_NEST_CAVES)
            SNOW -> biomeHolder.`is`(CobblemonBiomeTags.HAS_SNOW_NEST)
            LUSH -> biomeHolder.`is`(CobblemonBiomeTags.HAS_LUSH_NEST_CAVES)
            SEAGRASS -> biomeHolder.`is`(CobblemonBiomeTags.HAS_SEAGRASS_NEST)
            WARM_OCEAN -> biomeHolder.`is`(CobblemonBiomeTags.HAS_WARM_OCEAN_NEST)
            SAND -> biomeHolder.`is`(CobblemonBiomeTags.HAS_SAND_NEST)
            RED_SAND -> biomeHolder.`is`(CobblemonBiomeTags.HAS_RED_SAND_NEST)
            NETHER -> biomeHolder.`is`(CobblemonBiomeTags.HAS_NETHER_NEST)
            BASALT -> biomeHolder.`is`(CobblemonBiomeTags.HAS_BASALT_NEST)
            SOUL_SAND -> biomeHolder.`is`(CobblemonBiomeTags.HAS_SOUL_SAND_NEST)
            CHORUS -> biomeHolder.`is`(CobblemonBiomeTags.HAS_CHORUS_NEST)
        }
    }


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
        //HAY -> Items.WHEAT
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
            Items.STICK to 2,
            Items.OAK_SAPLING to 2,
            Items.COBBLESTONE to 2,
            Items.COBBLED_DEEPSLATE to 2,
            Items.SNOWBALL to 2,
            Items.MOSS_BLOCK to 2,
            Items.KELP to 2,
            Items.SEA_PICKLE to 2,
            Items.SAND to 2,
            Items.RED_SAND to 2,
            Items.WARPED_ROOTS to 2,
            Items.BASALT to 2,
            Items.SOUL_SAND to 2,
            Items.CHORUS_FRUIT to 2,
            //Items.WHEAT to 2
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