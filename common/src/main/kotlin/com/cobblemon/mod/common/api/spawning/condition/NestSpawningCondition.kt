/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.spawning.condition

import com.cobblemon.mod.common.api.conditional.RegistryLikeCondition
import com.cobblemon.mod.common.api.spawning.context.FishingSpawningContext
import com.cobblemon.mod.common.api.spawning.context.NestSpawningContext
import com.cobblemon.mod.common.util.itemRegistry
import net.minecraft.world.item.Item
import net.minecraft.world.item.enchantment.EnchantmentHelper
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.level.block.Block

/**
 * A spawning condition that applies to [NestSpawningContext]s.
 *
 * @author Hiroku
 * @since February 3rd, 2024
 */
class NestSpawningCondition: SpawningCondition<NestSpawningContext>() {
    override fun contextClass() = NestSpawningContext::class.java

    var neededNearbyBlocks: MutableList<RegistryLikeCondition<Block>>? = null

    override fun fits(ctx: NestSpawningContext): Boolean {
        if (!super.fits(ctx)) {
            return false
        } else if (neededNearbyBlocks != null && neededNearbyBlocks!!.none { cond -> ctx.nearbyBlockTypes.any { cond.fits(it, ctx.blockRegistry) } }) {
            return false
        }
        return true
    }

    companion object {
        const val NAME = "nest"
    }
}