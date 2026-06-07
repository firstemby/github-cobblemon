package com.cobblemon.mod.common.integration.recipeviewer.tm

import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.api.tms.TechnicalMachine
import com.cobblemon.mod.common.integration.recipeviewer.RecipeViewerCategoryProvider
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import org.joml.Vector2i

class TMViewerProvider: RecipeViewerCategoryProvider<TMViewerRecipe> {
    override val id = ID
    override val recipeClass = TMViewerRecipe::class.java

    override fun getIconResource(): ResourceLocation = cobblemonResource("textures/item/tms/blank_disc.png")
    override fun getIconDimensions(): Vector2i = Vector2i(16, 16)

    override fun getWidth(): Int = WIDTH
    override fun getHeight(): Int = HEIGHT

    override fun getRecipes(): List<TMViewerRecipe> {
        return TechnicalMachine.filterTms(null, null, null, null, true).map { TMViewerRecipe(it) }
    }

    override fun getWorkstations(): List<ItemStack> {
        return listOf(CobblemonItems.TM_MACHINE.defaultInstance)
    }

    companion object {
        private const val WIDTH = 146
        private const val HEIGHT = 59

        val ID = cobblemonResource("tm_recipe")
    }
}