/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common

import com.cobblemon.mod.common.item.crafting.CookingPotShapedRecipeDisplay
import com.cobblemon.mod.common.item.crafting.CookingPotShapelessRecipeDisplay
import com.cobblemon.mod.common.item.crafting.brewingstand.BrewingStandRecipeDisplay
import com.cobblemon.mod.common.platform.PlatformRegistry
import com.mojang.serialization.MapCodec
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.crafting.display.RecipeDisplay

object CobblemonRecipeDisplayTypes : PlatformRegistry<Registry<RecipeDisplay.Type<*>>, ResourceKey<Registry<RecipeDisplay.Type<*>>>, RecipeDisplay.Type<*>>() {

    val BREWING = register("brewing", BrewingStandRecipeDisplay.CODEC, BrewingStandRecipeDisplay.STREAM_CODEC)
    val COOKING_SHAPED = register("cooking_shaped", CookingPotShapedRecipeDisplay.CODEC, CookingPotShapedRecipeDisplay.STREAM_CODEC)
    val COOKING_SHAPELESS = register("cooking_shapeless", CookingPotShapelessRecipeDisplay.CODEC, CookingPotShapelessRecipeDisplay.STREAM_CODEC)

    fun <T : RecipeDisplay>register(
        identifier: String,
        mapCodec: MapCodec<T>,
        streamCodec: StreamCodec<RegistryFriendlyByteBuf, T>
    ): RecipeDisplay.Type<T> {
        return Registry.register(
            BuiltInRegistries.RECIPE_DISPLAY,
            identifier,
            RecipeDisplay.Type(mapCodec, streamCodec)
        )
    }

    override val registry: Registry<RecipeDisplay.Type<*>>
        get() = BuiltInRegistries.RECIPE_DISPLAY
    override val resourceKey: ResourceKey<Registry<RecipeDisplay.Type<*>>>
        get() = Registries.RECIPE_DISPLAY
}
