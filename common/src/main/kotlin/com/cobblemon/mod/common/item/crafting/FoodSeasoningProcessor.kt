/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.item.crafting

import com.cobblemon.mod.common.CobblemonItemComponents
import com.cobblemon.mod.common.api.cooking.FoodUtils
import com.cobblemon.mod.common.api.cooking.MobEffectUtils
import com.cobblemon.mod.common.api.cooking.Seasonings
import com.cobblemon.mod.common.item.components.FoodComponent
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack
import net.minecraft.world.effect.MobEffectInstance

object FoodSeasoningProcessor : SeasoningProcessor {
    override val type = "food"

    override fun apply(result: ItemStack, seasoning: List<ItemStack>) {
        val foodData = result.get(DataComponents.FOOD)
        val baseHunger = foodData?.nutrition ?: 0
        val baseSaturation = foodData?.saturation ?: 0f

        val foodComponents = seasoning.mapNotNull {
            Seasonings.getFromItemStack(it)?.food
        }

        val mergedFood = FoodUtils.merge(
            foodComponents.map { it.toComponent() },
            extraHunger = baseHunger,
            extraSaturation = baseSaturation
        )

        val allEffects = foodComponents
            .mapNotNull { it.mobEffects }
            .flatMap { it }

        val mergedEffects = if (allEffects.isNotEmpty()) {
            MobEffectUtils
                .mergeEffects(allEffects)
                .map { it.toInstance() }
        } else null

        val finalComponent = if (mergedEffects != null && mergedEffects.isNotEmpty()) {
            FoodComponent(mergedFood.hunger, mergedFood.saturation, mergedEffects)
        } else {
            mergedFood
        }

        result.set(CobblemonItemComponents.FOOD, finalComponent)
    }


    override fun consumesItem(seasoning: ItemStack): Boolean {
        val seasoningData = Seasonings.getFromItemStack(seasoning)
        return seasoningData?.food != null
    }
}