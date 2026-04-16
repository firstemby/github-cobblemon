/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common

import com.cobblemon.mod.common.platform.PlatformRegistry
import com.google.common.collect.ImmutableSet
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import net.minecraft.core.Holder
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.sounds.SoundEvent
import net.minecraft.world.entity.ai.village.poi.PoiType
import net.minecraft.world.entity.npc.villager.VillagerProfession
import net.minecraft.world.item.trading.TradeSet

object CobblemonVillagerProfessions: PlatformRegistry<Registry<VillagerProfession>, ResourceKey<Registry<VillagerProfession>>, VillagerProfession>() {
    override val registry: Registry<VillagerProfession> = BuiltInRegistries.VILLAGER_PROFESSION
    override val resourceKey: ResourceKey<Registry<VillagerProfession>> = Registries.VILLAGER_PROFESSION

    @JvmField
    val NURSE = profession(CobblemonPoiTypes.NURSE_KEY, CobblemonSounds.VILLAGER_WORK_NURSE)

    /**
     * Register a villager profession texture override for Cobblemon villagers of the given [VillagerProfession] for specified names.
     *
     * @param profession The Cobblemon [VillagerProfession] being overridden.
     * @return The profession override texture file name and a list of names that will trigger the override, as a pair.
     */
    fun getNameTagOverride(profession: VillagerProfession?): Pair<String, Array<String>>? = when (profession) {
        NURSE -> Pair("nurse_joy", arrayOf("ジョーイ", "간호순", "祖兒", "喬伊", "乔伊", "האחות ג'וי", "الممرضة جوي", "Joy", "Joelle", "Joëlle", "Джой", "จอย"))
        else -> null
    }

    private fun profession(resourceKey: ResourceKey<PoiType>, soundEvent: SoundEvent?): VillagerProfession =
        create(resourceKey.identifier(), VillagerProfession(
            Component.translatable("entity.${resourceKey.identifier().namespace}.villager.${resourceKey.identifier().path}"),
            { holder: Holder<PoiType> -> holder.`is`(resourceKey) },
            { holder: Holder<PoiType> -> holder.`is`(resourceKey) },
            ImmutableSet.of(), ImmutableSet.of(), soundEvent, if (resourceKey == CobblemonPoiTypes.NURSE_KEY) nurseTradeSetsByLevel() else Int2ObjectMaps.emptyMap()
        ))

    private fun nurseTradeSetsByLevel() = Int2ObjectOpenHashMap<ResourceKey<TradeSet>>().apply {
        put(1, tradeSetKey(1))
        put(2, tradeSetKey(2))
        put(3, tradeSetKey(3))
        put(4, tradeSetKey(4))
        put(5, tradeSetKey(5))
    }

    private fun tradeSetKey(level: Int): ResourceKey<TradeSet> =
        ResourceKey.create(Registries.TRADE_SET, com.cobblemon.mod.common.util.cobblemonResource("nurse/level_$level"))
}
