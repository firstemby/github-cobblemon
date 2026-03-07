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
 * Registry for move category mappings loaded from `data/<namespace>/move_categories/`.
 * Moves can appear in multiple categories.
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

    private val mappings = mutableMapOf<String, MutableSet<MoveCategory>>()

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

    fun getCategories(moveName: String): Set<MoveCategory> {
        return mappings[moveName.lowercase()] ?: emptySet()
    }

    fun getCategories(move: MoveTemplate): Set<MoveCategory> {
        return getCategories(move.name)
    }

    fun hasCategory(moveName: String, category: MoveCategory): Boolean {
        return getCategories(moveName).contains(category)
    }

    fun hasCategory(move: MoveTemplate, category: MoveCategory): Boolean {
        return hasCategory(move.name, category)
    }

    fun getMovesWithCategory(category: MoveCategory): Set<String> {
        return mappings.filterValues { it.contains(category) }.keys
    }

    /** Fallback when no explicit mapping exists for a move. */
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

    fun getCategoriesOrInfer(move: MoveTemplate): Set<MoveCategory> {
        val explicit = getCategories(move)
        return if (explicit.isNotEmpty()) explicit else inferCategories(move)
    }
}
