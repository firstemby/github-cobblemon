/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.compat.villager

import net.minecraft.server.level.ServerLevel
import net.minecraft.util.RandomSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.trading.ItemCost
import net.minecraft.world.item.trading.MerchantOffer
import net.minecraft.world.level.ItemLike

object VillagerTrades {
    fun interface ItemListing {
        fun getOffer(serverLevel: ServerLevel, entity: Entity, randomSource: RandomSource): MerchantOffer
    }

    class ItemsForEmeralds(
        val item: ItemLike,
        val emeraldCost: Int,
        val numberOfItems: Int,
        val maxUses: Int,
        val villagerXp: Int,
        val priceMultiplier: Float = 0.05F
    ) : ItemListing {
        override fun getOffer(serverLevel: ServerLevel, entity: Entity, randomSource: RandomSource): MerchantOffer {
            return MerchantOffer(ItemCost(Items.EMERALD, emeraldCost), ItemStack(item, numberOfItems), maxUses, villagerXp, priceMultiplier)
        }
    }
}
