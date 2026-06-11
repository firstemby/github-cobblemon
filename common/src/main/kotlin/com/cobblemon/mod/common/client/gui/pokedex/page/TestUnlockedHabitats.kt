/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.pokedex.page

import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.resources.ResourceLocation

/**
 * TEMPORARY testing scaffold: every habitat structure, treated as already-discovered so the
 * CobblePedia Habitats page and structure preview can be exercised without scanning. Display-only
 * (client-side); remove once habitat scan content is wired up.
 */
object TestUnlockedHabitats {
    val entries: List<ResourceLocation> = listOf(
        "abandoned_fortress",
        "ancient_wellspring",
        "badlands_shaded_rock",
        "badlands_shaded_rock_connector",
        "berry_patch",
        "birch",
        "birch_wildlife_scar",
        "birch_wildlife_scar_connector",
        "bug_mound",
        "bug_mound_connector",
        "carved_ice_spikes",
        "carved_ice_spikes_connector",
        "chorus_briar",
        "dam_test",
        "dead_birch_path",
        "deep_root",
        "deep_sea_spire",
        "desert_oasis",
        "desert_oasis_start",
        "desert_shaded_rock",
        "desert_shaded_rock_connector",
        "drifting_iceberg",
        "drifting_iceberg_extender",
        "drifting_iceberg_pieces",
        "earthen_hive",
        "exposed_geode",
        "fae_mound",
        "fae_mounds_connector",
        "flowerbed_clearing",
        "flowerbed_tree",
        "freshwater_pond",
        "freshwater_pond_start",
        "fungal_dwelling",
        "fungal_dwelling_connector",
        "fungal_dwelling_connector_start",
        "lost_ruins",
        "lush_bog_connector",
        "lush_canopy",
        "lush_canopy_start",
        "lush_cenote",
        "lush_cenote_connector",
        "lush_cenote_end",
        "lush_cenote_stopper",
        "lush_peat_bog",
        "lush_peat_bog_start",
        "meteorite_impact",
        "meteorite_impact_start",
        "natural_lightningrod_connector",
        "natural_lightningrod_large",
        "natural_lightningrod_small",
        "oak_wildlife_scar",
        "oak_wildlife_scar_connector",
        "parched_peat_bog",
        "parched_peat_bog_start",
        "peat_bog_connector",
        "pinkflowerbed_clearing",
        "pinkflowerbed_tree",
        "quartz_spikes",
        "reclaimed_deserted_monument",
        "reclaimed_deserted_monument_connector",
        "reclaimed_lush_monument",
        "reclaimed_lush_monument_connector",
        "rocky_outcrop",
        "rocky_outcrop_start",
        "rocky_tidepool",
        "root_nursery",
        "sandpit_clearing",
        "sandy_tidepool",
        "skeletal_fall",
        "snowy_burrow",
        "snowy_grotto",
        "snowy_thermal_vent",
        "spruce_wildlife_scar",
        "spruce_wildlife_scar_connector",
        "strange_fungal_dwelling",
        "sunflowerbed_clearing",
        "sunscorched_clearing",
        "thermal_crevice",
        "thermal_crevice_fallback",
        "treasure_hoard",
        "vent_connector",
        "village_desert",
        "village_plains",
        "village_savanna",
        "village_snowy",
        "village_taiga",
        "volcanic_plume",
        "zen_garden",
        "zen_garden_start",
    ).map { cobblemonResource(it) }
}
