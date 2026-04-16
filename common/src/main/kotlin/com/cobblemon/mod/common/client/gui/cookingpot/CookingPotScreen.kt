/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.cookingpot

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonNetwork.sendToServer
import com.cobblemon.mod.common.block.campfirepot.CampfirePotColor
import com.cobblemon.mod.common.block.campfirepot.CookingPotMenu
import com.cobblemon.mod.common.block.campfirepot.SeasoningSlot
import com.cobblemon.mod.common.block.entity.CampfireBlockEntity.Companion.COOKING_POT_COLOR_INDEX
import com.cobblemon.mod.common.block.entity.CampfireBlockEntity.Companion.IS_LID_OPEN_INDEX
import com.cobblemon.mod.common.integration.jei.CobblemonJeiPlugin
import com.cobblemon.mod.common.integration.jei.cooking.CampfirePotRecipeCategory
import com.cobblemon.mod.common.mixin.accessor.RecipeBookComponentAccessor
import com.cobblemon.mod.common.net.messages.client.cooking.ToggleCookingPotLidPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.CycleButton
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.components.WidgetSprites
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.crafting.display.RecipeDisplay
import kotlin.math.ceil

@Environment(EnvType.CLIENT)
class CookingPotScreen(
    menu: CookingPotMenu,
    playerInventory: Inventory,
    title: Component
) : AbstractContainerScreen<CookingPotMenu>(
    menu,
    playerInventory,
    Component.translatable("cobblemon.container.campfire_pot")
), RecipeUpdateListener {

    companion object {
        private const val BACKGROUND_HEIGHT = 166
        private const val BACKGROUND_WIDTH = 176

        const val COOK_PROGRESS_HEIGHT = 12
        const val COOK_PROGRESS_WIDTH = 22

        private val BACKGROUND = cobblemonResource("textures/gui/campfirepot/campfire_pot.png")
        val COOK_PROGRESS_SPRITE: Identifier = cobblemonResource("textures/gui/campfirepot/cook_progress.png")
        private val FILTER_BUTTON_SPRITES = WidgetSprites(
            Identifier.withDefaultNamespace("recipe_book/campfire_pot_filter_enabled"),
            Identifier.withDefaultNamespace("recipe_book/campfire_pot_filter_disabled"),
            Identifier.withDefaultNamespace("recipe_book/campfire_pot_filter_enabled_highlighted"),
            Identifier.withDefaultNamespace("recipe_book/campfire_pot_filter_disabled_highlighted")
        )
    }
    //TODO IMPLEMENT OWN RECIPEBOOKCOMPONENT
    private val recipeBookComponent: RecipeBookComponent<CookingPotMenu> = CookingRecipeBookComponent(this.menu)
    private var widthTooNarrow: Boolean = false

    private lateinit var cookButton: CookButton

    override fun containerTick() {
        super.containerTick()
    }

    override fun init() {
        super.init()
        val topPos = ((height - BACKGROUND_HEIGHT) / 2)

        this.titleLabelX = this.imageWidth - (BACKGROUND_WIDTH / 2) - (this.font.width(this.title) / 2)
        this.titleLabelY = 6

        if (::cookButton.isInitialized) removeWidget(cookButton)
        val color = CampfirePotColor.entries[menu.containerData.get(COOKING_POT_COLOR_INDEX).coerceIn(0, CampfirePotColor.entries.lastIndex)]
        cookButton = CookButton(this.leftPos + 97, topPos + 56, menu.containerData.get(IS_LID_OPEN_INDEX) == 0, color) {
            val isLidClosed = menu.containerData.get(IS_LID_OPEN_INDEX) == 0
            sendToServer(ToggleCookingPotLidPacket(isLidClosed))
        }

        addRenderableWidget(cookButton)
    }

    override fun extractLabels(graphics: GuiGraphicsExtractor, xm: Int, ym: Int){
        graphics.text(this.font, this.title, this.titleLabelX, this.titleLabelY, 4210752, false)
    }

    override fun extractBackground(
        context: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float
    ) {
        context.blit(
            RenderPipelines.GUI_TEXTURED,
            BACKGROUND,
            leftPos,
            (height - BACKGROUND_HEIGHT) / 2,
            0f,
            0f,
            BACKGROUND_WIDTH,
            BACKGROUND_HEIGHT,
            BACKGROUND_WIDTH,
            BACKGROUND_HEIGHT
        )

        val cookProgress = ceil(menu.getBurnProgress() * COOK_PROGRESS_WIDTH).toInt()
        context.blit(
            RenderPipelines.GUI_TEXTURED,
            COOK_PROGRESS_SPRITE,
            leftPos + 96,
            topPos + 39,
            0f,
            0f,
            cookProgress,
            COOK_PROGRESS_HEIGHT,
            COOK_PROGRESS_WIDTH,
            COOK_PROGRESS_HEIGHT
        )
    }

    override fun extractRenderState(
        context: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        partialTicks: Float
    ) {
        cookButton.color = CampfirePotColor.entries[menu.containerData.get(COOKING_POT_COLOR_INDEX).coerceIn(0, CampfirePotColor.entries.lastIndex)]
        cookButton.selected = menu.containerData.get(IS_LID_OPEN_INDEX) == 0
        cookButton.setPosition(this.leftPos + 97, topPos + 56)

        if (this.recipeBookComponent.isVisible && this.widthTooNarrow) {
            this.extractBackground(context, mouseX, mouseY, partialTicks)
            this.recipeBookComponent.extractRenderState(context, mouseX, mouseY, partialTicks)
        } else {
            super.extractRenderState(context, mouseX, mouseY, partialTicks)
            this.recipeBookComponent.extractRenderState(context, mouseX, mouseY, partialTicks)
            this.recipeBookComponent.extractGhostRecipe(context, false)
        }

        val resultSlot = menu.slots[menu.getResultSlotIndex()]
        val optionalRecipe = menu.currentActiveRecipe
        if (optionalRecipe != null) {
            val resultItem = menu.previewItem
            val recipeResult = optionalRecipe.value.result.create()
            if (!recipeResult.isEmpty && !resultSlot.hasItem()) {
                context.fakeItem(resultItem, leftPos + resultSlot.x, topPos + resultSlot.y)
                context.itemDecorations(font, resultItem, leftPos + resultSlot.x, topPos + resultSlot.y)

                if (isHovering(resultSlot.x, resultSlot.y, 16, 16, mouseX.toDouble(), mouseY.toDouble()) && menu.getCarried().isEmpty()) {
                    context.setTooltipForNextFrame(font, getTooltipFromContainerItem(resultItem), resultItem.tooltipImage, mouseX, mouseY)
                }
            }
        }

        this.extractTooltip(context, mouseX, mouseY)
        this.recipeBookComponent.extractTooltip(context, mouseX, mouseY, null)
    }

    override fun extractSlot(guiGraphics: GuiGraphicsExtractor, slot: Slot, mouseX: Int, mouseY: Int) {
        if (slot is SeasoningSlot && ((!menu.getCarried().isEmpty() && !slot.mayPlace(menu.getCarried())) || (slot.hasItem() && !slot.mayPlace(slot.item))) ) {
            val x = slot.x
            val y = slot.y
            guiGraphics.fill(x,y,x+16,y+16,822018048)
        }
        super.extractSlot(guiGraphics, slot, mouseX, mouseY)
    }

    override fun recipesUpdated() {
        this.recipeBookComponent.recipesUpdated()
    }


    override fun fillGhostRecipe(display: RecipeDisplay) {
        this.recipeBookComponent.fillGhostRecipe(display)
    }

    override fun mouseClicked(mouseButtonEvent: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val progressX = leftPos + 96
        val progressY = topPos + 39
        if (Cobblemon.implementation.isModInstalled("jei") &&
            mouseButtonEvent.x() >= progressX && mouseButtonEvent.x() < progressX + COOK_PROGRESS_WIDTH &&
            mouseButtonEvent.y() >= progressY && mouseButtonEvent.y() < progressY + COOK_PROGRESS_HEIGHT
        ) {
            CobblemonJeiPlugin.jeiRuntime?.recipesGui?.showTypes(
                listOf(CampfirePotRecipeCategory.RECIPE_TYPE)
            )
            return true
        }

        if (this.recipeBookComponent.mouseClicked(mouseButtonEvent, doubleClick)) {
            this.focused = this.recipeBookComponent

            if (this.recipeBookComponent.isVisible) {
                val recipeBookFilterButton = (this.recipeBookComponent as RecipeBookComponentAccessor).filterButton
                if (recipeBookFilterButton.isMouseOver(mouseButtonEvent.x(), mouseButtonEvent.y())) {}
                    //overrideRecipeBookFilterButton(recipeBookFilterButton) -- This is likely not needed as we implement our own bookComponent
            }

            return true
        } else {
            return if (this.widthTooNarrow && this.recipeBookComponent.isVisible) true else super.mouseClicked(
                mouseButtonEvent,
                doubleClick
            )
        }

        return super.mouseClicked(mouseButtonEvent, doubleClick)
    }

    override fun extractTooltip(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val progressX = leftPos + 96
        val progressY = topPos + 39
        if (Cobblemon.implementation.isModInstalled("jei") &&
            mouseX >= progressX && mouseX < progressX + COOK_PROGRESS_WIDTH &&
            mouseY >= progressY && mouseY < progressY + COOK_PROGRESS_HEIGHT
        ) {
            graphics.setTooltipForNextFrame(this.font, Component.translatable("jei.tooltip.show.recipes"), mouseX, mouseY)
            return
        }
        super.extractTooltip(graphics, mouseX, mouseY)
    }

    // Java nullability with kotlin is my greatest enemy, it'll complain but this works
    override fun slotClicked(slot: Slot, slotId: Int, mouseButton: Int, containerInput: ContainerInput) {
        super.slotClicked(slot, slotId, mouseButton, containerInput)
        recipeBookComponent.slotClicked(slot)
    }
}
