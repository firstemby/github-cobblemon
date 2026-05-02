package com.cobblemon.mod.common.integration.rei.brewing

import com.cobblemon.mod.common.item.crafting.brewingstand.BrewingStandRecipe
import me.shedaniel.rei.plugin.common.displays.brewing.DefaultBrewingDisplay
import net.minecraft.resources.ResourceLocation
import java.util.Optional

class ReiCobblemonBrewingDisplay(
    val recipe: BrewingStandRecipe,
    val id: ResourceLocation
): DefaultBrewingDisplay(
    recipe.bottle,
    recipe.input,
    recipe.result
) {
    override fun getDisplayLocation(): Optional<ResourceLocation> = Optional.of(id)
}