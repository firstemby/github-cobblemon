/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.fabric.data.generator.providers

import com.cobblemon.mod.common.CobblemonBlocks
import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.block.TypeGemClusterBlock
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput
import net.fabricmc.fabric.api.datagen.v1.provider.FabricBlockLootTableProvider
import net.minecraft.advancements.critereon.*
import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.Registries
import net.minecraft.world.item.Item
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.storage.loot.IntRange
import net.minecraft.world.level.storage.loot.LootPool
import net.minecraft.world.level.storage.loot.LootTable
import net.minecraft.world.level.storage.loot.entries.AlternativesEntry
import net.minecraft.world.level.storage.loot.entries.LootItem
import net.minecraft.world.level.storage.loot.functions.ApplyBonusCount
import net.minecraft.world.level.storage.loot.functions.ApplyExplosionDecay
import net.minecraft.world.level.storage.loot.functions.LimitCount
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction
import net.minecraft.world.level.storage.loot.predicates.LootItemBlockStatePropertyCondition
import net.minecraft.world.level.storage.loot.predicates.MatchTool
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator
import java.util.concurrent.CompletableFuture

class TypeGemsLootTableProvider(
    dataOutput: FabricDataOutput,
    val registryLookup: CompletableFuture<HolderLookup.Provider>
) : FabricBlockLootTableProvider(dataOutput, registryLookup) {

    override fun getName(): String? {
        return "Type Gems Loot Tables"
    }

    override fun generate() {
        val lookup = registryLookup.join()

        val enchantments = lookup.lookupOrThrow(Registries.ENCHANTMENT)
        val silkTouch = enchantments.getOrThrow(Enchantments.SILK_TOUCH)
        val fortune = enchantments.getOrThrow(Enchantments.FORTUNE)

        CobblemonBlocks.typeGemBlocks().values.forEach { block ->
            val gem = BLOCK_TO_GEM[block] ?: return@forEach

            add(
                block,
                LootTable.lootTable().withPool(
                    LootPool.lootPool().add(
                        AlternativesEntry.alternatives(
                            LootItem.lootTableItem(block).`when`(
                                MatchTool.toolMatches(
                                    ItemPredicate.Builder.item()
                                        .withSubPredicate(
                                            ItemSubPredicates.ENCHANTMENTS,
                                            ItemEnchantmentsPredicate.enchantments(
                                                listOf(
                                                    EnchantmentPredicate(
                                                        silkTouch,
                                                        MinMaxBounds.Ints.atLeast(1)
                                                    )
                                                )
                                            ),
                                        )
                                )
                            ),
                            LootItem.lootTableItem(gem)
                                .apply(
                                    SetItemCountFunction.setCount(
                                        UniformGenerator.between(1f, 2f)
                                    )
                                )
                                .apply(
                                    ApplyBonusCount.addOreBonusCount(fortune)
                                )
                                .apply(
                                    LimitCount.limitCount(IntRange.upperBound(4))
                                )
                                .apply(
                                    ApplyExplosionDecay.explosionDecay()
                                )
                        )
                    )
                )
            )
        }

        CobblemonBlocks.typeGemClusters().values.forEach { cluster ->
            val gem = CLUSTER_TO_GEM[cluster] ?: return@forEach

            add(
                cluster,
                LootTable.lootTable().withPool(
                    LootPool.lootPool().add(
                        AlternativesEntry.alternatives(
                            LootItem.lootTableItem(cluster).`when`(
                                MatchTool.toolMatches(
                                    ItemPredicate.Builder.item()
                                        .withSubPredicate(
                                            ItemSubPredicates.ENCHANTMENTS,
                                            ItemEnchantmentsPredicate.enchantments(
                                                listOf(
                                                    EnchantmentPredicate(
                                                        silkTouch,
                                                        MinMaxBounds.Ints.atLeast(1)
                                                    )
                                                )
                                            ),
                                        )
                                )
                            ),
                            LootItem.lootTableItem(gem).`when`(
                                LootItemBlockStatePropertyCondition
                                    .hasBlockStateProperties(cluster)
                                    .setProperties(
                                        StatePropertiesPredicate.Builder.properties()
                                            .hasProperty(TypeGemClusterBlock.STAGE, 3)
                                    )
                            )
                                .apply(
                                    SetItemCountFunction.setCount(
                                        UniformGenerator.between(2f, 3f)
                                    )
                                )
                                .apply(
                                    ApplyBonusCount.addOreBonusCount(fortune)
                                )
                                .apply(
                                    LimitCount.limitCount(IntRange.upperBound(4))
                                )
                                .apply(
                                    ApplyExplosionDecay.explosionDecay()
                                ),
                            LootItem.lootTableItem(gem)
                                .apply(
                                    SetItemCountFunction.setCount(
                                        ConstantValue.exactly(1f)
                                    )
                                )
                                .apply(
                                    ApplyExplosionDecay.explosionDecay()
                                )
                        )
                    )
                )
            )
        }
    }

    companion object {
        val BLOCK_TO_GEM: Map<Block, Item> by lazy {
            mapOf(
                CobblemonBlocks.TYPE_GEM_BLOCK_NORMAL to CobblemonItems.NORMAL_GEM,
                CobblemonBlocks.TYPE_GEM_BLOCK_FIRE to CobblemonItems.FIRE_GEM,
                CobblemonBlocks.TYPE_GEM_BLOCK_WATER to CobblemonItems.WATER_GEM,
                CobblemonBlocks.TYPE_GEM_BLOCK_GRASS to CobblemonItems.GRASS_GEM,
                CobblemonBlocks.TYPE_GEM_BLOCK_ELECTRIC to CobblemonItems.ELECTRIC_GEM,
                CobblemonBlocks.TYPE_GEM_BLOCK_ICE to CobblemonItems.ICE_GEM,
                CobblemonBlocks.TYPE_GEM_BLOCK_FIGHTING to CobblemonItems.FIGHTING_GEM,
                CobblemonBlocks.TYPE_GEM_BLOCK_POISON to CobblemonItems.POISON_GEM,
                CobblemonBlocks.TYPE_GEM_BLOCK_GROUND to CobblemonItems.GROUND_GEM,
                CobblemonBlocks.TYPE_GEM_BLOCK_FLYING to CobblemonItems.FLYING_GEM,
                CobblemonBlocks.TYPE_GEM_BLOCK_PSYCHIC to CobblemonItems.PSYCHIC_GEM,
                CobblemonBlocks.TYPE_GEM_BLOCK_BUG to CobblemonItems.BUG_GEM,
                CobblemonBlocks.TYPE_GEM_BLOCK_ROCK to CobblemonItems.ROCK_GEM,
                CobblemonBlocks.TYPE_GEM_BLOCK_GHOST to CobblemonItems.GHOST_GEM,
                CobblemonBlocks.TYPE_GEM_BLOCK_DRAGON to CobblemonItems.DRAGON_GEM,
                CobblemonBlocks.TYPE_GEM_BLOCK_DARK to CobblemonItems.DARK_GEM,
                CobblemonBlocks.TYPE_GEM_BLOCK_STEEL to CobblemonItems.STEEL_GEM,
                CobblemonBlocks.TYPE_GEM_BLOCK_FAIRY to CobblemonItems.FAIRY_GEM
            )
        }

        val CLUSTER_TO_GEM: Map<Block, Item> by lazy {
            mapOf(
                CobblemonBlocks.TYPE_GEM_CLUSTER_NORMAL to CobblemonItems.NORMAL_GEM,
                CobblemonBlocks.TYPE_GEM_CLUSTER_FIRE to CobblemonItems.FIRE_GEM,
                CobblemonBlocks.TYPE_GEM_CLUSTER_WATER to CobblemonItems.WATER_GEM,
                CobblemonBlocks.TYPE_GEM_CLUSTER_GRASS to CobblemonItems.GRASS_GEM,
                CobblemonBlocks.TYPE_GEM_CLUSTER_ELECTRIC to CobblemonItems.ELECTRIC_GEM,
                CobblemonBlocks.TYPE_GEM_CLUSTER_ICE to CobblemonItems.ICE_GEM,
                CobblemonBlocks.TYPE_GEM_CLUSTER_FIGHTING to CobblemonItems.FIGHTING_GEM,
                CobblemonBlocks.TYPE_GEM_CLUSTER_POISON to CobblemonItems.POISON_GEM,
                CobblemonBlocks.TYPE_GEM_CLUSTER_GROUND to CobblemonItems.GROUND_GEM,
                CobblemonBlocks.TYPE_GEM_CLUSTER_FLYING to CobblemonItems.FLYING_GEM,
                CobblemonBlocks.TYPE_GEM_CLUSTER_PSYCHIC to CobblemonItems.PSYCHIC_GEM,
                CobblemonBlocks.TYPE_GEM_CLUSTER_BUG to CobblemonItems.BUG_GEM,
                CobblemonBlocks.TYPE_GEM_CLUSTER_ROCK to CobblemonItems.ROCK_GEM,
                CobblemonBlocks.TYPE_GEM_CLUSTER_GHOST to CobblemonItems.GHOST_GEM,
                CobblemonBlocks.TYPE_GEM_CLUSTER_DRAGON to CobblemonItems.DRAGON_GEM,
                CobblemonBlocks.TYPE_GEM_CLUSTER_DARK to CobblemonItems.DARK_GEM,
                CobblemonBlocks.TYPE_GEM_CLUSTER_STEEL to CobblemonItems.STEEL_GEM,
                CobblemonBlocks.TYPE_GEM_CLUSTER_FAIRY to CobblemonItems.FAIRY_GEM
            )
        }
    }

}