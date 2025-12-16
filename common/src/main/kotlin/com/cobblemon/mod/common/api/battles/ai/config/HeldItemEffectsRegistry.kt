/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.battles.ai.config

import com.cobblemon.mod.common.api.data.JsonDataRegistry
import com.cobblemon.mod.common.api.reactive.SimpleObservable
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.asIdentifierDefaultingNamespace
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.packs.PackType

/**
 * Registry for held item effects used by the battle AI.
 * Each JSON file defines all effects for a single item using sub-objects.
 *
 * Files are loaded from: data/namespace/held_item_effects/item.json
 *
 * @author Amo Aster
 * @since December 2025
 */
object HeldItemEffectsRegistry : JsonDataRegistry<HeldItemEffects> {

    override val id = cobblemonResource("held_item_effects")
    override val type = PackType.SERVER_DATA
    override val observable = SimpleObservable<HeldItemEffectsRegistry>()

    override val gson: Gson = GsonBuilder()
        .disableHtmlEscaping()
        .create()

    override val typeToken: TypeToken<HeldItemEffects> = TypeToken.get(HeldItemEffects::class.java)
    override val resourcePath = "held_item_effects"

    private val itemEffects = mutableMapOf<ResourceLocation, HeldItemEffects>()
    private val categoryToItems = mutableMapOf<String, MutableSet<ResourceLocation>>()

    override fun reload(data: Map<ResourceLocation, HeldItemEffects>) {
        itemEffects.clear()
        categoryToItems.clear()

        for ((_, effects) in data) {
            val itemId = effects.item.asIdentifierDefaultingNamespace()
            itemEffects[itemId] = effects

            for (category in effects.categories) {
                categoryToItems.getOrPut(category.lowercase()) { mutableSetOf() }.add(itemId)
            }
        }

        observable.emit(this)
    }

    override fun sync(player: ServerPlayer) {
        // Item effects don't need to sync to client
    }

    fun getEffects(itemId: ResourceLocation): HeldItemEffects {
        return itemEffects[itemId] ?: HeldItemEffects.EMPTY
    }

    fun getEffects(itemName: String): HeldItemEffects {
        val itemId = itemName.asIdentifierDefaultingNamespace()
        return itemEffects[itemId] ?: HeldItemEffects.EMPTY
    }

    fun hasEffects(itemId: ResourceLocation): Boolean = itemEffects.containsKey(itemId)

    fun hasEffects(itemName: String): Boolean {
        val itemId = itemName.asIdentifierDefaultingNamespace()
        return itemEffects.containsKey(itemId)
    }

    fun hasCategory(itemId: ResourceLocation, category: String): Boolean {
        return getEffects(itemId).hasCategory(category)
    }

    fun hasCategory(itemName: String, category: String): Boolean {
        return getEffects(itemName).hasCategory(category)
    }

    fun getCategories(itemId: ResourceLocation): List<String> = getEffects(itemId).categories

    fun getCategories(itemName: String): List<String> = getEffects(itemName).categories

    fun getItemsInCategory(category: String): Set<ResourceLocation> {
        return categoryToItems[category.lowercase()]?.toSet() ?: emptySet()
    }

    fun boostsType(itemName: String, type: String): Boolean = getEffects(itemName).boostsType(type)

    fun getTypeMultiplier(itemName: String, type: String): Double = getEffects(itemName).getTypeMultiplier(type)

    fun getBoostedType(itemName: String): String? {
        val effects = getEffects(itemName)
        return effects.damage?.typeBoosts?.entries
            ?.firstOrNull { it.value > 1.0 }
            ?.key
    }

    fun getStatMultiplier(itemName: String, stat: String): Double = getEffects(itemName).getStatMultiplier(stat)

    fun restrictsMove(itemName: String): Boolean = getEffects(itemName).locksMove()

    fun getDamageMultiplier(itemName: String): Double = getEffects(itemName).getDamageMultiplier()

    fun getRecoil(itemName: String): Double = getEffects(itemName).getRecoilPercent()

    fun isChoiceItem(itemName: String): Boolean = hasCategory(itemName, "choice")

    fun isBerry(itemName: String): Boolean = hasCategory(itemName, "berry")

    fun isConsumable(itemName: String): Boolean {
        return getEffects(itemName).isConsumable() || hasCategory(itemName, "consumable")
    }

    /**
     * Calculates total damage multiplier for a move, considering type boosts,
     * stat boosts, and flat damage multipliers.
     */
    fun calculateDamageMultiplier(itemName: String, moveType: String, isPhysical: Boolean): Double {
        val effects = getEffects(itemName)
        var multiplier = 1.0

        multiplier *= effects.getTypeMultiplier(moveType)

        val statName = if (isPhysical) "attack" else "specialattack"
        multiplier *= effects.getStatMultiplier(statName)

        multiplier *= effects.getDamageMultiplier()

        return multiplier
    }
}
