/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common

import com.cobblemon.mod.common.platform.PlatformRegistry
import com.cobblemon.mod.common.util.cobblemonResource
import com.google.common.collect.ImmutableSet
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import net.minecraft.core.Holder
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.sounds.SoundEvent
import net.minecraft.world.entity.ai.village.poi.PoiType
import net.minecraft.world.entity.npc.villager.VillagerProfession
import net.minecraft.world.item.trading.TradeSet

object CobblemonVillagerProfessions: PlatformRegistry<Registry<VillagerProfession>, ResourceKey<Registry<VillagerProfession>>, VillagerProfession>() {
    override val registry: Registry<VillagerProfession> = BuiltInRegistries.VILLAGER_PROFESSION
    override val resourceKey: ResourceKey<Registry<VillagerProfession>> = Registries.VILLAGER_PROFESSION

    val NURSE_KEY = cobblemonResource("nurse")
    @JvmField
    val NURSE = profession(NURSE_KEY, CobblemonPoiTypes.NURSE_KEY, CobblemonSounds.VILLAGER_WORK_NURSE, tradeSetsByLevel(NURSE_KEY))

    private fun profession(
        profession: Identifier,
        resourceKey: ResourceKey<PoiType>,
        soundEvent: SoundEvent?,
        nurseTradeSetsByLevel: Int2ObjectOpenHashMap<ResourceKey<TradeSet>>
    ): VillagerProfession =
        create(profession, VillagerProfession(
            Component.translatable("entity.${profession.namespace}.villager.${profession.path}"),
            { holder: Holder<PoiType> -> holder.`is`(resourceKey) },
            { holder: Holder<PoiType> -> holder.`is`(resourceKey) },
            ImmutableSet.of(), ImmutableSet.of(), soundEvent, nurseTradeSetsByLevel
        ))

    private fun tradeSetsByLevel(profession: Identifier, maxLevel: Int = 5) : Int2ObjectOpenHashMap<ResourceKey<TradeSet>> {
        val result = Int2ObjectOpenHashMap<ResourceKey<TradeSet>>()
        for (i in 1..maxLevel) {
            result.put(i, tradeSetKey(profession, i))
        }
        return result
    }

    private fun tradeSetKey(profession: Identifier, level: Int): ResourceKey<TradeSet> =
        ResourceKey.create(Registries.TRADE_SET, cobblemonResource("${profession.path}/level_$level"))
}
