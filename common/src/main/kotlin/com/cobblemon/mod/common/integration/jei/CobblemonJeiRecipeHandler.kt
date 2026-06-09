package com.cobblemon.mod.common.integration.jei

import com.cobblemon.mod.common.integration.recipeviewer.RecipeViewerRecipe
import com.cobblemon.mod.common.integration.recipeviewer.RecipeViewerRecipeHandler
import mezz.jei.api.gui.ingredient.IRecipeSlotsView
import mezz.jei.api.recipe.RecipeType
import mezz.jei.api.recipe.transfer.IRecipeTransferError
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.MenuType
import java.util.Optional

class CobblemonJeiRecipeHandler<E: AbstractContainerMenu, R: RecipeViewerRecipe>(val provider: CobblemonJeiProvider<R>, val originalHandler: RecipeViewerRecipeHandler<R, E>): IRecipeTransferHandler<E, R> {
    override fun getContainerClass(): Class<out E> = originalHandler.getMenuClass()
    override fun getMenuType(): Optional<MenuType<E>> = Optional.of(originalHandler.getMenuType())
    override fun getRecipeType(): RecipeType<R> = provider.recipeType

    override fun transferRecipe(
        container: E,
        recipe: R,
        recipeSlots: IRecipeSlotsView,
        player: Player,
        maxTransfer: Boolean,
        doTransfer: Boolean
    ): IRecipeTransferError? {
        originalHandler.additionalHandle(recipe)
        return null
    }
}