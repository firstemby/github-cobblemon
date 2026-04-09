/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.item.bag

import com.cobblemon.mod.common.api.tags.CobblemonItemTags
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.ItemTags
import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item

object CobblemonBagCategories {
    val categories = mutableListOf<Category>(
        Category("ball", mutableListOf(CobblemonItemTags.POKE_BALLS), 256, cobblemonResource("textures/item/poke_balls/poke_ball.png")),
        Category("gems", mutableListOf(CobblemonItemTags.TYPE_GEMS), 256, cobblemonResource("textures/item/type_gem/electric_gem.png")),
        Category("gems", mutableListOf(CobblemonItemTags.TYPE_GEMS), 256, cobblemonResource("textures/item/type_gem/fire_gem.png")),
        Category("gems", mutableListOf(CobblemonItemTags.TYPE_GEMS), 256, cobblemonResource("textures/item/type_gem/water_gem.png")),
        Category("gems", mutableListOf(CobblemonItemTags.TYPE_GEMS), 256, cobblemonResource("textures/item/type_gem/fairy_gem.png")),
        Category("gems", mutableListOf(CobblemonItemTags.TYPE_GEMS), 256, cobblemonResource("textures/item/type_gem/fighting_gem.png")),
        Category("gems", mutableListOf(CobblemonItemTags.TYPE_GEMS), 256, cobblemonResource("textures/item/type_gem/grass_gem.png")),
        Category("gems", mutableListOf(CobblemonItemTags.TYPE_GEMS), 256, cobblemonResource("textures/item/type_gem/ghost_gem.png")),
        Category("gems", mutableListOf(CobblemonItemTags.TYPE_GEMS), 256, cobblemonResource("textures/item/type_gem/bug_gem.png")),
    )

    data class Category(
        val categoryName: String,
        val filters: MutableList<TagKey<Item>>,
        val maxCount: Int,
        val icon: ResourceLocation
    )
}