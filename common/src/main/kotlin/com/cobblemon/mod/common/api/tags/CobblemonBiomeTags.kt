/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.tags

import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.core.registries.Registries
import net.minecraft.tags.TagKey

/**
 * A collection of the Cobblemon [TagKey]s related to the [Registry.BIOME_KEY].
 *
 * @author Veraxiel, Licious
 * @since July 8th, 2022
 */
object CobblemonBiomeTags {

    @JvmField
    val IS_AUTUMN = create("has_season/autumn")
    @JvmField
    val IS_SPRING = create("has_season/spring")
    @JvmField
    val IS_SUMMER = create("has_season/summer")
    @JvmField
    val IS_TEMPERATE = create("is_temperate")
    @JvmField
    val IS_WINTER = create("has_season/winter")
    @JvmField
    val IS_SWAMP = create("is_swamp")
    @JvmField
    val IS_PLAINS = create("is_plains")
    @JvmField
    val IS_BEACH = create("is_beach")

    // Has Feature tags
    @JvmField
    val HAS_APRICORNS_DENSE = create("has_feature/apricorns_dense")
    @JvmField
    val HAS_APRICORNS_NORMAL = create("has_feature/apricorns_normal")
    @JvmField
    val HAS_APRICORNS_SPARSE = create("has_feature/apricorns_sparse")
    @JvmField
    val HAS_REVIVAL_HERBS = create("has_feature/revival_herbs")
    @JvmField
    val ALLOWED_BIOMES_SACCHARINE_TREE = create("has_feature/saccharine_tree")

    // Nests
    @JvmField
    val HAS_STICK_NEST = create("has_nest/nest_stick")
    @JvmField
    val HAS_STICK_NEST_TREES = create("has_nest/nest_stick_trees")
    @JvmField
    val HAS_LEAF_NEST_TREES = create("has_nest/nest_leaf_trees")
    @JvmField
    val HAS_SNOW_NEST = create("has_nest/nest_snow")
    @JvmField
    val HAS_SAND_NEST = create("has_nest/nest_sand")
    @JvmField
    val HAS_RED_SAND_NEST = create("has_nest/nest_red_sand")
    @JvmField
    val HAS_SEAGRASS_NEST = create("has_nest/nest_seagrass")
    @JvmField
    val HAS_WARM_OCEAN_NEST = create("has_nest/nest_warm_ocean")
    @JvmField
    val HAS_STONE_NEST = create("has_nest/nest_stone")
    @JvmField
    val HAS_STONE_NEST_CAVES = create("has_nest/nest_stone_caves")
    @JvmField
    val HAS_DEEPSLATE_NEST_CAVES = create("has_nest/nest_deepslate_caves")
    @JvmField
    val HAS_LUSH_NEST_CAVES = create("has_nest/nest_lush_caves")
    @JvmField
    val HAS_BASALT_NEST = create("has_nest/nest_basalt")
    @JvmField
    val HAS_NETHER_NEST = create("has_nest/nest_nether")
    @JvmField
    val HAS_SOUL_SAND_NEST = create("has_nest/nest_soul_sand")
    @JvmField
    val HAS_CHORUS_NEST = create("has_nest/nest_chorus")

    // Has Ore tags
    @JvmField
    val HAS_DAWN_STONE_ORE = create("has_ore/ore_dawn_stone_normal")
    @JvmField
    val HAS_DAWN_STONE_ORE_RARE = create("has_ore/ore_dawn_stone_rare")
    @JvmField
    val HAS_DUSK_STONE_ORE = create("has_ore/ore_dusk_stone_normal")
    @JvmField
    val HAS_DUSK_STONE_ORE_RARE = create("has_ore/ore_dusk_stone_rare")
    @JvmField
    val HAS_FIRE_STONE_ORE = create("has_ore/ore_fire_stone_normal")
    @JvmField
    val HAS_FIRE_STONE_ORE_RARE = create("has_ore/ore_fire_stone_rare")
    @JvmField
    val HAS_FIRE_STONE_ORE_NETHER = create("has_ore/ore_fire_stone_nether")
    @JvmField
    val HAS_ICE_STONE_ORE = create("has_ore/ore_ice_stone_normal")
    @JvmField
    val HAS_ICE_STONE_ORE_RARE = create("has_ore/ore_ice_stone_rare")
    @JvmField
    val HAS_LEAF_STONE_ORE = create("has_ore/ore_leaf_stone_normal")
    @JvmField
    val HAS_LEAF_STONE_ORE_RARE = create("has_ore/ore_leaf_stone_rare")
    @JvmField
    val HAS_MOON_STONE_ORE = create("has_ore/ore_moon_stone_normal")
    @JvmField
    val HAS_MOON_STONE_ORE_RARE = create("has_ore/ore_moon_stone_rare")
    @JvmField
    val HAS_MOON_STONE_ORE_DRIPSTONE = create("has_ore/ore_moon_stone_dripstone")
    @JvmField
    val HAS_SHINY_STONE_ORE = create("has_ore/ore_shiny_stone_normal")
    @JvmField
    val HAS_SHINY_STONE_ORE_RARE = create("has_ore/ore_shiny_stone_rare")
    @JvmField
    val HAS_SUN_STONE_ORE = create("has_ore/ore_sun_stone_normal")
    @JvmField
    val HAS_SUN_STONE_ORE_RARE = create("has_ore/ore_sun_stone_rare")
    @JvmField
    val HAS_THUNDER_STONE_ORE = create("has_ore/ore_thunder_stone_normal")
    @JvmField
    val HAS_THUNDER_STONE_ORE_RARE = create("has_ore/ore_thunder_stone_rare")
    @JvmField
    val HAS_WATER_STONE_ORE = create("has_ore/ore_water_stone_normal")
    @JvmField
    val HAS_WATER_STONE_ORE_RARE = create("has_ore/ore_water_stone_rare")

    private fun create(path: String) = TagKey.create(Registries.BIOME, cobblemonResource(path))
}