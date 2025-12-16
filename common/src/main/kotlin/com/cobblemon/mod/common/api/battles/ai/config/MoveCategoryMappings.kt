/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.battles.ai.config

import com.cobblemon.mod.common.api.data.JsonDataRegistry
import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.api.moves.categories.DamageCategories
import com.cobblemon.mod.common.api.reactive.SimpleObservable
import com.cobblemon.mod.common.util.adapters.IdentifierAdapter
import com.cobblemon.mod.common.util.adapters.LowercaseEnumAdapter
import com.cobblemon.mod.common.util.cobblemonResource
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.packs.PackType

/**
 * Registry for move category files that allows datapacks to categorize moves for AI decision-making.
 * Files are loaded from `data/<namespace>/move_categories/`.
 *
 * Each file defines a single category and a list of moves belonging to that category.
 * Moves can appear in multiple files (and thus multiple categories).
 *
 * Supports merge behavior via the `replace` field in JSON:
 * - `replace: false` (default): Adds moves to the existing category
 * - `replace: true`: Replaces all existing moves for this category
 *
 * Example JSON (heal.json):
 * ```json
 * {
 *   "category": "heal",
 *   "replace": false,
 *   "moves": ["recover", "roost", "softboiled", "moonlight"]
 * }
 * ```
 *
 * @author Amo Aster
 * @since December 2025
 */
object MoveCategoryMappings : JsonDataRegistry<MoveCategoryGroup> {
    override val id = cobblemonResource("move_category_mappings")
    override val type = PackType.SERVER_DATA
    override val observable = SimpleObservable<MoveCategoryMappings>()
    override val typeToken: TypeToken<MoveCategoryGroup> = TypeToken.get(MoveCategoryGroup::class.java)
    override val resourcePath = "move_categories"
    override val gson: Gson = GsonBuilder()
        .registerTypeAdapter(ResourceLocation::class.java, IdentifierAdapter)
        .registerTypeAdapter(MoveCategory::class.java, LowercaseEnumAdapter(MoveCategory::class.java))
        .disableHtmlEscaping()
        .setPrettyPrinting()
        .create()

    /** Maps move names to their AI categories */
    private val mappings = mutableMapOf<String, MutableSet<MoveCategory>>()

    /** Server-side only, no sync needed */
    override fun sync(player: ServerPlayer) {}

    override fun reload(data: Map<ResourceLocation, MoveCategoryGroup>) {
        mappings.clear()

        for ((_, file) in data) {
            for (move in file.moves) {
                mappings.getOrPut(move.lowercase()) { mutableSetOf() }.add(file.category)
            }
        }

        observable.emit(this)
    }

    /**
     * Gets all AI categories for a move.
     * Returns an empty set if no categories are defined for this move.
     *
     * @param moveName The move name in Showdown ID format (e.g., "swordsdance")
     * @return The set of AI categories for this move
     */
    fun getCategories(moveName: String): Set<MoveCategory> {
        return mappings[moveName.lowercase()] ?: emptySet()
    }

    /**
     * Gets all AI categories for a move template.
     *
     * @param move The MoveTemplate to get categories for
     * @return The set of AI categories for this move
     */
    fun getCategories(move: MoveTemplate): Set<MoveCategory> {
        return getCategories(move.name)
    }

    /**
     * Checks if a move has a specific category.
     *
     * @param moveName The move name in Showdown ID format
     * @param category The category to check for
     * @return True if the move has this category
     */
    fun hasCategory(moveName: String, category: MoveCategory): Boolean {
        return getCategories(moveName).contains(category)
    }

    /**
     * Checks if a move template has a specific category.
     *
     * @param move The MoveTemplate to check
     * @param category The category to check for
     * @return True if the move has this category
     */
    fun hasCategory(move: MoveTemplate, category: MoveCategory): Boolean {
        return hasCategory(move.name, category)
    }

    /**
     * Gets all moves that have a specific category.
     *
     * @param category The category to filter by
     * @return Set of move names that have this category
     */
    fun getMovesWithCategory(category: MoveCategory): Set<String> {
        return mappings.filterValues { it.contains(category) }.keys
    }

    /**
     * Infers basic categories from a MoveTemplate's properties.
     * This is used as a fallback when no explicit mapping exists.
     *
     * @param move The MoveTemplate to infer categories from
     * @return Inferred categories based on move properties
     */
    fun inferCategories(move: MoveTemplate): Set<MoveCategory> {
        val inferred = mutableSetOf<MoveCategory>()

        when (move.damageCategory) {
            DamageCategories.STATUS -> inferred.add(MoveCategory.STATUS)
            else -> if (move.power > 0) inferred.add(MoveCategory.DAMAGE)
        }

        if (move.priority > 0) {
            inferred.add(MoveCategory.PRIORITY)
        }

        return inferred
    }

    /**
     * Gets categories for a move, falling back to inference if no mapping exists.
     *
     * @param move The MoveTemplate to get categories for
     * @return Categories from mapping or inferred from properties
     */
    fun getCategoriesOrInfer(move: MoveTemplate): Set<MoveCategory> {
        val explicit = getCategories(move)
        return if (explicit.isNotEmpty()) explicit else inferCategories(move)
    }
}
