/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.integration.rei

import me.shedaniel.rei.api.common.entry.comparison.ComparisonContext
import me.shedaniel.rei.api.common.entry.comparison.EntryComparator
import net.minecraft.core.component.DataComponentType
import net.minecraft.world.item.ItemStack

class CobblemonReiComponentComparator(val component: DataComponentType<*>): EntryComparator<ItemStack> {
    override fun hash(context: ComparisonContext, stack: ItemStack): Long {
        return stack.get(component).hashCode().toLong()
    }
}