package com.cobblemon.mod.common.integration.recipeviewer.berry

import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.integration.recipeviewer.RecipeViewerCategoryProvider
import com.cobblemon.mod.common.item.berry.BerryItem
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.lang
import mezz.jei.api.recipe.RecipeType
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.Ingredient
import net.minecraft.world.level.ItemLike
import org.joml.Vector2i

class BerryMutationProvider: RecipeViewerCategoryProvider<BerryMutationRecipe> {
    override val id = ID
    override val recipeClass = BerryMutationRecipe::class.java

    override fun getIconResource(): ResourceLocation = cobblemonResource("textures/item/mulches/surprise_mulch.png")
    override fun getIconDimensions(): Vector2i = Vector2i(16, 16)

    override fun getWidth(): Int = WIDTH
    override fun getHeight(): Int = HEIGHT

    override fun getName(): Component = lang("recipeviewer.category.berry_mutation")

    override fun getRecipes(): List<BerryMutationRecipe> {
        val berryList = CobblemonItems.berries().map { it.value }
        val berryMutations = mutableSetOf<Triple<BerryItem, BerryItem, BerryItem>>()
        berryList.forEach {berry ->
            for (mut in berry.berry()?.mutations?.entries ?: emptySet()) {
                val berryTwo = CobblemonItems.berries()[mut.key] ?: continue
                val berryThree = CobblemonItems.berries()[mut.value] ?: continue
                val mutation = Triple(berry, berryTwo, berryThree)
                val altMutation = Triple(berryTwo, berry, berryThree)
                if (!(berryMutations.contains(mutation) || berryMutations.contains(altMutation))) {
                    berryMutations.add(mutation)
                }
            }
        }
        return berryMutations.map { values ->
            BerryMutationRecipe(values.first, values.second, values.third) }
    }

    override fun getWorkstations(): List<ItemStack> {
        return emptyList()
    }

    companion object {
        private const val WIDTH = 125
        private const val HEIGHT = 18

        val ID = cobblemonResource("berry_recipe")
    }
}