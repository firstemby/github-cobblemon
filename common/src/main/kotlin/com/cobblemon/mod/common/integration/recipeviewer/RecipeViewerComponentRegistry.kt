/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.integration.recipeviewer

import com.cobblemon.mod.common.CobblemonItemComponents
import com.cobblemon.mod.common.CobblemonItems
import net.minecraft.core.component.DataComponentType
import net.minecraft.world.item.Item

object RecipeViewerComponentRegistry {
    fun register(method: (item: Item, component: DataComponentType<*>) -> Unit) {
        method(CobblemonItems.TECHNICAL_MACHINE, CobblemonItemComponents.TM_MOVE)
    }
}