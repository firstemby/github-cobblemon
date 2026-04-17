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

class CookingPotShapedRecipeDisplay(
    val width: Int,
    val height: Int,
    val ingredients: List<SlotDisplay>,
    val resultField: SlotDisplay,
    val seasonings: SlotDisplay
) : RecipeDisplay {

    companion object {
        val CODEC: MapCodec<CookingPotShapedRecipeDisplay> =
            RecordCodecBuilder.mapCodec {
                it.group(
                    Codec.INT.fieldOf("width").forGetter(CookingPotShapedRecipeDisplay::width),
                    Codec.INT.fieldOf("height").forGetter(CookingPotShapedRecipeDisplay::height),
                    SlotDisplay.CODEC.listOf().fieldOf("ingredients")
                        .forGetter(CookingPotShapedRecipeDisplay::ingredients),
                    SlotDisplay.CODEC.fieldOf("result").forGetter(CookingPotShapedRecipeDisplay::resultField),
                    SlotDisplay.CODEC.fieldOf("seasonings").forGetter(CookingPotShapedRecipeDisplay::seasonings)
                ).apply(it, ::CookingPotShapedRecipeDisplay)
            }

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, CookingPotShapedRecipeDisplay> = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            CookingPotShapedRecipeDisplay::width,
            ByteBufCodecs.VAR_INT,
            CookingPotShapedRecipeDisplay::height,
            SlotDisplay.STREAM_CODEC.apply(ByteBufCodecs.list()),
            CookingPotShapedRecipeDisplay::ingredients,
            SlotDisplay.STREAM_CODEC,
            CookingPotShapedRecipeDisplay::resultField,
            SlotDisplay.STREAM_CODEC,
            CookingPotShapedRecipeDisplay::seasonings,
            ::CookingPotShapedRecipeDisplay
        )
    }

    override fun result() = resultField

    override fun craftingStation() = SlotDisplay.ItemSlotDisplay(CobblemonItems.CAMPFIRE_POT_RED)

    override fun type() = CobblemonRecipeDisplayTypes.COOKING_SHAPED

    override fun isEnabled(enabledFeatures: FeatureFlagSet): Boolean {
        return this.ingredients.stream().allMatch { it.isEnabled(enabledFeatures) } && super.isEnabled(enabledFeatures)
    }
}