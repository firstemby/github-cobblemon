/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.pokedex.page

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.resources.ResourceLocation

/**
 * TEMPORARY testing scaffold: every bundled (non-habitat) structure, treated as already-discovered so
 * the CobblePedia Structures page and structure preview can be exercised without scanning. The entry
 * paths (e.g. "fossils/lush_den", "ruins/crumbling_arch") are read from a generated manifest and map
 * straight to `data/cobblemon/structure/<path>.nbt`. Display-only (client-side); remove with the
 * manifest once real structure scan content is wired up.
 */
object TestUnlockedStructures {
    private const val MANIFEST = "/assets/cobblemon/cobblepedia/test_unlocked_structures.txt"

    val entries: List<ResourceLocation> by lazy { load() }

    private fun load(): List<ResourceLocation> {
        val stream = TestUnlockedStructures::class.java.getResourceAsStream(MANIFEST) ?: run {
            Cobblemon.LOGGER.warn("Missing test_unlocked_structures manifest at {}", MANIFEST)
            return emptyList()
        }
        return stream.bufferedReader().useLines { lines ->
            lines.map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .map { cobblemonResource(it) }
                .toList()
        }
    }
}
