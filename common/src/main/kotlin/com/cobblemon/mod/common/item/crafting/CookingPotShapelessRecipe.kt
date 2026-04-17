/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.item.crafting

import com.cobblemon.mod.common.CobblemonRecipeSerializers
import com.cobblemon.mod.common.CobblemonRecipeTypes
import com.cobblemon.mod.common.api.tags.CobblemonItemTags
import com.cobblemon.mod.common.util.codec.CodecUtils.createByStringCodec
import com.cobblemon.mod.common.util.readString
import com.cobblemon.mod.common.util.writeString
import com.mojang.serialization.Codec
import com.mojang.serialization.DataResult
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.registries.Registries
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStackTemplate
import net.minecraft.world.item.crafting.CraftingInput
import net.minecraft.world.item.crafting.Ingredient
import net.minecraft.world.item.crafting.PlacementInfo
import net.minecraft.world.item.crafting.RecipeSerializer
import net.minecraft.world.item.crafting.RecipeType
import net.minecraft.world.item.crafting.display.RecipeDisplay
import net.minecraft.world.item.crafting.display.SlotDisplay
import net.minecraft.world.level.Level

class CookingPotShapelessRecipe(
    override val groupName: String,
    override val category: CookingPotBookCategory,
    override val result: ItemStackTemplate,
    private val ingredients: List<Ingredient>,
    override val seasoningTag: TagKey<Item>,
    override val seasoningProcessors: List<SeasoningProcessor>
) : CookingPotRecipeBase {
    override fun getType(): RecipeType<CookingPotShapelessRecipe> = CobblemonRecipeTypes.COOKING_POT_SHAPELESS
    override fun getSerializer(): RecipeSerializer<CookingPotShapelessRecipe> = CobblemonRecipeSerializers.COOKING_POT_SHAPELESS
    override fun placementInfo(): PlacementInfo = PlacementInfo.create(ingredients)

    override fun matches(input: CraftingInput, level: Level): Boolean {
        val remaining = ingredients.toMutableList()

        for (item in input.items()) {
            if (item.isEmpty) continue

            val matchIndex = remaining.indexOfFirst { it.test(item) }
            if (matchIndex != -1) {
                remaining.removeAt(matchIndex)
            } else {
                return false
            }
        }

        return remaining.isEmpty()
    }

    override fun display(): List<RecipeDisplay> {
        return listOf(CookingPotShapelessRecipeDisplay(
            ingredients.map { it.display() },
            SlotDisplay.ItemStackSlotDisplay(result),
            SlotDisplay.TagSlotDisplay(seasoningTag)
        ))
    }

    companion object {
        private val CODEC: MapCodec<CookingPotShapelessRecipe> = RecordCodecBuilder.mapCodec { instance ->
            instance.group(
                Codec.STRING.optionalFieldOf("group", "").forGetter { it.group() },
                CookingPotBookCategory.CODEC.fieldOf("category").orElse(CookingPotBookCategory.MISC).forGetter { it.category },
                ItemStackTemplate.CODEC.fieldOf("result").forGetter { it.result },
                Ingredient.CODEC.listOf().fieldOf("ingredients").flatXmap({ list ->
                    when {
                        list.isEmpty() -> DataResult.error { "No ingredients for shapeless recipe" }
                        list.size > 9 -> DataResult.error { "Too many ingredients for shapeless recipe" }
                        else -> DataResult.success(list)
                    }
                }, { DataResult.success(it) }).forGetter { it.ingredients },
                TagKey.codec(Registries.ITEM).fieldOf("seasoningTag").orElse(CobblemonItemTags.EMPTY).forGetter { recipe -> recipe.seasoningTag },
                createByStringCodec(
                    { SeasoningProcessor.processors[it] },
                    { it.type },
                    { "Unknown seasoning processor: $it" }
                ).listOf().fieldOf("seasoningProcessors").forGetter { recipe -> recipe.seasoningProcessors }
            ).apply(instance, ::CookingPotShapelessRecipe)
        }

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, CookingPotShapelessRecipe> =
            StreamCodec.of(::toNetwork, ::fromNetwork)
        val SERIALIZER: RecipeSerializer<CookingPotShapelessRecipe> = RecipeSerializer(CODEC, STREAM_CODEC)

        private fun fromNetwork(buffer: RegistryFriendlyByteBuf): CookingPotShapelessRecipe {
            val group = buffer.readUtf()
            val category = buffer.readEnum(CookingPotBookCategory::class.java)
            val seasoningTag = TagKey.create(Registries.ITEM, buffer.readIdentifier())
            val size = buffer.readVarInt()
            val ingredients = List(size) { Ingredient.CONTENTS_STREAM_CODEC.decode(buffer) }
            val result = ItemStackTemplate.STREAM_CODEC.decode(buffer)
            val seasoningProcessors = buffer.readList {
                val type = buffer.readString()
                SeasoningProcessor.processors[type] ?: error("Unknown seasoning processor: $type")
            }
            return CookingPotShapelessRecipe(group, category, result, ingredients, seasoningTag, seasoningProcessors)
        }

        private fun toNetwork(buffer: RegistryFriendlyByteBuf, recipe: CookingPotShapelessRecipe) {
            buffer.writeUtf(recipe.group())
            buffer.writeEnum(recipe.category)
            buffer.writeIdentifier(recipe.seasoningTag.location())
            buffer.writeVarInt(recipe.ingredients.size)
            recipe.ingredients.forEach { ingredient ->
                Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, ingredient)
            }
            ItemStackTemplate.STREAM_CODEC.encode(buffer, recipe.result)
            buffer.writeCollection(recipe.seasoningProcessors) { _, it ->
                buffer.writeString(it.type)
            }
        }
    }
}
