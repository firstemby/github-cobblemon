/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.npc

import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.api.npc.premades.PremadeNPC
import com.cobblemon.mod.common.api.npc.premades.PremadeNPCs
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.client.gui.npc.widgets.SimpleNPCTextInputWidget
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.net.messages.server.npc.ApplyPremadeNPCPacket
import com.cobblemon.mod.common.net.messages.server.npc.DeletePremadePacket
import com.cobblemon.mod.common.net.messages.server.npc.SavePremadeFromNPCPacket
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.lang
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.ContainerObjectSelectionList
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

class PremadeEditorScreen(
    val npcId: Int,
    val returnScreen: Screen
) : Screen(Component.literal("Premade Editor")) {
    companion object {
        const val BASE_WIDTH = 360
        const val BASE_HEIGHT = 220

        val baseResource = cobblemonResource("textures/gui/npc/base_behaviour.png")
    }

    var x = 0
    var y = 0

    var newId = ""
    var newDisplayName = ""

    override fun init() {
        super.init()
        minecraft = Minecraft.getInstance()
        x = (minecraft!!.window.guiScaledWidth / 2) - BASE_WIDTH / 2
        y = (minecraft!!.window.guiScaledHeight / 2) - BASE_HEIGHT / 2

        // Scrolling list of existing premades
        addRenderableWidget(
            PremadeList(
                parent = this,
                left = x + 12,
                top = y + 38
            )
        )

        // New premade ID input
        addRenderableWidget(SimpleNPCTextInputWidget(
            getter = { newId },
            setter = { newId = it },
            posX = x + 220,
            posY = y + 52,
            width = 128,
            height = 14,
            maxLength = 64
        ))

        // New premade display name input
        addRenderableWidget(SimpleNPCTextInputWidget(
            getter = { newDisplayName },
            setter = { newDisplayName = it },
            posX = x + 220,
            posY = y + 82,
            width = 128,
            height = 14,
            maxLength = 64
        ))

        // Save New button
        addRenderableWidget(
            NPCEditorButton(
                buttonX = x + 220F,
                buttonY = y + 102F,
                label = lang("ui.premade_editor.save_new"),
            ) {
                val id = ResourceLocation.tryParse(newId) ?: return@NPCEditorButton
                SavePremadeFromNPCPacket(
                    npcId = npcId,
                    premadeNPCId = id,
                    displayName = newDisplayName.text().copy(),
                    includeVariation = true,
                    includeSettings = true,
                    includeParty = true,
                    includeClassification = true
                ).sendToServer()
                minecraft!!.setScreen(returnScreen)
            }
        )

        // Back button
        addRenderableWidget(
            NPCEditorButton(
                buttonX = x + 348F,
                buttonY = y + 201F,
                label = lang("ui.generic.back"),
                alignRight = true
            ) {
                minecraft!!.setScreen(returnScreen)
            }
        )
    }

    override fun renderBlurredBackground(delta: Float) {}

    override fun renderMenuBackground(context: GuiGraphics) {}

    override fun isPauseScreen() = false

    override fun render(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        blitk(
            matrixStack = context.pose(),
            texture = baseResource,
            x = x,
            y = y,
            height = BASE_HEIGHT,
            width = BASE_WIDTH
        )

        drawScaledText(
            context = context,
            text = lang("ui.premade_editor.title"),
            x = x + 12,
            y = y + 9,
            shadow = true
        )

        drawScaledText(
            context = context,
            text = lang("ui.premade_editor.existing"),
            x = x + 94,
            y = y + 26,
            centered = true,
            shadow = true
        )

        drawScaledText(
            context = context,
            text = lang("ui.premade_editor.new_id"),
            x = x + 220,
            y = y + 42,
            shadow = true
        )

        drawScaledText(
            context = context,
            text = lang("ui.premade_editor.new_display_name"),
            x = x + 220,
            y = y + 72,
            shadow = true
        )

        super.render(context, mouseX, mouseY, delta)
    }

    class PremadeList(
        val parent: PremadeEditorScreen,
        val left: Int,
        val top: Int,
    ) : ContainerObjectSelectionList<PremadeSlot>(
        Minecraft.getInstance(),
        195,
        170,
        top,
        21
    ) {
        init {
            x = left
            val premades = PremadeNPCs.premadeNPCsById
            premades.forEach { (id, premade) ->
                addEntry(PremadeSlot(this, id, premade))
            }
        }

        override fun renderListBackground(context: GuiGraphics) {}
        override fun renderListSeparators(guiGraphics: GuiGraphics) {}
        override fun getScrollbarPosition() = left + width - 6
    }

    class PremadeSlot(
        val parent: PremadeList,
        val premadeId: ResourceLocation,
        val premade: PremadeNPC
    ) : ContainerObjectSelectionList.Entry<PremadeSlot>() {

        companion object {
            const val ROW_WIDTH = 165
            const val BUTTON_WIDTH = 16
        }

        val isEditable = premade.editable
        val textColour = if (isEditable) 0xFFFFFF else 0x888888

        // Apply button
        val applyButton = NPCEditorButton(
            0F, 0F,
            label = lang("ui.premade_editor.apply"),
            buttonWidth = 36
        ) {
            ApplyPremadeNPCPacket(parent.parent.npcId, premadeId).sendToServer()
            Minecraft.getInstance().setScreen(parent.parent.returnScreen)
        }

        // Overwrite button
        val overwriteButton = NPCEditorButton(
            0F, 0F,
            label = lang("ui.premade_editor.overwrite"),
            buttonWidth = 50
        ) {
            SavePremadeFromNPCPacket(
                npcId = parent.parent.npcId,
                premadeNPCId = premadeId,
                displayName = premade.displayName.copy(),
                includeVariation = true,
                includeSettings = true,
                includeParty = true,
                includeClassification = true
            ).sendToServer()
            Minecraft.getInstance().setScreen(parent.parent.returnScreen)
        }

        // Delete button (only for editable)
        val deleteButton = if (isEditable) NPCEditorButton(
            0F, 0F,
            label = lang("ui.premade_editor.delete"),
            buttonWidth = 36
        ) {
            DeletePremadePacket(premadeId).sendToServer()
            Minecraft.getInstance().setScreen(parent.parent.returnScreen)
        } else null

        override fun children(): List<GuiEventListener> = listOfNotNull(applyButton, overwriteButton, deleteButton)
        override fun narratables(): List<NarratableEntry> = emptyList()
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
            val displayText = if (premade.displayName.string.isNotEmpty()) {
                "${premade.displayName.string} (${premadeId})"
            } else {
                premadeId.toString()
            }

            drawScaledText(
                context = guiGraphics,
                text = displayText.text(),
                x = parent.left + 5,
                y = top + 2,
                shadow = true,
                colour = textColour
            )

            var buttonX = parent.left + 5
            val buttonY = top + 11

            applyButton.x = buttonX
            applyButton.y = buttonY
            applyButton.render(guiGraphics, mouseX, mouseY, partialTick)
            buttonX += 37

            overwriteButton.x = buttonX
            overwriteButton.y = buttonY
            overwriteButton.render(guiGraphics, mouseX, mouseY, partialTick)
            buttonX += 51

            deleteButton?.let {
                it.x = buttonX
                it.y = buttonY
                it.render(guiGraphics, mouseX, mouseY, partialTick)
            }
        }
    }
}
