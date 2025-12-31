/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.dialogue

import com.bedrockk.molang.runtime.MoLangRuntime
import com.bedrockk.molang.runtime.MoParams
import com.bedrockk.molang.runtime.value.DoubleValue
import com.cobblemon.mod.common.api.dialogue.ArtificialDialogueFaceProvider
import com.cobblemon.mod.common.api.dialogue.PlayerDialogueFaceProvider
import com.cobblemon.mod.common.api.dialogue.ReferenceDialogueFaceProvider
import com.cobblemon.mod.common.api.molang.ExpressionLike
import com.cobblemon.mod.common.api.molang.MoLangFunctions.addFunctions
import com.cobblemon.mod.common.api.molang.MoLangFunctions.setup
import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.client.ClientMoLangFunctions.setupClient
import com.cobblemon.mod.common.client.gui.CobblemonRenderable
import com.cobblemon.mod.common.client.gui.dialogue.widgets.DialogueBox
import com.cobblemon.mod.common.client.gui.dialogue.widgets.DialogueNameWidget
import com.cobblemon.mod.common.client.gui.dialogue.widgets.DialogueOptionWidget
import com.cobblemon.mod.common.client.gui.dialogue.widgets.DialoguePortraitWidget
import com.cobblemon.mod.common.client.gui.dialogue.widgets.DialogueTextInputWidget
import com.cobblemon.mod.common.client.gui.dialogue.widgets.DialogueTimerWidget
import com.cobblemon.mod.common.entity.PosableEntity
import com.cobblemon.mod.common.net.messages.client.dialogue.dto.DialogueDTO
import com.cobblemon.mod.common.net.messages.client.dialogue.dto.DialogueGibberDTO
import com.cobblemon.mod.common.net.messages.client.dialogue.dto.DialogueInputDTO
import com.cobblemon.mod.common.net.messages.server.dialogue.EscapeDialoguePacket
import com.cobblemon.mod.common.util.asExpressionLike
import com.cobblemon.mod.common.util.asExpressions
import com.cobblemon.mod.common.util.asTranslated
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.isInventoryKeyPressed
import com.cobblemon.mod.common.util.nextBetween
import com.cobblemon.mod.common.util.resolve
import kotlin.random.Random
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.sounds.SoundEvent
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth

class DialogueScreen(var dialogueDTO: DialogueDTO) : Screen("gui.dialogue".asTranslated()), CobblemonRenderable {
    val speakers = dialogueDTO.speakers?.mapNotNull { (key, value) ->
        val name = value.name
        when (val face = value.face) {
            is ArtificialDialogueFaceProvider -> key to DialogueRenderableSpeaker(
                name = name,
                face = ArtificialRenderableFace(face.modelType, face.identifier, face.aspects, face.isLeftSide),
                gibber = value.gibber
            )
            is PlayerDialogueFaceProvider -> key to DialogueRenderableSpeaker(
                name = name,
                face = PlayerRenderableFace(
                    playerId = face.playerId,
                    isLeftSide = face.isLeftSide
                ),
                gibber = value.gibber
            )
            is ReferenceDialogueFaceProvider -> {
                key to DialogueRenderableSpeaker(
                    name = name,
                    face = ReferenceRenderableFace(
                        entity = Minecraft.getInstance().level?.getEntity(face.entityId) as? PosableEntity ?: return@mapNotNull null,
                        isLeftSide = face.isLeftSide
                    ),
                    gibber = value.gibber
                )
            }
            else -> key to DialogueRenderableSpeaker(name = name, face = null, gibber = value.gibber)
        }
    }?.toMap() ?: emptyMap()
    val runtime = MoLangRuntime().setup().setupClient()

    // After they do something, the GUI will wait for the server to update the dialogue in some way
    var waitingForServerUpdate = false

    val dialogueId = dialogueDTO.dialogueId
    var remainingSeconds = dialogueDTO.dialogueInput.deadline
    lateinit var dialogueTimerWidget: DialogueTimerWidget
    lateinit var dialogueTextInputWidget: DialogueTextInputWidget
    lateinit var dialogueBox: DialogueBox
    lateinit var dialogueOptionWidgets: List<DialogueOptionWidget>
    lateinit var dialogueNameWidget: DialogueNameWidget
    lateinit var dialoguePortraitWidget: DialoguePortraitWidget

    var dialogueStartTick = 0

    val scaledWidth
        get() = this.minecraft!!.window.guiScaledWidth
    val scaledHeight
        get() = this.minecraft!!.window.guiScaledHeight

    var expressionsToRun = listOf<ExpressionLike>()
    var gibberDone = false
    var gibber: DialogueGibberDTO? = null
    var gibberSounds = listOf<SoundEvent>()

    companion object {
        const val BOX_WIDTH = 196
        const val BOX_HEIGHT = 74

        const val PORTRAIT_WIDTH = 38
        const val PORTRAIT_HEIGHT = 36

        private const val BAR_WIDTH = 186
        private const val BAR_HEIGHT = 4

        private const val OPTION_HEIGHT = 21
        private const val OPTION_WIDTH_NARROW = 96
        private const val OPTION_WIDTH_WIDE = 196

        private const val NAME_WIDTH = 196
        private const val NAME_HEIGHT = 17

        private const val TEXT_INPUT_WIDTH = 196
        private const val TEXT_INPUT_HEIGHT = 21

        private const val OPTION_HORIZONTAL_SPACING = 4
        private const val OPTION_VERTICAL_SPACING = 4

        private const val SCROLL_TRACK_THICKNESS = 3
        private const val SCROLL_TRACK_PADDING = 4
        private const val SCROLL_CONTAINER_WIDTH = 10
        private const val SCROLL_INSET = 2
        private const val BUTTON_EDGE_PADDING = 2

        private val buttonResource = cobblemonResource("textures/gui/dialogue/dialogue_button.png")
        private val buttonFullWidthResource = cobblemonResource("textures/gui/dialogue/dialogue_button_full.png")

        val dialogueMoLangFunctions = mutableListOf<(DialogueScreen) -> HashMap<String, java.util.function.Function<MoParams, Any>>>(
            { dialogueScreen ->
                return@mutableListOf hashMapOf(
                    "face" to java.util.function.Function {
                        val face = dialogueScreen.dialogueDTO.currentPageDTO.speaker?.let { dialogueScreen.speakers[it] }?.face ?: return@Function DoubleValue.ZERO
                        return@Function face.struct
                    },
                )
            }
        )
    }

    override fun renderBackground(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {}

    override fun renderBlurredBackground(delta: Float) {}

    override fun init() {
        super.init()

        runtime.environment.query.addFunctions(
            functions = dialogueMoLangFunctions.flatMap { it(this@DialogueScreen).entries }.associate { it.key to it.value }
        )

        val currentSpeaker = dialogueDTO.currentPageDTO.speaker
        gibber = dialogueDTO.currentPageDTO.gibber ?: currentSpeaker?.let { speakers[it]?.gibber }
        gibberSounds = gibber?.sounds?.mapNotNull { BuiltInRegistries.SOUND_EVENT.get(it) } ?: emptyList()

        expressionsToRun = dialogueDTO.currentPageDTO.clientActions.map { it.asExpressionLike() }
        val centerX = scaledWidth / 2F
        val boxMinY = (scaledHeight / 2F) - (BOX_HEIGHT / 2F) - 30
        val boxMaxY = boxMinY + BOX_HEIGHT

        dialogueBox = DialogueBox(
            dialogueScreen = this,
            background = dialogueDTO.currentPageDTO.background,
            messages = dialogueDTO.currentPageDTO.lines,
            textColor = dialogueDTO.currentPageDTO.textColor,
            listX = (centerX - BOX_WIDTH / 2F).toInt(),
            listY = boxMinY.toInt(),
            frameWidth = BOX_WIDTH,
            height = BOX_HEIGHT
        )

        val name = speakers[dialogueDTO.currentPageDTO.speaker]?.name
        dialogueNameWidget = DialogueNameWidget(
            x = (centerX - BOX_WIDTH / 2F).toInt(),
            y = (boxMinY - NAME_HEIGHT + 1).toInt(),
            width = NAME_WIDTH,
            height = NAME_HEIGHT,
            text = name
        )

        dialoguePortraitWidget = DialoguePortraitWidget(
            dialogueScreen = this,
            x = (centerX - BOX_WIDTH / 2F - PORTRAIT_WIDTH).toInt(),
            y = (boxMinY - NAME_HEIGHT + 1).toInt(),
            width = PORTRAIT_WIDTH,
            height = PORTRAIT_HEIGHT
        )

        dialogueTimerWidget = DialogueTimerWidget(
            dialogueScreen = this,
            x = (centerX - BAR_WIDTH / 2F).toInt(),
            y = (boxMaxY - 1).toInt(),
            width = BAR_WIDTH,
            height = BAR_HEIGHT
        )

        dialogueTextInputWidget = DialogueTextInputWidget(
            dialogueScreen = this,
            x = (centerX - BOX_WIDTH / 2F).toInt(),
            y = (boxMaxY + 7).toInt(),
            width = TEXT_INPUT_WIDTH,
            height = TEXT_INPUT_HEIGHT
        )

        val optionCount = dialogueDTO.dialogueInput.options.size
        val vertical = dialogueDTO.dialogueInput.vertical
        val horizontalSpacing = if (vertical) 0 else (OPTION_HORIZONTAL_SPACING + OPTION_WIDTH_NARROW)
        val verticalSpacing = if (vertical) OPTION_VERTICAL_SPACING + OPTION_HEIGHT else 0
        val totalWidth = (optionCount - 1) * horizontalSpacing
        val optionStartX = centerX - (totalWidth / 2F)
        val optionStartY = boxMaxY + 7

        // Build option widget instances (x/y will be set by either layout or scroller)
        dialogueOptionWidgets = dialogueDTO.dialogueInput.options.mapIndexed { index, option ->
            val x = optionStartX + (index * horizontalSpacing).toFloat()
            val y = optionStartY + (index * verticalSpacing).toFloat()

            DialogueOptionWidget(
                dialogueScreen = this,
                text = option.text,
                value = option.value,
                selectable = option.selectable,
                x = x.toInt() - (if (vertical) OPTION_WIDTH_WIDE / 2 else OPTION_WIDTH_NARROW / 2),
                y = y.toInt(),
                width = if (vertical) OPTION_WIDTH_WIDE else OPTION_WIDTH_NARROW,
                height = OPTION_HEIGHT,
                texture = if (vertical) buttonFullWidthResource else buttonResource
            )
        }

        if (dialogueOptionWidgets.size > 4) {
            // Create and add a scroller containing the option widgets
            val visibleCount = 4

            if (vertical) {
                val contentWidth = OPTION_WIDTH_WIDE
                val scrollerWidth = contentWidth + SCROLL_CONTAINER_WIDTH + BUTTON_EDGE_PADDING * 2
                val scrollerHeight =
                    (OPTION_HEIGHT * visibleCount + OPTION_VERTICAL_SPACING * (visibleCount - 1)) + BUTTON_EDGE_PADDING * 2
                // center only the content area so the buttons remain centered where expected
                val scrollerX = (optionStartX - (contentWidth / 2F)).toInt() - BUTTON_EDGE_PADDING
                val scrollerY = optionStartY.toInt()

                val optionsScroller = OptionsScrollWidget(
                    dialogueScreen = this,
                    x = scrollerX,
                    y = scrollerY,
                    width = scrollerWidth,
                    height = scrollerHeight,
                    entries = dialogueOptionWidgets,
                    vertical = true,
                    containerSize = SCROLL_CONTAINER_WIDTH
                )
                addRenderableWidget(optionsScroller)
            } else {
                val contentWidth = OPTION_WIDTH_NARROW * visibleCount + OPTION_HORIZONTAL_SPACING * (visibleCount - 1)
                val scrollerWidth = contentWidth + BUTTON_EDGE_PADDING * 2
                val scrollerHeight = OPTION_HEIGHT + SCROLL_CONTAINER_WIDTH + BUTTON_EDGE_PADDING * 2

                val desiredScrollerX = (centerX - (scrollerWidth / 2F)).toInt()
                val scrollerY = optionStartY.toInt() + 2

                val minX = 0
                val maxX = scaledWidth - scrollerWidth - 4
                val scrollerX = Mth.clamp(desiredScrollerX, minX, maxX)


                val optionsScroller = OptionsScrollWidget(
                    dialogueScreen = this,
                    x = scrollerX,
                    y = scrollerY,
                    width = scrollerWidth,
                    height = scrollerHeight,
                    entries = dialogueOptionWidgets,
                    vertical = false,
                    containerSize = SCROLL_CONTAINER_WIDTH
                )
                addRenderableWidget(optionsScroller)
            }
        } else {
            dialogueOptionWidgets.forEachIndexed { index, widget ->
                val x = optionStartX + (index * horizontalSpacing)
                val y = optionStartY + (index * verticalSpacing)

                widget.x = x.toInt() - (if (vertical) OPTION_WIDTH_WIDE / 2 else OPTION_WIDTH_NARROW / 2)
                widget.y = y.toInt()
                addRenderableWidget(widget)
            }
        }

        addRenderableOnly(dialogueTimerWidget)
        addRenderableWidget(dialogueTextInputWidget)
        addRenderableWidget(dialogueBox)
        addRenderableOnly(dialogueNameWidget)
        addRenderableOnly(dialoguePortraitWidget)

        if (dialogueDTO.dialogueInput.inputType == DialogueInputDTO.InputType.TEXT) {
            setInitialFocus(dialogueTextInputWidget)
        }

        this.dialogueStartTick = minecraft!!.player!!.tickCount
    }

    var gibberIndex = 0
    var timeElapsedSinceGibber = 0.0

    fun renderInput() = gibber?.graduallyShowText != true || gibberDone

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        val gibber = gibber
        if (gibber != null) {
            playGibberSpeak(
                delta = delta,
                text = dialogueDTO.currentPageDTO.lines.joinToString { it.string },
                gibber = gibber
            )
        }
        remainingSeconds -= delta / 20F
        if (renderInput()) {
            dialogueTimerWidget.ratio = if (remainingSeconds <= 0) -1F else remainingSeconds / dialogueDTO.dialogueInput.deadline
        }
        super.render(guiGraphics, mouseX, mouseY, delta)
        expressionsToRun.forEach { runtime.resolve(it) }
        expressionsToRun = emptyList()
    }

    override fun isPauseScreen() = false

    fun update(dialogueDTO: DialogueDTO) {
        gibberIndex = 0
        gibberDone = false
        this.dialogueDTO = dialogueDTO
        this.remainingSeconds = dialogueDTO.dialogueInput.deadline
        waitingForServerUpdate = false
        rebuildWidgets()
    }

    fun sendToServer(packet: NetworkPacket<*>) {
        packet.sendToServer()
        waitingForServerUpdate = true
    }

    override fun onClose() {
        EscapeDialoguePacket().sendToServer()
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (isInventoryKeyPressed(minecraft, keyCode, scanCode) && focused !is EditBox) {
            onClose()
            return true
        }

        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    fun playGibberSpeak(delta: Float, text: String, gibber: DialogueGibberDTO) {
        timeElapsedSinceGibber += delta / 20

        if (timeElapsedSinceGibber < gibber.interval || gibberDone) {
            return
        }

        timeElapsedSinceGibber = 0.0

        if (gibberIndex >= text.length) {
            gibberDone = true
            return
        }

        val gibberChar = text[gibberIndex]
        gibberIndex += gibber.step

        if (gibberSounds.isNotEmpty()) {
            val pitchRange = gibber.maxPitch * 100 - gibber.minPitch * 100
            val gibberPitch = (gibberChar.hashCode() % pitchRange + gibber.minPitch * 100) / 100

            val volume = Random.nextBetween(gibber.minVolume, gibber.maxVolume)
            val gibberSound = gibberSounds[gibberChar.hashCode() % gibberSounds.size]
            minecraft?.soundManager?.play(SimpleSoundInstance.forUI(gibberSound, gibberPitch, volume))
        }
    }

    inner class OptionsScrollWidget(
        dialogueScreen: DialogueScreen,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        val entries: List<DialogueOptionWidget>,
        val vertical: Boolean,
        val containerSize: Int
    ) : net.minecraft.client.gui.components.AbstractWidget(x, y, width, height, Component.empty()) {

        private var scrollOffset = 0

        private val horizontalInset = BUTTON_EDGE_PADDING
        private val verticalInset = BUTTON_EDGE_PADDING

        private val contentAreaWidth
            get() = if (vertical) width - containerSize - horizontalInset * 2 else width - horizontalInset * 2
        private val contentAreaHeight
            get() = if (vertical) height - verticalInset * 2 else height - containerSize - verticalInset * 2

        private val slotSize =
            if (vertical) OPTION_HEIGHT + OPTION_VERTICAL_SPACING else OPTION_WIDTH_NARROW + OPTION_HORIZONTAL_SPACING
        private val totalSize = kotlin.math.max(
            0,
            entries.size * slotSize - (if (vertical) OPTION_VERTICAL_SPACING else OPTION_HORIZONTAL_SPACING)
        )
        private val maxScroll = kotlin.math.max(0, totalSize - (if (vertical) contentAreaHeight else contentAreaWidth))

        // Drag state for the scrollbar
        private var dragging = false
        private var dragAnchor = 0
        private var dragStartScroll = 0

        // Inclusive visible index range (covers partially-visible items)
        private fun visibleRange(): IntRange {
            val start = (scrollOffset / slotSize).coerceAtLeast(0)
            val visiblePixels = if (vertical) contentAreaHeight else contentAreaWidth
            val end = ((scrollOffset + visiblePixels + slotSize - 1) / slotSize).coerceAtMost(entries.size - 1)
            return start..end
        }

        override fun renderWidget(context: GuiGraphics, mouseX: Int, mouseY: Int, partialTicks: Float) {
            // Ensure scrollOffset never exceeds maxScroll (prevents top being cut off after resize/changes)
            scrollOffset = scrollOffset.coerceIn(0, maxScroll)

            // Enable scissor to clip children
            context.enableScissor(x, y, x + width, y + height)
            try {
                val range = visibleRange()

                val contentLeft = x + horizontalInset
                val contentTop = y + verticalInset

                for (i in range) {
                    val entry = entries[i]
                    val posIndex = i

                    val px = if (vertical) {
                        x + horizontalInset + (contentAreaWidth - entry.width) / 2
                    } else {
                        x + horizontalInset + (posIndex * slotSize) - scrollOffset
                    }

                    val py = if (vertical) {
                        y + verticalInset + (posIndex * slotSize) - scrollOffset
                    } else {
                        y + verticalInset + (contentAreaHeight - entry.height) / 2
                    }

                    val prevX = entry.x
                    val prevY = entry.y
                    entry.x = px
                    entry.y = py
                    entry.render(context, mouseX, mouseY, partialTicks)
                    entry.x = prevX
                    entry.y = prevY
                }

                if (maxScroll > 0) {
                    if (vertical) {
                        val containerX = x + horizontalInset + contentAreaWidth + SCROLL_INSET
                        val containerY = y + SCROLL_INSET
                        val containerW = kotlin.math.max(8, containerSize - SCROLL_INSET * 2)
                        val containerH = kotlin.math.max(16, height - SCROLL_INSET * 2)


                        // Scroll container background
                        context.fill(
                            containerX,
                            containerY + 2,
                            containerX + containerW,
                            containerY + containerH - 2,
                            0xFFDDDDDD.toInt()
                        )
                        context.fill(
                            containerX + 1,
                            containerY + 3,
                            containerX + containerW - 1,
                            containerY + containerH - 3,
                            0xFFEEEEEE.toInt()
                        )
                        context.fill(
                            containerX,
                            containerY + 2,
                            containerX + 1,
                            containerY + containerH - 2,
                            0xFF888888.toInt()
                        )

                        val trackX = containerX + (containerW - SCROLL_TRACK_THICKNESS) / 2
                        val trackY = containerY + SCROLL_TRACK_PADDING
                        val trackH = (containerH - SCROLL_TRACK_PADDING * 2).coerceAtLeast(8)

                        var thumbSize = (trackH.toFloat() * trackH.toFloat() / totalSize.toFloat()).toInt()
                        thumbSize = Mth.clamp(thumbSize, 8, trackH - 4)
                        val thumbPos = (scrollOffset.toFloat() / maxScroll.toFloat() * (trackH - thumbSize)).toInt() + trackY

                        context.fill(
                            trackX + 3,
                            trackY,
                            trackX + SCROLL_TRACK_THICKNESS - 2,
                            trackY + trackH,
                            0xFFCCCCCC.toInt()
                        )
                        context.fill(
                            trackX,
                            thumbPos,
                            trackX + SCROLL_TRACK_THICKNESS + 1,
                            thumbPos + thumbSize,
                            0xFF888888.toInt()
                        )
                    } else {
                        val containerX = x + SCROLL_INSET
                        val containerY = y + verticalInset + contentAreaHeight + SCROLL_INSET
                        val containerW = kotlin.math.max(16, width - SCROLL_INSET * 2)
                        val containerH = kotlin.math.max(8, containerSize - SCROLL_INSET * 2)

                        // Scroll container background
                        context.fill(
                            containerX + 2,
                            containerY,
                            containerX + containerW - 2,
                            containerY + containerH,
                            0xFFDDDDDD.toInt()
                        )
                        context.fill(
                            containerX + 3,
                            containerY + 1,
                            containerX + containerW - 3,
                            containerY + containerH - 1,
                            0xFFEEEEEE.toInt()
                        )
                        context.fill(
                            containerX + 2,
                            containerY,
                            containerX + containerW - 2,
                            containerY + 1,
                            0xFFA0A0A0.toInt()
                        )

                        val trackX = containerX + SCROLL_TRACK_PADDING
                        val trackW = (containerW - SCROLL_TRACK_PADDING * 2).coerceAtLeast(16)
                        val trackY = containerY + (containerH - SCROLL_TRACK_THICKNESS) / 2

                        var thumbSize = (trackW.toFloat() * trackW.toFloat() / totalSize.toFloat()).toInt()
                        thumbSize = Mth.clamp(thumbSize, 8, trackW - 4)
                        val thumbPos = (scrollOffset.toFloat() / maxScroll.toFloat() * (trackW - thumbSize)).toInt() + trackX

                        context.fill(
                            trackX,
                            trackY + 3,
                            trackX + trackW,
                            trackY + SCROLL_TRACK_THICKNESS - 2,
                            0xFFCCCCCC.toInt()
                        )
                        context.fill(
                            thumbPos,
                            trackY,
                            thumbPos + thumbSize,
                            trackY + SCROLL_TRACK_THICKNESS + 1,
                            0xFFA0A0A0.toInt()
                        )
                    }
                }
            } finally {
                context.disableScissor()
            }
        }

        override fun updateWidgetNarration(narrationElementOutput: net.minecraft.client.gui.narration.NarrationElementOutput) {
        }

        override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
            if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height) {
                scrollOffset = Mth.clamp(scrollOffset - (scrollY * slotSize).toInt(), 0, maxScroll)
                return true
            }
            return false
        }

        override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
            if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height) {
                val range = visibleRange()

                val contentLeft = x + horizontalInset
                val contentTop = y + verticalInset
                val contentRight = contentLeft + contentAreaWidth
                val contentBottom = contentTop + contentAreaHeight

                for (i in range) {
                    val entry = entries[i]
                    val posIndex = i
                    val px = if (vertical) {
                        x + horizontalInset + (contentAreaWidth - entry.width) / 2
                    } else {
                        x + horizontalInset + (posIndex * slotSize) - scrollOffset
                    }
                    val py = if (vertical) {
                        y + verticalInset + (posIndex * slotSize) - scrollOffset
                    } else {
                        y + verticalInset + (contentAreaHeight - entry.height) / 2
                    }

                    // require click to be within both the entry rect and the visible content area
                    if (mouseX >= px && mouseX <= px + entry.width && mouseY >= py && mouseY <= py + entry.height
                        && mouseX >= contentLeft && mouseX <= contentRight && mouseY >= contentTop && mouseY <= contentBottom
                    ) {
                        val prevX = entry.x
                        val prevY = entry.y
                        entry.x = px
                        entry.y = py
                        val consumed = entry.mouseClicked(mouseX, mouseY, button)
                        entry.x = prevX
                        entry.y = prevY
                        if (consumed) return true
                    }
                }

                // If not clicking an entry, check scrollbar container/track and handle click/drag/jump
                if (maxScroll > 0) {
                    if (vertical) {
                        val containerX = x + horizontalInset + contentAreaWidth + SCROLL_INSET
                        val containerY = y + SCROLL_INSET
                        val containerW = kotlin.math.max(8, containerSize - SCROLL_INSET * 2)
                        val containerH = kotlin.math.max(16, height - SCROLL_INSET * 2)

                        // if clicked within the container area (the 0xFFEEEEEE / light fill), map to scroll
                        if (mouseX >= containerX && mouseX <= containerX + containerW && mouseY >= containerY && mouseY <= containerY + containerH) {
                            val trackY = containerY + SCROLL_TRACK_PADDING
                            val trackH = (containerH - SCROLL_TRACK_PADDING * 2).coerceAtLeast(8)

                            var thumbSize = (trackH.toFloat() * trackH.toFloat() / totalSize.toFloat()).toInt()
                            thumbSize = Mth.clamp(thumbSize, 8, trackH - 4)

                            // jump so thumb centers under the click, then start dragging
                            val relative = (mouseY.toInt() - trackY)
                            val trackRange = (trackH - thumbSize).coerceAtLeast(1)
                            val target = ((relative - thumbSize / 2f) / trackRange * maxScroll).toInt()
                            scrollOffset = Mth.clamp(target, 0, maxScroll)

                            // start dragging
                            dragging = true
                            dragAnchor = mouseY.toInt()
                            dragStartScroll = scrollOffset
                            return true
                        }
                    } else {
                        val containerX = x + SCROLL_INSET
                        val containerY = y + verticalInset + contentAreaHeight + SCROLL_INSET
                        val containerW = kotlin.math.max(16, width - SCROLL_INSET * 2)
                        val containerH = kotlin.math.max(8, containerSize - SCROLL_INSET * 2)

                        if (mouseX >= containerX && mouseX <= containerX + containerW && mouseY >= containerY && mouseY <= containerY + containerH) {
                            val trackX = containerX + SCROLL_TRACK_PADDING
                            val trackW = (containerW - SCROLL_TRACK_PADDING * 2).coerceAtLeast(16)

                            var thumbSize = (trackW.toFloat() * trackW.toFloat() / totalSize.toFloat()).toInt()
                            thumbSize = Mth.clamp(thumbSize, 8, trackW - 4)

                            val relative = (mouseX.toInt() - trackX)
                            val trackRange = (trackW - thumbSize).coerceAtLeast(1)
                            val target = ((relative - thumbSize / 2f) / trackRange * maxScroll).toInt()
                            scrollOffset = Mth.clamp(target, 0, maxScroll)

                            // start dragging
                            dragging = true
                            dragAnchor = mouseX.toInt()
                            dragStartScroll = scrollOffset
                            return true
                        }
                    }
                }

                return true
            }
            return false
        }

        override fun mouseDragged(
            mouseX: Double,
            mouseY: Double,
            button: Int,
            deltaX: Double,
            deltaY: Double
        ): Boolean {
            if (!dragging) return false

            if (vertical) {
                val containerX = x + horizontalInset + contentAreaWidth + SCROLL_INSET
                val containerY = y + SCROLL_INSET
                val containerW = kotlin.math.max(8, containerSize - SCROLL_INSET * 2)
                val containerH = kotlin.math.max(16, height - SCROLL_INSET * 2)
                val trackY = containerY + SCROLL_TRACK_PADDING
                val trackH = (containerH - SCROLL_TRACK_PADDING * 2).coerceAtLeast(8)

                var thumbSize = (trackH.toFloat() * trackH.toFloat() / totalSize.toFloat()).toInt()
                thumbSize = Mth.clamp(thumbSize, 8, trackH - 4)
                val trackRange = (trackH - thumbSize).coerceAtLeast(1)

                val delta = (mouseY.toInt() - dragAnchor)
                if (trackRange > 0) {
                    val deltaScroll = (delta.toFloat() / trackRange * maxScroll).toInt()
                    scrollOffset = Mth.clamp(dragStartScroll + deltaScroll, 0, maxScroll)
                }
            } else {
                val containerX = x + SCROLL_INSET
                val containerY = y + verticalInset + contentAreaHeight + SCROLL_INSET
                val containerW = kotlin.math.max(16, width - SCROLL_INSET * 2)
                val containerH = kotlin.math.max(8, containerSize - SCROLL_INSET * 2)
                val trackX = containerX + SCROLL_TRACK_PADDING
                val trackW = (containerW - SCROLL_TRACK_PADDING * 2).coerceAtLeast(16)

                var thumbSize = (trackW.toFloat() * trackW.toFloat() / totalSize.toFloat()).toInt()
                thumbSize = Mth.clamp(thumbSize, 8, trackW - 4)
                val trackRange = (trackW - thumbSize).coerceAtLeast(1)

                val delta = (mouseX.toInt() - dragAnchor)
                if (trackRange > 0) {
                    val deltaScroll = (delta.toFloat() / trackRange * maxScroll).toInt()
                    scrollOffset = Mth.clamp(dragStartScroll + deltaScroll, 0, maxScroll)
                }
            }

            return true
        }

        override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
            if (dragging) {
                dragging = false
                return true
            }
            return false
        }
    }
}