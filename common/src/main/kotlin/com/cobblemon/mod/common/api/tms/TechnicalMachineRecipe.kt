/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.tms

import net.minecraft.world.item.crafting.Ingredient

/**
 * Represents the ingredients necessary to craft a [TechnicalMachine] in the [TMBlock]
 */
data class TechnicalMachineRecipe(
    val ingredient: Ingredient,
    val count: Int
)
