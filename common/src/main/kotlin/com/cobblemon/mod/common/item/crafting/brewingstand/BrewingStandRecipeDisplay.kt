package com.cobblemon.mod.common.item.crafting.brewingstand

import com.cobblemon.mod.common.CobblemonRecipeDisplayTypes
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.item.crafting.display.RecipeDisplay
import net.minecraft.world.item.crafting.display.SlotDisplay

class BrewingStandRecipeDisplay(var bottles: SlotDisplay,
                                       var inputItem: SlotDisplay,
                                       var resultDisplay: SlotDisplay, // Implements RecipeDisplay#result
                                       var craftingStationDisplay: SlotDisplay) // Implements RecipeDisplay#craftingStation)
     : RecipeDisplay {
    override fun result() = this.resultDisplay

    override fun craftingStation() = this.craftingStationDisplay

    override fun type(): RecipeDisplay.Type<out RecipeDisplay> = CobblemonRecipeDisplayTypes.BREWING

    companion object {
        val CODEC = RecordCodecBuilder.mapCodec { instance ->
            instance.group(
                SlotDisplay.CODEC.fieldOf("inputState").forGetter(BrewingStandRecipeDisplay::bottles),
                SlotDisplay.CODEC.fieldOf("inputState").forGetter(BrewingStandRecipeDisplay::inputItem),
                SlotDisplay.CODEC.fieldOf("result").forGetter(BrewingStandRecipeDisplay::resultDisplay),
                SlotDisplay.CODEC.fieldOf("crafting_station").forGetter(BrewingStandRecipeDisplay::craftingStationDisplay)
            ).apply(instance, ::BrewingStandRecipeDisplay)
        }

        val STREAM_CODEC = StreamCodec.composite(
            SlotDisplay.STREAM_CODEC,
            BrewingStandRecipeDisplay::bottles,
            SlotDisplay.STREAM_CODEC,
            BrewingStandRecipeDisplay::inputItem,
            SlotDisplay.STREAM_CODEC,
            BrewingStandRecipeDisplay::result,
            SlotDisplay.STREAM_CODEC,
            BrewingStandRecipeDisplay::craftingStation,
            ::BrewingStandRecipeDisplay
        )
    }
}