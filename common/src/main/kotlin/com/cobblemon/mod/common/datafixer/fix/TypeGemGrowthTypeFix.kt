/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.datafixer.fix

import com.mojang.datafixers.DSL
import com.mojang.datafixers.DataFix
import com.mojang.datafixers.TypeRewriteRule
import com.mojang.datafixers.schemas.Schema
import com.mojang.serialization.Dynamic
import net.minecraft.util.datafix.fixes.References

/**
 * Merges the old `should_grow` and `stunted` type gem cluster properties into `growth_type`.
 */
class TypeGemGrowthTypeFix(outputSchema: Schema) : DataFix(outputSchema, false) {

    override fun makeRule(): TypeRewriteRule {
        return fixTypeEverywhereTyped("TypeGemGrowthTypeFix", inputSchema.getType(References.CHUNK)) { chunk ->
            chunk.update(DSL.remainderFinder(), ::fixChunk)
        }
    }

    private fun fixChunk(chunk: Dynamic<*>): Dynamic<*> {
        return chunk.update("sections") { sections ->
            sections.createList(sections.asStream().map(::fixSection))
        }
    }

    private fun fixSection(section: Dynamic<*>): Dynamic<*> {
        return section.update("block_states") { blockStates ->
            blockStates.update("palette") { palette ->
                palette.createList(palette.asStream().map(::fixPaletteEntry))
            }
        }
    }

    private fun fixPaletteEntry(entry: Dynamic<*>): Dynamic<*> {
        if (entry.get("Name").asString("") !in CLUSTER_IDS) return entry

        return entry.update("Properties") { properties ->
            if (properties.get(GROWTH_TYPE).result().isPresent) return@update properties

            val shouldGrow = properties.get(SHOULD_GROW).asString(OLD_SHOULD_GROW_DEFAULT) == "true"
            val stunted = properties.get(STUNTED).asString(OLD_STUNTED_DEFAULT) == "true"

            properties
                .remove(SHOULD_GROW)
                .remove(STUNTED)
                .set(GROWTH_TYPE, properties.createString(growthTypeFor(shouldGrow, stunted)))
        }
    }

    private fun growthTypeFor(shouldGrow: Boolean, stunted: Boolean): String {
        return when {
            stunted -> GROWTH_TYPE_STUNTED
            shouldGrow -> GROWTH_TYPE_FULL
            else -> GROWTH_TYPE_NONE
        }
    }

    companion object {
        private const val SHOULD_GROW = "should_grow"
        private const val STUNTED = "stunted"
        private const val GROWTH_TYPE = "growth_type"

        private const val OLD_SHOULD_GROW_DEFAULT = "true"
        private const val OLD_STUNTED_DEFAULT = "false"

        private const val GROWTH_TYPE_FULL = "full"
        private const val GROWTH_TYPE_STUNTED = "stunted"
        private const val GROWTH_TYPE_NONE = "none"

        // ID's as they were when this fix was made.
        private val CLUSTER_IDS = setOf(
            "cobblemon:normal_gem_cluster",
            "cobblemon:fire_gem_cluster",
            "cobblemon:water_gem_cluster",
            "cobblemon:electric_gem_cluster",
            "cobblemon:grass_gem_cluster",
            "cobblemon:ice_gem_cluster",
            "cobblemon:fighting_gem_cluster",
            "cobblemon:poison_gem_cluster",
            "cobblemon:ground_gem_cluster",
            "cobblemon:flying_gem_cluster",
            "cobblemon:psychic_gem_cluster",
            "cobblemon:bug_gem_cluster",
            "cobblemon:rock_gem_cluster",
            "cobblemon:ghost_gem_cluster",
            "cobblemon:dragon_gem_cluster",
            "cobblemon:dark_gem_cluster",
            "cobblemon:steel_gem_cluster",
            "cobblemon:fairy_gem_cluster"
        )
    }
}
