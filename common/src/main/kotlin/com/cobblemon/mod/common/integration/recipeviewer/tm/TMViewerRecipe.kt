package com.cobblemon.mod.common.integration.recipeviewer.tm

import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.api.text.red
import com.cobblemon.mod.common.api.tms.TechnicalMachine
import com.cobblemon.mod.common.api.tms.TechnicalMachines
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.integration.recipeviewer.RecipeIngredient
import com.cobblemon.mod.common.integration.recipeviewer.RecipeSlot
import com.cobblemon.mod.common.integration.recipeviewer.RecipeViewerCategoryProvider
import com.cobblemon.mod.common.integration.recipeviewer.RecipeViewerRecipe
import com.cobblemon.mod.common.integration.recipeviewer.SlotType
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.lang
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.Ingredient

class TMViewerRecipe(val originalTM: TechnicalMachine): RecipeViewerRecipe {
    override fun getId(): ResourceLocation = cobblemonResource("/tm_recipe_" + originalTM.id.path)

    override fun getInputs(): List<RecipeIngredient> {
        val items = mutableListOf(RecipeIngredient(Ingredient.of(CobblemonItems.BLANK_TM)))
        items.addAll(originalTM.recipe?.map { RecipeIngredient(it.ingredient, it.count) } ?: listOf())
        return items
    }

    override fun getOutputs(): List<ItemStack> {
         return listOf(originalTM.createItemStack())
    }

    override fun getSlots(): List<RecipeSlot> {
        val slots = mutableListOf<RecipeSlot>()
        val recipe = originalTM.recipe ?: listOf()

        slots.add(RecipeSlot(0, 0, SlotType.INPUT, RecipeIngredient(Ingredient.of(CobblemonItems.BLANK_TM))))

        for (i in 0..2) {
            val ingredient = recipe.getOrNull(i)?.let { RecipeIngredient(it.ingredient, it.count) } ?: RecipeIngredient(Ingredient.of())
            slots.add(RecipeSlot(17 + i * 17, 0, SlotType.INPUT, ingredient))
        }

        slots.add(RecipeSlot(0, 20, SlotType.OUTPUT, RecipeIngredient(Ingredient.of(originalTM.createItemStack()))))

        return slots
    }

    override fun renderRecipe(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        // TODO: Make and Render Background, also tooltip

        drawScaledText(
            context = context,
            text = if (recipeKnown()) lang("recipeviewer.tm_learned") else lang("recipeviewer.tm_not_learned").red(),
            x = 2,
            y = 30,
            shadow = true
        )
    }

    fun recipeKnown(): Boolean {
        val learnedTMs = CobblemonClient.clientTMMoveData.learnedTMs
        return originalTM.isPassivelyObtained() || originalTM.id in learnedTMs
    }
}