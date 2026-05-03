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
import net.minecraft.world.level.levelgen.placement.PlacedFeature

object CobblemonPlacedFeatures {

    // TODO we don't need a placed feature for every colour, clean all this crap in the JSONs (ask Hiro)
    @JvmField
    val BLACK_APRICORN_TREE_PLACED_FEATURE = of("black_apricorn_tree")
    @JvmField
    val BLUE_APRICORN_TREE_PLACED_FEATURE = of("blue_apricorn_tree")
    @JvmField
    val GREEN_APRICORN_TREE_PLACED_FEATURE = of("green_apricorn_tree")
    @JvmField
    val PINK_APRICORN_TREE_PLACED_FEATURE = of("pink_apricorn_tree")
    @JvmField
    val RED_APRICORN_TREE_PLACED_FEATURE = of("red_apricorn_tree")
    @JvmField
    val WHITE_APRICORN_TREE_PLACED_FEATURE = of("white_apricorn_tree")
    @JvmField
    val YELLOW_APRICORN_TREE_PLACED_FEATURE = of("yellow_apricorn_tree")

    @JvmField
    val APRICORN_TREES = of("apricorn_trees")
    @JvmField
    val SACCHARINE_TREE = of("saccharine_tree")

    @JvmField
    val MINTS = of("mints")

    @JvmField
    val MEDICINAL_LEEK = of("medicinal_leek")
    @JvmField
    val BIG_ROOT = of("big_root")

    @JvmField
    val REVIVAL_HERB = of("revival_herb")

    @JvmField
    val BERRY_GROVE = of("berry_groves")

    @JvmField
    val TYPE_GEM = of("type_gems")

    @JvmField
    val SWAMP_GRAINS = of("swamp_grains")
    @JvmField
    val PLAINS_GRAINS = of("plains_grains")

    @JvmField
    val GALARICA_NUTS = of("galarica_nuts")

    @JvmField
    val TREASURE_HOARD = of("habitats/treasure_hoard")
    @JvmField
    val ABANDONED_FORTRESS = of("habitats/abandoned_fortress")
    @JvmField
    val ANCIENT_WELLSPRINGS = of("habitats/ancient_wellsprings")
    @JvmField
    val CHORUS_BRIARS = of("habitats/chorus_briars")
    @JvmField
    val DEEP_ROOTS = of("habitats/deep_roots")
    @JvmField
    val EARTHEN_HIVES = of("habitats/earthen_hives")
    @JvmField
    val EXPOSED_GEODES = of("habitats/exposed_geodes")
    @JvmField
    val LOST_RUINS = of("habitats/lost_ruins")
    @JvmField
    val QUARTZ_SPIKES = of("habitats/quartz_spikes")
    @JvmField
    val ROCKY_TIDEPOOLS = of("habitats/rocky_tidepools")
    @JvmField
    val ROOT_NURSERIES = of("habitats/root_nurseries")
    @JvmField
    val SANDY_TIDEPOOLS = of("habitats/sandy_tidepools")
    @JvmField
    val SKELETAL_FALL = of("habitats/skeletal_fall")
    @JvmField
    val STRANGE_FUNGAL_DWELLINGS = of("habitats/strange_fungal_dwellings")
    @JvmField
    val VOLCANIC_PLUMES = of("habitats/volcanic_plumes")

    @JvmField
    val PREHISTORIC_BIRCH_TREE = of("fossils/prehistoric_birch_tree")
    @JvmField
    val PREHISTORIC_DRIPSTONE_OASIS = of("fossils/prehistoric_dripstone_oasis")
    @JvmField
    val PREHISTORIC_ENHYDRO_AGATE = of("fossils/prehistoric_enhydro_agate")
    @JvmField
    val PREHISTORIC_ERODED_PILLAR = of("fossils/prehistoric_eroded_pillar")
    @JvmField
    val PREHISTORIC_FROZEN_POND = of("fossils/prehistoric_frozen_pond")
    @JvmField
    val PREHISTORIC_FROZEN_SPIKE = of("fossils/prehistoric_frozen_spike")
    @JvmField
    val PREHISTORIC_HYDROTHERMAL_VENTS = of("fossils/prehistoric_hydrothermal_vents")
    @JvmField
    val PREHISTORIC_LUSH_DEN = of("fossils/prehistoric_lush_den")
    @JvmField
    val PREHISTORIC_MOSSY_POND = of("fossils/prehistoric_mossy_pond")
    @JvmField
    val PREHISTORIC_MUD_PIT = of("fossils/prehistoric_mud_pit")
    @JvmField
    val PREHISTORIC_OAK_TREE = of("fossils/prehistoric_oak_tree")
    @JvmField
    val PREHISTORIC_POWDERED_DEPOSIT = of("fossils/prehistoric_powdered_deposit")
    @JvmField
    val PREHISTORIC_PRESERVED_SKELETON = of("fossils/prehistoric_preserved_skeleton")
    @JvmField
    val PREHISTORIC_ROOTED_PITS = of("fossils/prehistoric_rooted_pits")
    @JvmField
    val PREHISTORIC_SANDY_DEN = of("fossils/prehistoric_sandy_den")
    @JvmField
    val PREHISTORIC_SPRUCE_TREE = of("fossils/prehistoric_spruce_tree")
    @JvmField
    val PREHISTORIC_SUBMERGED_IMPACT = of("fossils/prehistoric_submerged_impact")
    @JvmField
    val PREHISTORIC_SUBMERGED_SPIKE = of("fossils/prehistoric_submerged_spike")
    @JvmField
    val PREHISTORIC_SUNSCORCHED_DEN = of("fossils/prehistoric_sunscorched_den")
    @JvmField
    val PREHISTORIC_SUNSCORCHED_REMAINS = of("fossils/prehistoric_sunscorched_remains")
    @JvmField
    val PREHISTORIC_SUSPICIOUS_MOUNDS = of("fossils/prehistoric_suspicious_mounds")
    @JvmField
    val PREHISTORIC_UNDERWATER_FISSURE = of("fossils/prehistoric_underwater_fissure")
    @JvmField
    val PREHISTORIC_VIBRANT_HYDROTHERMAL_VENTS = of("fossils/prehistoric_vibrant_hydrothermal_vents")

    @JvmField
    val CRUMBLING_ARCH = of("ruins/crumbling_arch")
    @JvmField
    val DEEP_CRYPT = of("ruins/deep_crypt")
    @JvmField
    val ROOTED_ARCH = of("ruins/rooted_arch")

    fun register() {
        // We don't need to pass in any tags, the feature implementation handles it, while not a perfect system it works
        Cobblemon.implementation.addFeatureToWorldGen(APRICORN_TREES, GenerationStep.Decoration.VEGETAL_DECORATION, null)
        Cobblemon.implementation.addFeatureToWorldGen(SACCHARINE_TREE, GenerationStep.Decoration.VEGETAL_DECORATION, null)
        Cobblemon.implementation.addFeatureToWorldGen(MINTS, GenerationStep.Decoration.VEGETAL_DECORATION, null)
        Cobblemon.implementation.addFeatureToWorldGen(MEDICINAL_LEEK, GenerationStep.Decoration.VEGETAL_DECORATION, null)
        Cobblemon.implementation.addFeatureToWorldGen(BIG_ROOT, GenerationStep.Decoration.VEGETAL_DECORATION, BiomeTags.IS_OVERWORLD)
        Cobblemon.implementation.addFeatureToWorldGen(REVIVAL_HERB, GenerationStep.Decoration.VEGETAL_DECORATION, CobblemonBiomeTags.HAS_REVIVAL_HERBS)
        Cobblemon.implementation.addFeatureToWorldGen(BERRY_GROVE, GenerationStep.Decoration.VEGETAL_DECORATION, BiomeTags.IS_OVERWORLD)
        Cobblemon.implementation.addFeatureToWorldGen(TYPE_GEM, GenerationStep.Decoration.UNDERGROUND_ORES, BiomeTags.IS_OVERWORLD)
        Cobblemon.implementation.addFeatureToWorldGen(SWAMP_GRAINS, GenerationStep.Decoration.VEGETAL_DECORATION, CobblemonBiomeTags.IS_SWAMP)
        Cobblemon.implementation.addFeatureToWorldGen(PLAINS_GRAINS, GenerationStep.Decoration.VEGETAL_DECORATION, CobblemonBiomeTags.IS_PLAINS)
        Cobblemon.implementation.addFeatureToWorldGen(GALARICA_NUTS, GenerationStep.Decoration.VEGETAL_DECORATION, CobblemonBiomeTags.IS_BEACH)
        Cobblemon.implementation.addFeatureToWorldGen(TREASURE_HOARD, GenerationStep.Decoration.LOCAL_MODIFICATIONS, BiomeTags.IS_OVERWORLD)
        Cobblemon.implementation.addFeatureToWorldGen(ABANDONED_FORTRESS, GenerationStep.Decoration.UNDERGROUND_DECORATION, BiomeTags.IS_NETHER)
        Cobblemon.implementation.addFeatureToWorldGen(ANCIENT_WELLSPRINGS, GenerationStep.Decoration.LOCAL_MODIFICATIONS, CobblemonBiomeTags.IS_LUSH)
        Cobblemon.implementation.addFeatureToWorldGen(CHORUS_BRIARS, GenerationStep.Decoration.LOCAL_MODIFICATIONS, CobblemonBiomeTags.IS_END)
        Cobblemon.implementation.addFeatureToWorldGen(DEEP_ROOTS, GenerationStep.Decoration.LOCAL_MODIFICATIONS, CobblemonBiomeTags.IS_LUSH)
        Cobblemon.implementation.addFeatureToWorldGen(EARTHEN_HIVES, GenerationStep.Decoration.LOCAL_MODIFICATIONS, CobblemonBiomeTags.IS_DRIPSTONE)
        Cobblemon.implementation.addFeatureToWorldGen(EXPOSED_GEODES, GenerationStep.Decoration.LOCAL_MODIFICATIONS, BiomeTags.IS_OVERWORLD)
        Cobblemon.implementation.addFeatureToWorldGen(LOST_RUINS, GenerationStep.Decoration.UNDERGROUND_DECORATION, BiomeTags.IS_OVERWORLD)
        Cobblemon.implementation.addFeatureToWorldGen(QUARTZ_SPIKES, GenerationStep.Decoration.LOCAL_MODIFICATIONS, BiomeTags.IS_NETHER)
        Cobblemon.implementation.addFeatureToWorldGen(ROCKY_TIDEPOOLS, GenerationStep.Decoration.LOCAL_MODIFICATIONS, CobblemonBiomeTags.IS_STONY_BEACH)
        Cobblemon.implementation.addFeatureToWorldGen(ROOT_NURSERIES, GenerationStep.Decoration.LOCAL_MODIFICATIONS, CobblemonBiomeTags.IS_SWAMP)
        Cobblemon.implementation.addFeatureToWorldGen(SANDY_TIDEPOOLS, GenerationStep.Decoration.LOCAL_MODIFICATIONS, CobblemonBiomeTags.IS_BEACH)
        Cobblemon.implementation.addFeatureToWorldGen(SKELETAL_FALL, GenerationStep.Decoration.LOCAL_MODIFICATIONS, CobblemonBiomeTags.IS_SOUL_SAND)
        Cobblemon.implementation.addFeatureToWorldGen(STRANGE_FUNGAL_DWELLINGS, GenerationStep.Decoration.LOCAL_MODIFICATIONS, CobblemonBiomeTags.IS_FUNGUS)
        Cobblemon.implementation.addFeatureToWorldGen(VOLCANIC_PLUMES, GenerationStep.Decoration.LOCAL_MODIFICATIONS, BiomeTags.IS_NETHER)
        Cobblemon.implementation.addFeatureToWorldGen(PREHISTORIC_BIRCH_TREE, GenerationStep.Decoration.LAKES, CobblemonBiomeTags.HAS_BIRCH_LOG)
        Cobblemon.implementation.addFeatureToWorldGen(PREHISTORIC_DRIPSTONE_OASIS, GenerationStep.Decoration.LAKES, CobblemonBiomeTags.IS_DRIPSTONE)
        Cobblemon.implementation.addFeatureToWorldGen(PREHISTORIC_ENHYDRO_AGATE, GenerationStep.Decoration.LAKES, CobblemonBiomeTags.IS_LUSH)
        Cobblemon.implementation.addFeatureToWorldGen(PREHISTORIC_ERODED_PILLAR, GenerationStep.Decoration.LAKES, CobblemonBiomeTags.HAS_RED_SAND)
        Cobblemon.implementation.addFeatureToWorldGen(PREHISTORIC_DRIPSTONE_OASIS, GenerationStep.Decoration.LAKES, CobblemonBiomeTags.IS_DRIPSTONE)
        Cobblemon.implementation.addFeatureToWorldGen(PREHISTORIC_FROZEN_POND, GenerationStep.Decoration.LAKES, CobblemonBiomeTags.IS_GLACIAL)
        Cobblemon.implementation.addFeatureToWorldGen(PREHISTORIC_FROZEN_SPIKE, GenerationStep.Decoration.LAKES, CobblemonBiomeTags.IS_GLACIAL)
        Cobblemon.implementation.addFeatureToWorldGen(PREHISTORIC_DRIPSTONE_OASIS, GenerationStep.Decoration.LAKES, CobblemonBiomeTags.IS_DRIPSTONE)
        Cobblemon.implementation.addFeatureToWorldGen(PREHISTORIC_HYDROTHERMAL_VENTS, GenerationStep.Decoration.LAKES, CobblemonBiomeTags.IS_TEMPERATE_OCEAN)
        Cobblemon.implementation.addFeatureToWorldGen(PREHISTORIC_LUSH_DEN, GenerationStep.Decoration.LAKES, CobblemonBiomeTags.IS_JUNGLE)
        Cobblemon.implementation.addFeatureToWorldGen(PREHISTORIC_MOSSY_POND, GenerationStep.Decoration.LAKES, CobblemonBiomeTags.IS_LUSH)
        Cobblemon.implementation.addFeatureToWorldGen(PREHISTORIC_MUD_PIT, GenerationStep.Decoration.LAKES, CobblemonBiomeTags.IS_JUNGLE)
        Cobblemon.implementation.addFeatureToWorldGen(PREHISTORIC_OAK_TREE, GenerationStep.Decoration.LAKES, CobblemonBiomeTags.HAS_OAK_LOG)
        Cobblemon.implementation.addFeatureToWorldGen(PREHISTORIC_POWDERED_DEPOSIT, GenerationStep.Decoration.LAKES, CobblemonBiomeTags.IS_SNOWY)
        Cobblemon.implementation.addFeatureToWorldGen(PREHISTORIC_ROOTED_PITS, GenerationStep.Decoration.LAKES, CobblemonBiomeTags.IS_SWAMP)
        Cobblemon.implementation.addFeatureToWorldGen(PREHISTORIC_SANDY_DEN, GenerationStep.Decoration.LAKES, CobblemonBiomeTags.HAS_SAND)
        Cobblemon.implementation.addFeatureToWorldGen(PREHISTORIC_SPRUCE_TREE, GenerationStep.Decoration.LAKES, CobblemonBiomeTags.HAS_SPRUCE_LOG)
        Cobblemon.implementation.addFeatureToWorldGen(PREHISTORIC_SUBMERGED_IMPACT, GenerationStep.Decoration.LAKES, CobblemonBiomeTags.IS_TEMPERATE_OCEAN)
        Cobblemon.implementation.addFeatureToWorldGen(PREHISTORIC_SUBMERGED_SPIKE, GenerationStep.Decoration.UNDERGROUND_STRUCTURES, CobblemonBiomeTags.IS_FROZEN_OCEAN)
        Cobblemon.implementation.addFeatureToWorldGen(PREHISTORIC_PRESERVED_SKELETON, GenerationStep.Decoration.UNDERGROUND_STRUCTURES, CobblemonBiomeTags.IS_FROZEN_OCEAN)
        Cobblemon.implementation.addFeatureToWorldGen(PREHISTORIC_SUNSCORCHED_DEN, GenerationStep.Decoration.LAKES, CobblemonBiomeTags.HAS_RED_SAND)
        Cobblemon.implementation.addFeatureToWorldGen(PREHISTORIC_SUNSCORCHED_REMAINS, GenerationStep.Decoration.LAKES, CobblemonBiomeTags.HAS_RED_SAND)
        Cobblemon.implementation.addFeatureToWorldGen(PREHISTORIC_SUSPICIOUS_MOUNDS, GenerationStep.Decoration.LAKES, CobblemonBiomeTags.IS_PLAINS)
        Cobblemon.implementation.addFeatureToWorldGen(PREHISTORIC_UNDERWATER_FISSURE, GenerationStep.Decoration.LAKES, CobblemonBiomeTags.IS_TEMPERATE_OCEAN)
        Cobblemon.implementation.addFeatureToWorldGen(PREHISTORIC_VIBRANT_HYDROTHERMAL_VENTS, GenerationStep.Decoration.LAKES, CobblemonBiomeTags.IS_WARM_OCEAN)
        Cobblemon.implementation.addFeatureToWorldGen(CRUMBLING_ARCH, GenerationStep.Decoration.UNDERGROUND_DECORATION, BiomeTags.IS_OVERWORLD)
        Cobblemon.implementation.addFeatureToWorldGen(DEEP_CRYPT, GenerationStep.Decoration.UNDERGROUND_DECORATION, CobblemonBiomeTags.IS_DEEP_DARK)
        Cobblemon.implementation.addFeatureToWorldGen(ROOTED_ARCH, GenerationStep.Decoration.UNDERGROUND_DECORATION, CobblemonBiomeTags.IS_LUSH)
    }

    private fun of(id: String): ResourceKey<PlacedFeature> = ResourceKey.create(Registries.PLACED_FEATURE, cobblemonResource(id))
}