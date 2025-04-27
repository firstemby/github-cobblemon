/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cooking

import com.cobblemon.mod.common.CobblemonItemComponents
import com.cobblemon.mod.common.api.berry.Berries
import com.cobblemon.mod.common.api.conditional.RegistryLikeCondition
import com.cobblemon.mod.common.api.conditional.RegistryLikeIdentifierCondition
import com.cobblemon.mod.common.api.data.JsonDataRegistry
import com.cobblemon.mod.common.api.fishing.SpawnBait
import com.cobblemon.mod.common.api.reactive.SimpleObservable
import com.cobblemon.mod.common.net.messages.client.cooking.SeasoningRegistrySyncPacket
import com.cobblemon.mod.common.util.adapters.IdentifierAdapter
import com.cobblemon.mod.common.util.adapters.ItemLikeConditionAdapter
import com.cobblemon.mod.common.util.cobblemonResource
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.packs.PackType
import net.minecraft.world.item.DyeColor
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

object Seasonings : JsonDataRegistry<Seasoning> {
    override val id = cobblemonResource("seasonings")
    override val type = PackType.SERVER_DATA
    override val observable = SimpleObservable<Seasonings>()
    override val typeToken: TypeToken<Seasoning> = TypeToken.get(Seasoning::class.java)
    override val resourcePath = "seasonings"
    override val gson: Gson = GsonBuilder()
        .registerTypeAdapter(ResourceLocation::class.java, IdentifierAdapter)
        .registerTypeAdapter(TypeToken.getParameterized(RegistryLikeCondition::class.java, Item::class.java).type, ItemLikeConditionAdapter)
        .setPrettyPrinting()
        .create()

    val seasonings = mutableListOf<Seasoning>()

    val BLANK_SEASONING = Seasoning(
        ingredient = RegistryLikeIdentifierCondition<Item>(cobblemonResource("blank")),
        flavours = emptyMap(),
        colour = DyeColor.WHITE
    )

    override fun sync(player: ServerPlayer) {
        SeasoningRegistrySyncPacket(seasonings.toList()).sendToPlayer(player)
    }

    override fun reload(data: Map<ResourceLocation, Seasoning>) {
        // this needs to sideload the berry data so we don't get duplicate JSONs.
        val finalData = Berries.all().associate {
            it.identifier to Seasoning(
                ingredient = RegistryLikeIdentifierCondition(it.identifier),
                flavours = it.flavours.toMap(),
                colour = it.colour
            )
        }.toMutableMap()
        finalData.putAll(data)
        seasonings.addAll(finalData.values)
    }

    fun reloadEntries(seasonings: Collection<Seasoning>) {
        this.seasonings.clear()
        this.seasonings.addAll(seasonings)
    }

    fun getFlavoursFromItemStack(stack: ItemStack): Map<Flavour, Int> {
        val holder = stack.itemHolder
        val seasoning = seasonings.find { it.ingredient.fits(holder) } ?: BLANK_SEASONING
        val inherentFlavours = stack.get(CobblemonItemComponents.FLAVOUR)?.flavours ?: emptyMap()

        return seasoning.flavours.mapValues { (flavour, value) -> inherentFlavours.getOrDefault(flavour, 0) + value }
    }

    fun getBaitEffectsFromItemStack(stack: ItemStack): List<SpawnBait.Effect> {
        return getFromItemStack(stack)?.baitEffects ?: emptyList()
    }

    fun getFromItemStack(stack: ItemStack): Seasoning? {
        val holder = stack.itemHolder
        return seasonings.find { it.ingredient.fits(holder) }
    }

    fun isSeasoning(stack: ItemStack) = getFromItemStack(stack) != null
}
