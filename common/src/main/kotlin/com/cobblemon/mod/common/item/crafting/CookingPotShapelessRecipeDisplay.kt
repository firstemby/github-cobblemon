package com.cobblemon.mod.common.item.crafting

import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.CobblemonRecipeDisplayTypes
import com.mojang.datafixers.util.Function5
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.flag.FeatureFlagSet
import net.minecraft.world.item.crafting.display.RecipeDisplay
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay
import net.minecraft.world.item.crafting.display.SlotDisplay
import java.util.function.Function

class CookingPotShapelessRecipeDisplay(
    val ingredients: List<SlotDisplay>,
    val resultField: SlotDisplay,
    val seasonings: SlotDisplay
) : RecipeDisplay {

    companion object {
        val CODEC: MapCodec<CookingPotShapelessRecipeDisplay> =
            RecordCodecBuilder.mapCodec {
                it.group(
                    SlotDisplay.CODEC.listOf().fieldOf("ingredients")
                        .forGetter(CookingPotShapelessRecipeDisplay::ingredients),
                    SlotDisplay.CODEC.fieldOf("result").forGetter(CookingPotShapelessRecipeDisplay::resultField),
                    SlotDisplay.CODEC.fieldOf("seasonings").forGetter(CookingPotShapelessRecipeDisplay::seasonings)
                ).apply(it, ::CookingPotShapelessRecipeDisplay)
            }

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, CookingPotShapelessRecipeDisplay> = StreamCodec.composite(
            SlotDisplay.STREAM_CODEC.apply(ByteBufCodecs.list()),
            CookingPotShapelessRecipeDisplay::ingredients,
            SlotDisplay.STREAM_CODEC,
            CookingPotShapelessRecipeDisplay::resultField,
            SlotDisplay.STREAM_CODEC,
            CookingPotShapelessRecipeDisplay::seasonings,
            ::CookingPotShapelessRecipeDisplay
        )
    }

    override fun result() = resultField

    override fun craftingStation() = SlotDisplay.ItemSlotDisplay(CobblemonItems.CAMPFIRE_POT_RED)

    override fun type() = CobblemonRecipeDisplayTypes.COOKING_SHAPELESS

    override fun isEnabled(enabledFeatures: FeatureFlagSet): Boolean {
        return this.ingredients.stream().allMatch { it.isEnabled(enabledFeatures) } && super.isEnabled(enabledFeatures)
    }
}