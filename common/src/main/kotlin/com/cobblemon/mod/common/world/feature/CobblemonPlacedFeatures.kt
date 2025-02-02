/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.world.feature

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.tags.CobblemonBiomeTags
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.tags.BiomeTags
import net.minecraft.world.level.levelgen.GenerationStep
import net.minecraft.world.level.levelgen.feature.ChorusPlantFeature
import net.minecraft.world.level.levelgen.placement.PlacedFeature

object CobblemonPlacedFeatures {

    // TODO we don't need a placed feature for every colour, clean all this crap in the JSONs (ask Hiro)
    val BLACK_APRICORN_TREE_PLACED_FEATURE = of("black_apricorn_tree")
    val BLUE_APRICORN_TREE_PLACED_FEATURE = of("blue_apricorn_tree")
    val GREEN_APRICORN_TREE_PLACED_FEATURE = of("green_apricorn_tree")
    val PINK_APRICORN_TREE_PLACED_FEATURE = of("pink_apricorn_tree")
    val RED_APRICORN_TREE_PLACED_FEATURE = of("red_apricorn_tree")
    val WHITE_APRICORN_TREE_PLACED_FEATURE = of("white_apricorn_tree")
    val YELLOW_APRICORN_TREE_PLACED_FEATURE = of("yellow_apricorn_tree")


    val APRICORN_TREES = of("apricorn_trees")

    val MINTS = of("mints")

    val MEDICINAL_LEEK = of("medicinal_leek")
    val BIG_ROOT = of("big_root")

    val REVIVAL_HERB = of("revival_herb")

    val BERRY_GROVE = of("berry_groves")

    // Wild Nests
    val NEST_STICK = of ("nests/stick_nest")
    val NEST_STICK_TREES = of ("nests/stick_nest_trees")
    val NEST_LEAF_TREES = of ("nests/leaf_nest_trees")
    val NEST_SNOW = of ("nests/snow_nest")
    val NEST_SAND = of ("nests/sand_nest")
    val NEST_RED_SAND = of ("nests/red_sand_nest")
    val NEST_SEAGRASS = of ("nests/seagrass_nest")
    val NEST_WARM_OCEAN = of ("nests/warm_ocean_nest")
    val NEST_STONE = of ("nests/stone_nest")
    val NEST_STONE_CAVES = of ("nests/stone_nest_caves")
    val NEST_DEEPSLATE_CAVES = of ("nests/deepslate_nest_caves")
    val NEST_LUSH_CAVES = of ("nests/lush_nest_caves")
    val NEST_BASALT = of ("nests/basalt_nest")
    val NEST_NETHER = of ("nests/nether_nest")
    val NEST_SOUL_SAND = of ("nests/soul_sand_nest")
    val NEST_CHORUS = of ("nests/chorus_nest")

    fun register() {
        // We don't need to pass in any tags, the feature implementation handles it, while not a perfect system it works
        Cobblemon.implementation.addFeatureToWorldGen(APRICORN_TREES, GenerationStep.Decoration.VEGETAL_DECORATION, null)
        Cobblemon.implementation.addFeatureToWorldGen(MINTS, GenerationStep.Decoration.VEGETAL_DECORATION, null)
        Cobblemon.implementation.addFeatureToWorldGen(MEDICINAL_LEEK, GenerationStep.Decoration.VEGETAL_DECORATION, null)
        Cobblemon.implementation.addFeatureToWorldGen(BIG_ROOT, GenerationStep.Decoration.VEGETAL_DECORATION, BiomeTags.IS_OVERWORLD)
        Cobblemon.implementation.addFeatureToWorldGen(REVIVAL_HERB, GenerationStep.Decoration.VEGETAL_DECORATION, CobblemonBiomeTags.HAS_REVIVAL_HERBS)
        Cobblemon.implementation.addFeatureToWorldGen(BERRY_GROVE, GenerationStep.Decoration.VEGETAL_DECORATION, BiomeTags.IS_OVERWORLD)
        // Nests
        Cobblemon.implementation.addFeatureToWorldGen(NEST_STICK, GenerationStep.Decoration.VEGETAL_DECORATION, CobblemonBiomeTags.HAS_STICK_NEST)
        Cobblemon.implementation.addFeatureToWorldGen(NEST_STICK_TREES, GenerationStep.Decoration.VEGETAL_DECORATION, CobblemonBiomeTags.HAS_STICK_NEST_TREES)
        Cobblemon.implementation.addFeatureToWorldGen(NEST_LEAF_TREES, GenerationStep.Decoration.VEGETAL_DECORATION, CobblemonBiomeTags.HAS_LEAF_NEST_TREES)
        Cobblemon.implementation.addFeatureToWorldGen(NEST_SNOW, GenerationStep.Decoration.VEGETAL_DECORATION, CobblemonBiomeTags.HAS_SNOW_NEST)
        Cobblemon.implementation.addFeatureToWorldGen(NEST_SAND, GenerationStep.Decoration.VEGETAL_DECORATION, CobblemonBiomeTags.HAS_SAND_NEST)
        Cobblemon.implementation.addFeatureToWorldGen(NEST_RED_SAND, GenerationStep.Decoration.VEGETAL_DECORATION, CobblemonBiomeTags.HAS_RED_SAND_NEST)
        Cobblemon.implementation.addFeatureToWorldGen(NEST_SEAGRASS, GenerationStep.Decoration.VEGETAL_DECORATION, CobblemonBiomeTags.HAS_SEAGRASS_NEST)
        Cobblemon.implementation.addFeatureToWorldGen(NEST_WARM_OCEAN, GenerationStep.Decoration.VEGETAL_DECORATION, CobblemonBiomeTags.HAS_WARM_OCEAN_NEST)
        Cobblemon.implementation.addFeatureToWorldGen(NEST_STONE, GenerationStep.Decoration.VEGETAL_DECORATION, CobblemonBiomeTags.HAS_STONE_NEST)
        Cobblemon.implementation.addFeatureToWorldGen(NEST_STONE_CAVES, GenerationStep.Decoration.VEGETAL_DECORATION, CobblemonBiomeTags.HAS_STONE_NEST_CAVES)
        Cobblemon.implementation.addFeatureToWorldGen(NEST_DEEPSLATE_CAVES, GenerationStep.Decoration.VEGETAL_DECORATION, CobblemonBiomeTags.HAS_DEEPSLATE_NEST_CAVES)
        Cobblemon.implementation.addFeatureToWorldGen(NEST_LUSH_CAVES, GenerationStep.Decoration.VEGETAL_DECORATION, CobblemonBiomeTags.HAS_LUSH_NEST_CAVES)
        Cobblemon.implementation.addFeatureToWorldGen(NEST_BASALT, GenerationStep.Decoration.VEGETAL_DECORATION, CobblemonBiomeTags.HAS_BASALT_NEST)
        Cobblemon.implementation.addFeatureToWorldGen(NEST_NETHER, GenerationStep.Decoration.VEGETAL_DECORATION, CobblemonBiomeTags.HAS_NETHER_NEST)
        Cobblemon.implementation.addFeatureToWorldGen(NEST_SOUL_SAND, GenerationStep.Decoration.VEGETAL_DECORATION, CobblemonBiomeTags.HAS_SOUL_SAND_NEST)
        Cobblemon.implementation.addFeatureToWorldGen(NEST_CHORUS, GenerationStep.Decoration.VEGETAL_DECORATION, CobblemonBiomeTags.HAS_CHORUS_NEST)

        // todo add more nests and make sure they are in the right dimensions
    }

    private fun of(id: String): ResourceKey<PlacedFeature> = ResourceKey.create(Registries.PLACED_FEATURE, cobblemonResource(id))
}
