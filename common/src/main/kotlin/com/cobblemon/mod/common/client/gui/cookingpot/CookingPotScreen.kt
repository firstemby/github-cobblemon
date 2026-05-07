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
import com.cobblemon.mod.common.block.campfirepot.CookingPotResultSlot
import com.cobblemon.mod.common.block.campfirepot.SeasoningSlot
import com.cobblemon.mod.common.block.entity.CampfireBlockEntity.Companion.COOKING_POT_COLOR_INDEX
import com.cobblemon.mod.common.block.entity.CampfireBlockEntity.Companion.IS_LID_OPEN_INDEX
import com.cobblemon.mod.common.integration.jei.CobblemonJeiPlugin
import com.cobblemon.mod.common.integration.jei.cooking.CampfirePotRecipeCategory
import com.cobblemon.mod.common.net.messages.client.cooking.ToggleCookingPotLidPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.ImageButton
import net.minecraft.client.gui.navigation.ScreenPosition
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.crafting.display.RecipeDisplay
import kotlin.math.ceil

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
    }

    private val recipeBookComponent: RecipeBookComponent<CookingPotMenu> = CookingRecipeBookComponent(this.menu)
    private var widthTooNarrow: Boolean = false

    private lateinit var cookButton: CookButton

    override fun containerTick() {
        super.containerTick()
        this.recipeBookComponent.tick()
    }

    override fun init() {
        super.init()
        val topPos = ((height - BACKGROUND_HEIGHT) / 2)

        this.titleLabelX = this.imageWidth - (BACKGROUND_WIDTH / 2) - (this.font.width(this.title) / 2)
        this.titleLabelY = 6

        this.widthTooNarrow = this.width < 379
        this.recipeBookComponent.init(this.width, this.height, this.minecraft, this.widthTooNarrow)
        this.leftPos = this.recipeBookComponent.updateScreenPosition(this.width, this.imageWidth)

        if (::cookButton.isInitialized) removeWidget(cookButton)
        val color = CampfirePotColor.entries[menu.containerData.get(COOKING_POT_COLOR_INDEX).coerceIn(0, CampfirePotColor.entries.lastIndex)]
        cookButton = CookButton(this.leftPos + 97, topPos + 56, menu.containerData.get(IS_LID_OPEN_INDEX) == 0, color) {
            val isLidClosed = menu.containerData.get(IS_LID_OPEN_INDEX) == 0
            sendToServer(ToggleCookingPotLidPacket(isLidClosed))
        }

        addRenderableWidget(cookButton)
        val buttonPos = ScreenPosition(this.leftPos + 5, this.height / 2 - 49);
        this.addRenderableWidget(
            ImageButton(
                buttonPos.x(),
                buttonPos.y(),
                20,
                18,
                RecipeBookComponent.RECIPE_BUTTON_SPRITES
            ) {
                this.recipeBookComponent.toggleVisibility()
                this.leftPos = this.recipeBookComponent.updateScreenPosition(this.width, this.imageWidth)
                val updatedButtonPos = ScreenPosition(this.leftPos + 5, this.height / 2 - 49);
                it.setPosition(updatedButtonPos.x(), updatedButtonPos.y())
            }
        )
        this.addWidget(this.recipeBookComponent)
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
        if(::cookButton.isInitialized) {
            cookButton.color = CampfirePotColor.entries[menu.containerData.get(COOKING_POT_COLOR_INDEX).coerceIn(0, CampfirePotColor.entries.lastIndex)]
            cookButton.selected = menu.containerData.get(IS_LID_OPEN_INDEX) == 0
            cookButton.setPosition(this.leftPos + 97, topPos + 56)
        }

        if (this.recipeBookComponent.isVisible && this.widthTooNarrow) {
            this.extractBackground(context, mouseX, mouseY, partialTicks)
        } else {
            super.extractContents(context, mouseX, mouseY, partialTicks)
        }

        context.nextStratum()
        this.recipeBookComponent.extractRenderState(context, mouseX, mouseY, partialTicks)
        context.nextStratum()
        val resultSlot = menu.slots[menu.getResultSlotIndex()] as CookingPotResultSlot
        if (resultSlot.isPreview) {
            val resultItem = (menu.getResultSlot() as CookingPotResultSlot).item
            if (!resultItem.isEmpty) {
                context.fakeItem(resultItem, leftPos + resultSlot.x, topPos + resultSlot.y)
                context.itemDecorations(font, resultItem, leftPos + resultSlot.x, topPos + resultSlot.y)

                if (isHovering(resultSlot.x, resultSlot.y, 16, 16, mouseX.toDouble(), mouseY.toDouble()) && menu.carried.isEmpty) {
                    context.setTooltipForNextFrame(font, getTooltipFromContainerItem(resultItem), resultItem.tooltipImage, mouseX, mouseY)
                }
            }
        }

        this.extractCarriedItem(context, mouseX, mouseY)
        this.extractSnapbackItem(context)
        this.extractTooltip(context, mouseX, mouseY)
        this.recipeBookComponent.extractTooltip(context, mouseX, mouseY, null)
    }

    override fun extractSlots(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        super.extractSlots(graphics, mouseX, mouseY)
        this.recipeBookComponent.extractGhostRecipe(graphics, true)

    }

    override fun extractSlot(guiGraphics: GuiGraphicsExtractor, slot: Slot, mouseX: Int, mouseY: Int) {
        if (slot is SeasoningSlot && ((!menu.carried.isEmpty && !slot.mayPlace(menu.carried)) || (slot.hasItem() && !slot.mayPlace(slot.item))) ) {
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

            return true
        } else {
            return if (this.widthTooNarrow && this.recipeBookComponent.isVisible) true else super.mouseClicked(
                mouseButtonEvent,
                doubleClick
            )
        }
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

    override fun charTyped(event: CharacterEvent): Boolean {
        return if (this.recipeBookComponent.charTyped(event)) true else super.charTyped(event)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        return if (this.recipeBookComponent.keyPressed(event)) true else super.keyPressed(event)
    }

    override fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        return if (this.recipeBookComponent.mouseDragged(event, dx, dy)) true else super.mouseDragged(event, dx, dy)
    }

    override fun isHovering(left: Int, top: Int, w: Int, h: Int, xm: Double, ym: Double): Boolean {
        return (!this.widthTooNarrow || !this.recipeBookComponent.isVisible) && super.isHovering(
            left,
            top,
            w,
            h,
            xm,
            ym
        )
    }

    override fun hasClickedOutside(mx: Double, my: Double, xo: Int, yo: Int): Boolean {
        val clickedOutside =
            mx < xo.toDouble() || my < yo.toDouble() || mx >= (xo + this.imageWidth).toDouble() || my >= (yo + this.imageHeight).toDouble()
        return this.recipeBookComponent.hasClickedOutside(
            mx,
            my,
            this.leftPos,
            this.topPos,
            this.imageWidth,
            this.imageHeight
        ) && clickedOutside
    }
}
