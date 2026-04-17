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
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.registries.Registries
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.ItemStackTemplate
import net.minecraft.world.item.crafting.CraftingInput
import net.minecraft.world.item.crafting.PlacementInfo
import net.minecraft.world.item.crafting.RecipeSerializer
import net.minecraft.world.item.crafting.ShapedRecipePattern
import net.minecraft.world.item.crafting.display.RecipeDisplay
import net.minecraft.world.item.crafting.display.SlotDisplay
import net.minecraft.world.item.crafting.display.SlotDisplay.Empty
import net.minecraft.world.level.Level
import kotlin.jvm.optionals.getOrNull

class CookingPotRecipe(
    val pattern: ShapedRecipePattern,
    override val result: ItemStackTemplate,
    override val groupName: String,
    override val category: CookingPotBookCategory,
    override val seasoningTag: TagKey<Item>,
    override val seasoningProcessors: List<SeasoningProcessor>
) : CookingPotRecipeBase {
    override fun getType() = CobblemonRecipeTypes.COOKING_POT_COOKING
    override fun getSerializer() = CobblemonRecipeSerializers.COOKING_POT_COOKING
    override fun placementInfo(): PlacementInfo = PlacementInfo.createFromOptionals(pattern.ingredients())

    override fun matches(input: CraftingInput, level: Level): Boolean {
        val filteredItems = (0..8).map { index ->
            if (index < input.size()) input.getItem(index) else ItemStack.EMPTY
        }
        return pattern.matches(CraftingInput.of(3, 3, filteredItems))
    }

    override fun display(): List<RecipeDisplay> {
        return listOf(CookingPotShapedRecipeDisplay(
            pattern.width(),
            pattern.height(),
            pattern.ingredients().map { it.getOrNull()?.display() ?: Empty.INSTANCE },
            SlotDisplay.ItemStackSlotDisplay(result),
            SlotDisplay.TagSlotDisplay(seasoningTag)
        ))
    }

    companion object {
        val CODEC: MapCodec<CookingPotRecipe> = RecordCodecBuilder.mapCodec { instance ->
            instance.group(
                ShapedRecipePattern.MAP_CODEC.forGetter { recipe -> recipe.pattern },
                ItemStackTemplate.CODEC.fieldOf("result").forGetter { recipe -> recipe.result },
                Codec.STRING.optionalFieldOf("group", "").forGetter { recipe -> recipe.group() },
                CookingPotBookCategory.CODEC.fieldOf("category").orElse(CookingPotBookCategory.MISC).forGetter { recipe -> recipe.category },
                TagKey.codec(Registries.ITEM).fieldOf("seasoningTag").orElse(CobblemonItemTags.EMPTY).forGetter { recipe -> recipe.seasoningTag },
                createByStringCodec(
                    { SeasoningProcessor.processors[it] },
                    { it.type },
                    { "Unknown seasoning processor: $it" }
                ).listOf().fieldOf("seasoningProcessors").forGetter { recipe -> recipe.seasoningProcessors }
            ).apply(instance, ::CookingPotRecipe)
        }

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, CookingPotRecipe> = StreamCodec.of(::toNetwork, ::fromNetwork)
        val SERIALIZER: RecipeSerializer<CookingPotRecipe> = RecipeSerializer(CODEC, STREAM_CODEC)

        private fun fromNetwork(buffer: RegistryFriendlyByteBuf): CookingPotRecipe {
            val group = buffer.readUtf()
            val category = buffer.readEnum(CookingPotBookCategory::class.java)
            val seasoningTag = TagKey.create(Registries.ITEM, buffer.readIdentifier())
            val pattern = ShapedRecipePattern.STREAM_CODEC.decode(buffer)
            val result = ItemStackTemplate.STREAM_CODEC.decode(buffer)
            val seasoningProcessors = buffer.readList {
                val type = buffer.readString()
                SeasoningProcessor.processors[type] ?: error("Unknown seasoning processor: $type")
            }
            return CookingPotRecipe(pattern, result, group, category, seasoningTag, seasoningProcessors)
        }

        private fun toNetwork(buffer: RegistryFriendlyByteBuf, recipe: CookingPotRecipe) {
            buffer.writeUtf(recipe.groupName)
            buffer.writeEnum(recipe.category)
            buffer.writeIdentifier(recipe.seasoningTag.location())
            ShapedRecipePattern.STREAM_CODEC.encode(buffer, recipe.pattern)
            ItemStackTemplate.STREAM_CODEC.encode(buffer, recipe.result)
            buffer.writeCollection(recipe.seasoningProcessors) { _, it ->
                buffer.writeString(it.type)
            }
        }
    }
}
