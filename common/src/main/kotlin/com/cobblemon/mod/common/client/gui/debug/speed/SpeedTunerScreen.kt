/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.debug.speed

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.text.text
import java.awt.Color
import java.util.Locale
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractSliderButton
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth
import org.lwjgl.glfw.GLFW

/**
 * Panel for [SpeedTuner] down the left side of the screen. Doesn't pause and leaves the world visible so the display
 * pokemon can be watched from anywhere. Closing it leaves the session running, the debug keybind brings it back.
 */
class SpeedTunerScreen : Screen("Speed Tuner".text()) {
    companion object {
        const val PANEL_WIDTH = 180
        const val MARGIN = 6
        const val ROW_HEIGHT = 20
        const val ROW_GAP = 4
        const val TEXT_LINE_HEIGHT = 11
        const val MAX_SPEED = 20.0
        const val MIN_RADIUS = 2.0
        const val MAX_RADIUS = 24.0
        const val MIN_LINE_LENGTH = 4.0
        const val MAX_LINE_LENGTH = 48.0
        val PANEL_COLOUR = Color(0, 0, 0, 150).rgb
        val EDITED_COLOUR = Color(255, 200, 80).rgb
        val TEXT_COLOUR = Color(255, 255, 255).rgb
    }

    private lateinit var searchBox: EditBox
    private lateinit var speedBox: EditBox
    private lateinit var speedSlider: DecimalSlider
    private var infoTop = 0

    override fun isPauseScreen() = false

    override fun init() {
        super.init()
        val fullWidth = PANEL_WIDTH - MARGIN * 2
        val halfWidth = (fullWidth - ROW_GAP) / 2
        var y = MARGIN
        fun nextRow(): Int = y.also { y += ROW_HEIGHT + ROW_GAP }

        nextRow().let { row ->
            addRenderableWidget(Button.builder("<".text()) { step(-1) }.bounds(MARGIN, row, ROW_HEIGHT, ROW_HEIGHT).build())
            addRenderableWidget(Button.builder(">".text()) { step(1) }.bounds(PANEL_WIDTH - MARGIN - ROW_HEIGHT, row, ROW_HEIGHT, ROW_HEIGHT).build())
        }
        searchBox = addRenderableWidget(EditBox(font, MARGIN, nextRow(), fullWidth, ROW_HEIGHT, "Search".text())).apply {
            setHint("Dex number or name, Enter".text())
        }

        nextRow().let { row ->
            addRenderableWidget(Button.builder("Gait: ${SpeedTuner.gait.name.lowercase()}".text()) {
                SpeedTuner.gait = if (SpeedTuner.gait == SpeedTuner.Gait.SPRINT) SpeedTuner.Gait.WALK else SpeedTuner.Gait.SPRINT
                rebuildWidgets()
            }.bounds(MARGIN, row, halfWidth, ROW_HEIGHT).build())
            addRenderableWidget(Button.builder("Track: ${SpeedTuner.track.name.lowercase()}".text()) {
                SpeedTuner.track = if (SpeedTuner.track == SpeedTuner.Track.CIRCLE) SpeedTuner.Track.LINE else SpeedTuner.Track.CIRCLE
                rebuildWidgets()
            }.bounds(MARGIN + halfWidth + ROW_GAP, row, halfWidth, ROW_HEIGHT).build())
        }

        val entry = SpeedTuner.selected
        speedSlider = addRenderableWidget(
            DecimalSlider(MARGIN, nextRow(), fullWidth, "Speed", 0.0, MAX_SPEED, entry?.let { SpeedTuner.speed(it) } ?: 0.0) {
                SpeedTuner.setSpeed(it)
                speedBox.value = format(it)
            }
        )
        nextRow().let { row ->
            speedBox = addRenderableWidget(EditBox(font, MARGIN, row, halfWidth, ROW_HEIGHT, "Speed".text())).apply {
                value = entry?.let { format(SpeedTuner.speed(it)) } ?: ""
            }
            addRenderableWidget(Button.builder("Set".text()) { applyTypedSpeed() }.bounds(MARGIN + halfWidth + ROW_GAP, row, halfWidth, ROW_HEIGHT).build())
        }

        if (SpeedTuner.track == SpeedTuner.Track.CIRCLE) {
            addRenderableWidget(DecimalSlider(MARGIN, nextRow(), fullWidth, "Radius", MIN_RADIUS, MAX_RADIUS, SpeedTuner.radius) {
                SpeedTuner.radius = it
            })
        } else {
            addRenderableWidget(DecimalSlider(MARGIN, nextRow(), fullWidth, "Length", MIN_LINE_LENGTH, MAX_LINE_LENGTH, SpeedTuner.lineLength) {
                SpeedTuner.lineLength = it
            })
        }

        nextRow().let { row ->
            addRenderableWidget(Button.builder((if (SpeedTuner.paused) "Resume" else "Pause").text()) {
                SpeedTuner.paused = !SpeedTuner.paused
                rebuildWidgets()
            }.bounds(MARGIN, row, halfWidth, ROW_HEIGHT).build())
            addRenderableWidget(Button.builder("Reset speed".text()) {
                SpeedTuner.resetSpeed()
                rebuildWidgets()
            }.bounds(MARGIN + halfWidth + ROW_GAP, row, halfWidth, ROW_HEIGHT).build())
        }
        nextRow().let { row ->
            val reviewed = entry?.let { SpeedTuner.isReviewed(it) } == true
            addRenderableWidget(Button.builder((if (reviewed) "Reviewed ✔" else "Mark reviewed").text()) {
                SpeedTuner.toggleReviewed()
                rebuildWidgets()
            }.bounds(MARGIN, row, halfWidth, ROW_HEIGHT).build())
            addRenderableWidget(Button.builder("Export".text()) { SpeedTuner.export() }
                .bounds(MARGIN + halfWidth + ROW_GAP, row, halfWidth, ROW_HEIGHT).build())
        }
        addRenderableWidget(Button.builder("End session".text()) {
            SpeedTuner.stop()
            onClose()
        }.bounds(MARGIN, nextRow(), fullWidth, ROW_HEIGHT).build())

        infoTop = y
    }

    private fun step(offset: Int) {
        SpeedTuner.step(offset)
        rebuildWidgets()
    }

    private fun applyTypedSpeed() {
        val typed = speedBox.value.toDoubleOrNull() ?: return
        SpeedTuner.setSpeed(typed.coerceIn(0.0, MAX_SPEED))
        rebuildWidgets()
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (searchBox.isFocused) {
                if (SpeedTuner.search(searchBox.value)) rebuildWidgets()
                return true
            }
            if (speedBox.isFocused) {
                applyTypedSpeed()
                return true
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun onClose() {
        SpeedTuner.save()
        super.onClose()
    }

    // Only darken the panel so the world stays visible
    override fun renderBackground(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        guiGraphics.fill(0, 0, PANEL_WIDTH, height, PANEL_COLOUR)
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)
        val entry = SpeedTuner.selected
        val title = entry?.let { selected ->
            val name = PokemonSpecies.getByIdentifier(selected.speciesId)?.translatedName ?: selected.speciesId.path.text()
            Component.literal("#${selected.dex} ").append(name)
        } ?: "No species".text()
        guiGraphics.drawCenteredString(font, title, PANEL_WIDTH / 2, MARGIN + (ROW_HEIGHT - font.lineHeight) / 2, TEXT_COLOUR)

        var y = infoTop
        fun line(text: String, colour: Int = TEXT_COLOUR) {
            guiGraphics.drawString(font, text, MARGIN, y, colour)
            y += TEXT_LINE_HEIGHT
        }
        if (entry != null) {
            val speed = SpeedTuner.speed(entry)
            line("Speed: ${format(speed)} blocks/s", if (SpeedTuner.isEdited(entry)) EDITED_COLOUR else TEXT_COLOUR)
            line("Original: ${format(SpeedTuner.originalSpeed(entry))} blocks/s")
            if (SpeedTuner.gait == SpeedTuner.Gait.WALK) {
                line("(estimated, raw walkSpeed ${format(entry.walkSpeed.toDouble())})")
            }
        }
        line("Sparks: player ${SpeedTuner.gait.name.lowercase()} ${format(SpeedTuner.referenceSpeed())} blocks/s")
        line("Edited: ${SpeedTuner.editedCount()}  Reviewed: ${SpeedTuner.reviewedCount()}")
        line("Species: ${SpeedTuner.visibleEntries().size}")
    }

    private fun format(value: Double) = String.format(Locale.ROOT, "%.2f", value)

    /** Slider over a decimal range that reports every change. */
    class DecimalSlider(
        x: Int,
        y: Int,
        width: Int,
        private val label: String,
        private val min: Double,
        private val max: Double,
        initial: Double,
        private val onChange: (Double) -> Unit
    ) : AbstractSliderButton(x, y, width, ROW_HEIGHT, Component.empty(), 0.0) {
        private val current: Double
            get() = Mth.lerp(value, min, max)

        init {
            value = ((initial - min) / (max - min)).coerceIn(0.0, 1.0)
            updateMessage()
        }

        override fun updateMessage() {
            message = "$label: ${String.format(Locale.ROOT, "%.2f", current)}".text()
        }

        override fun applyValue() {
            onChange(current)
        }
    }
}
