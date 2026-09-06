/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.tms

import com.google.gson.annotations.SerializedName
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.world.item.crafting.Ingredient

/**
 * DTO used for [TechnicalMachineRecipe] JSON loading because Gson cannot deserialize Minecraft Ingredients.
 */
data class TechnicalMachineRecipeDTO(
    val item: ResourceLocation?,
    val tag: ResourceLocation?,
    @SerializedName("count") private val _count: Int?,
) {
    private val count: Int
        get() = _count ?: 1

    fun toTechnicalMachineRecipe(): TechnicalMachineRecipe {
        val ingredient = when {
            item != null ->
                Ingredient.of(
                    BuiltInRegistries.ITEM.get(item)
                )

            tag != null ->
                Ingredient.of(
                    TagKey.create(Registries.ITEM, tag)
                )

            else ->
                error("Recipe entry must define either 'item' or 'tag'")
        }

        return TechnicalMachineRecipe(
            ingredient = ingredient,
            count = count
        )
    }
}