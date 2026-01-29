/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui

import com.cobblemon.mod.common.api.ai.biggerbrain.BiggerBrainState
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.ObjectiveStatus
import com.cobblemon.mod.common.client.CobblemonClient
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Gui
import net.minecraft.client.gui.GuiGraphics

class BrainDebugOverlay : Gui(Minecraft.getInstance()) {

    companion object {
        const val FIXATED_COLOUR = 0xAA00FF
        const val ACTIVE_COLOUR = 0x2CE121
        const val INACTIVE_COLOUR = 0xCCCCCC
        const val BLOCKED_COLOUR = 0xEA1313
        const val INVALID_COLOUR = 0x8C8C8C
    }

    override fun render(guiGraphics: GuiGraphics, deltaTracker: DeltaTracker) {
        val brainDebug = CobblemonClient.biggerBrainDebugData ?: return

        renderSenses(guiGraphics, deltaTracker, brainDebug)
        renderObjectives(guiGraphics, deltaTracker, brainDebug)
        renderFixation(guiGraphics, deltaTracker, brainDebug)
    }

    fun renderSenses(guiGraphics: GuiGraphics, deltaTracker: DeltaTracker, data: BiggerBrainState) {
        val mc = Minecraft.getInstance()
        val senseX = mc.window.guiScaledWidth / 2 - 165
        val senseY = 50

        guiGraphics.drawString(mc.font, "Senses", senseX, senseY, 0xFFFFFF)
        var i = 0
        for (sense in data.senses) {
            i++
            guiGraphics.drawString(mc.font, sense.className, senseX, senseY + i * 12, 0xFFFFFF)
            guiGraphics.drawString(mc.font, sense.ticksUntilNextExecution.toString(), senseX - 20, senseY + i * 12, 0xFFFFFF)
        }
    }

    fun renderObjectives(guiGraphics: GuiGraphics, deltaTracker: DeltaTracker, data: BiggerBrainState) {
        val mc = Minecraft.getInstance()
        val objectiveX = mc.window.guiScaledWidth / 2 + 50
        val objectiveY = 50

        guiGraphics.drawString(mc.font, "Objectives", objectiveX, objectiveY, 0xFFFFFF)

        var i = 0
        for (objective in data.objectives) {
            i++
            val colour = when (objective.status) {
                ObjectiveStatus.FIXATED -> FIXATED_COLOUR
                ObjectiveStatus.ACTIVE -> ACTIVE_COLOUR
                ObjectiveStatus.INACTIVE -> INACTIVE_COLOUR
                ObjectiveStatus.BLOCKED -> BLOCKED_COLOUR
                ObjectiveStatus.INVALID -> INVALID_COLOUR
            }
            guiGraphics.drawString(mc.font, objective.description, objectiveX, objectiveY + i * 12, colour)
        }
    }

    fun renderFixation(guiGraphics: GuiGraphics, deltaTracker: DeltaTracker, data: BiggerBrainState) {
        val mc = Minecraft.getInstance()
        val fixation = data.fixation ?: return
        val fixationX = Minecraft.getInstance().window.guiScaledWidth / 2
        val fixationY = 8

        guiGraphics.drawCenteredString(mc.font, "Fixation:", fixationX, fixationY, 0xFFFFFF)
        guiGraphics.drawCenteredString(mc.font, fixation, fixationX, fixationY + 12, FIXATED_COLOUR)
    }
}