/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.integration.jei

import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter
import mezz.jei.api.ingredients.subtypes.UidContext
import net.minecraft.core.component.DataComponentType
import net.minecraft.world.item.ItemStack

class CobblemonJeiComponentComparator(val component: DataComponentType<*>): ISubtypeInterpreter<ItemStack> {
    override fun getSubtypeData(ingredient: ItemStack, context: UidContext): Any? {
        return ingredient.get(component)
    }

    override fun getLegacyStringSubtypeInfo(ingredient: ItemStack, context: UidContext): String = ""
}