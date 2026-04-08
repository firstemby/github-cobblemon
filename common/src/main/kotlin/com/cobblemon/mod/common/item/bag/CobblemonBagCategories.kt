/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.item.bag

import com.cobblemon.mod.common.api.tags.CobblemonItemTags
import net.minecraft.tags.ItemTags
import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item

object CobblemonBagCategories {
    val categories = mutableListOf<Category>(
        Category("ball", "ball", mutableListOf(CobblemonItemTags.POKE_BALLS), 256)
    )

    data class Category(
        val category: String,
        val categoryName: String,
        val filters: MutableList<TagKey<Item>>,
        val maxCount: Int
    )
}