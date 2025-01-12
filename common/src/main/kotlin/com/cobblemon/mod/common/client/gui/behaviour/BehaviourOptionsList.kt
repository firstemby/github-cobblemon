/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.behaviour

import com.cobblemon.mod.common.CobblemonBrainConfigs
import com.cobblemon.mod.common.api.ai.BrainPreset
import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.client.CobblemonResources
import com.cobblemon.mod.common.client.gui.behaviour.BehaviourOptionsList.BehaviourOptionSlot
import com.cobblemon.mod.common.client.gui.pokedex.ScaledButton
import com.cobblemon.mod.common.client.render.drawScaledText
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.ContainerObjectSelectionList
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.LivingEntity

class BehaviourOptionsList(
    val parent: BehaviourEditorScreen,
    val left: Int,
    val entity: LivingEntity,
    val appliedPresets: MutableSet<ResourceLocation>,
    val addingMenu: Boolean // Whether this is the list for the un-added presets
) : ContainerObjectSelectionList<BehaviourOptionSlot>(
    Minecraft.getInstance(),
    // int width, int height, int y, int itemHeight
    140,
    200,
    10,
    18
) {

    override fun getScrollbarPosition() = x + width

    fun removeEntry(entry: ResourceLocation) {
        children().find { it.resourceLocation == entry }?.let { removeEntry(it) }
    }

    fun addEntry(entry: ResourceLocation) {
        val brainPreset = CobblemonBrainConfigs.presets[entry] ?: return
        addEntry(BehaviourOptionSlot(this, entry, brainPreset))
    }

    init {
        x = left
        if (addingMenu) {
            val unaddedPresets = CobblemonBrainConfigs.presets.filter { it.value.canBeApplied(entity) && it.key !in appliedPresets }
            unaddedPresets.forEach { (key, value) -> addEntry(BehaviourOptionSlot(this, key, value)) }
        } else {
            val addedPresets = appliedPresets.mapNotNull { it to (CobblemonBrainConfigs.presets[it]?.takeIf { it.canBeApplied(entity) } ?: return@mapNotNull null) }
            addedPresets.forEach { (key, value) -> addEntry(BehaviourOptionSlot(this, key, value)) }
        }
    }

    class BehaviourOptionSlot(val parent: BehaviourOptionsList, val resourceLocation: ResourceLocation, val brainPreset: BrainPreset) : Entry<BehaviourOptionSlot>() {
        val children = mutableListOf<GuiEventListener>()
        val applyButton = ScaledButton(
            buttonX = 0F,
            buttonY = 0F,
            buttonWidth = 15,
            buttonHeight = 15,
            resource = CobblemonResources.RED,
            scale = 1F,
            silent = false
        ) {
            if (parent.addingMenu) {
                parent.parent.add(resourceLocation)
            } else {
                parent.parent.remove(resourceLocation)
            }
        }

        override fun children() = listOf(applyButton)

        override fun narratables() = children.filterIsInstance<NarratableEntry>()

        override fun setFocused(focused: Boolean) {}
        override fun isFocused() = false

        override fun render(
            guiGraphics: GuiGraphics,
            index: Int,
            top: Int,
            left: Int,
            width: Int,
            height: Int,
            mouseX: Int,
            mouseY: Int,
            hovering: Boolean,
            partialTick: Float
        ) {
            blitk(
                matrixStack = guiGraphics.pose(),
                texture = CobblemonResources.WHITE,
                x = left,
                y = top,
                height = height,
                width = width,
                textureWidth = 1,
                textureHeight = 1
            )

            applyButton.buttonX = left.toFloat() + width - 57
            applyButton.buttonY = top.toFloat()
            applyButton.x = left + width - 57
            applyButton.y = top

            applyButton.render(
                guiGraphics,
                mouseX,
                mouseY,
                partialTick
            )

            drawScaledText(
                context = guiGraphics,
                text = brainPreset.name.copy(),
                x = left + 40,
                y = top + 4,
                maxCharacterWidth = 120,
                shadow = false,
                colour = 0x000000
            )

            if (hovering) {
                guiGraphics.renderTooltip(
                    parent.minecraft.font,
                    brainPreset.description,
                    mouseX,
                    mouseY
                )
            }
        }
    }
}

