package com.cobblemon.mod.common.client.gui.debug

import com.cobblemon.mod.common.CobblemonNetwork
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.battles.runner.ShowdownStatus
import com.cobblemon.mod.common.client.gui.CobblemonRenderable
import com.cobblemon.mod.common.client.gui.debug.ShowdownDebugGUI.Companion.activeShowdownService
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.net.messages.server.debug.showdown.RequestShowdownInfoPacket
import com.cobblemon.mod.common.net.messages.server.debug.showdown.ShowdownActionPacket
import com.cobblemon.mod.common.net.messages.server.debug.showdown.ShowdownInputPacket
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.lang
import com.mojang.blaze3d.platform.ClipboardManager
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.ObjectSelectionList
import net.minecraft.client.gui.components.ObjectSelectionList.Entry
import net.minecraft.client.gui.screens.Screen
import net.minecraft.locale.Language
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.FormattedCharSequence
import java.awt.Color
import java.util.*

/**
 * GUI screen for debugging the Showdown service in the Cobblemon mod.
 * This screen allows users to view and interact with the Showdown service logs and status.
 *
 * @author landonjw
 */
class ShowdownDebugGUI : Screen(lang("ui.debug.showdown")), CobblemonRenderable {

    companion object {
        var activeShowdownService = cobblemonResource("embedded")
            private set
    }

    private var showdownStatus: ShowdownStatus? = null
    val showdownLogs = mutableMapOf<ResourceLocation, MutableList<String>>()
    var availableServices = listOf(cobblemonResource("embedded"))

    val outputWindow = addRenderableWidget(ShowdownOutputList())
    val serviceButton = addRenderableWidget(
        Button.builder(Component.literal("Service: $activeShowdownService")) {
            cycleActiveService()
        }.bounds(10, 10, 200, 20).build()
    )

    val startButton = addRenderableWidget(
        Button.builder(Component.literal("Start")) { button ->
            CobblemonNetwork.sendToServer(ShowdownActionPacket(
                service = activeShowdownService,
                action = ShowdownActionPacket.Action.START
            ))
        }.bounds(10, 40, 50, 20).build()
    )

    val stopButton = addRenderableWidget(
        Button.builder(Component.literal("Stop")) { button ->
            CobblemonNetwork.sendToServer(ShowdownActionPacket(
                service = activeShowdownService,
                action = ShowdownActionPacket.Action.STOP
            ))
        }.bounds(70, 40, 50, 20).build()
    )

    val restartButton = addRenderableWidget(
        Button.builder(Component.literal("Restart")) { button ->
            this.showdownStatus = ShowdownStatus.RESTARTING
            CobblemonNetwork.sendToServer(ShowdownActionPacket(
                service = activeShowdownService,
                action = ShowdownActionPacket.Action.RESTART
            ))
        }.bounds(130, 40, 50, 20).build()
    )

    val clipboardManager = ClipboardManager()
    var timer = Timer()

    init {
        addRenderableWidget(
            Button.builder(Component.literal("Start Queue")) { button ->
                println("Queue button pressed")
                button.message = Component.literal("Cancel Queue")
            }.bounds(10, 70, 100, 20).build()
        )

        addRenderableWidget(
            Button.builder(Component.literal("Copy to Clipboard")) Clipboard@{
                val log = showdownLogs[activeShowdownService] ?: return@Clipboard
                clipboardManager.setClipboard(Minecraft.getInstance().window.window, log.joinToString("\n"))
            }.bounds(170, Minecraft.getInstance().window.guiScaledHeight - 40, 100, 30).build()
        )

        addRenderableWidget(ShowdownInputWidget())
        startTimer()
    }

    fun cycleActiveService() {
        if (availableServices.size == 1) return
        val currentIndex = availableServices.indexOf(activeShowdownService)
        activeShowdownService = availableServices[(currentIndex + 1) % availableServices.size]
        serviceButton.message = Component.literal("Service: $activeShowdownService")
        outputWindow.reset()
        showdownLogs[activeShowdownService]?.forEach { outputWindow.addNewEntry(it.text()) }
        startTimer()
    }

    fun startTimer() {
        timer.cancel()
        timer = Timer()
        timer.schedule(object : TimerTask() {
            override fun run() {
                CobblemonNetwork.sendToServer(RequestShowdownInfoPacket(activeShowdownService))
            }
        }, 0, 1000)
    }

    override fun onClose() {
        timer.cancel()
        super.onClose()
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        guiGraphics.fill(0, 0, width, height, Color(0, 0, 0, 100).rgb)
        super.render(guiGraphics, mouseX, mouseY, partialTick)
        if (showdownStatus != null) {
            guiGraphics.fill(
                190,
                45,
                200,
                55,
                when (showdownStatus!!) {
                    ShowdownStatus.DISCONNECTED -> Color(255, 0, 0, 255).rgb
                    ShowdownStatus.RESTARTING -> Color(255, 255, 0, 255).rgb
                    ShowdownStatus.CONNECTED -> Color(0, 255, 0, 255).rgb
                }
            )
            drawScaledText(
                context = guiGraphics,
                text = Component.literal(showdownStatus!!.name.replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(
                        Locale.getDefault()
                    ) else it.toString()
                }),
                x = 205,
                y = 47
            )
        }
    }

    /**
     * Refreshes the showdown information displayed in the GUI.
     *
     * @param status The current status of the showdown service.
     * @param messages The log messages from the showdown service.
     * @param availableServices The list of available showdown services.
     */
    fun refreshShowdownInfo(status: ShowdownStatus, messages: List<String>, availableServices: List<ResourceLocation>) {
        if (showdownLogs[activeShowdownService] == null) {
            showdownLogs[activeShowdownService] = mutableListOf()
        }
        val log = showdownLogs[activeShowdownService]!!
        if (log.isNotEmpty()) {
            val lastMessage = log.last()
            val lastMessageIndex = messages.indexOf(lastMessage)
            if (lastMessageIndex != -1 && lastMessageIndex != messages.size - 1) {
                val messagesToAdd = messages.subList(lastMessageIndex + 1, messages.size)
                messagesToAdd.forEach {
                    outputWindow.addNewEntry(it.text())
                    log.add(it)
                }
            }
        }
        else {
            outputWindow.reset()
            messages.forEach { outputWindow.addNewEntry(it.text()) }
            log.addAll(messages)
        }
        showdownStatus = status
        when (status) {
            ShowdownStatus.CONNECTED -> {
                startButton.active = false
                stopButton.active = true
                restartButton.active = true
            }
            ShowdownStatus.DISCONNECTED -> {
                startButton.active = true
                stopButton.active = false
                restartButton.active = false
            }
            ShowdownStatus.RESTARTING -> {
                startButton.active = false
                stopButton.active = false
                restartButton.active = false
            }
        }
        this.availableServices = availableServices
    }

}

/**
 * List widget for displaying the output log of the Showdown service.
 */
class ShowdownOutputList : ObjectSelectionList<OutputEntry>(
    Minecraft.getInstance(),
    Minecraft.getInstance().window.guiScaledWidth - 280 - 10,
    Minecraft.getInstance().window.guiScaledHeight - 10 - 40,
    10,
    14
) {
    /**
     * Adds a new entry to the output list.
     *
     * @param text The text to add as a new entry.
     */
    fun addNewEntry(text: Component) {
        val line = text.copy()
        val textRenderer = Minecraft.getInstance().font
        val lines = Language.getInstance().getVisualOrder(textRenderer.splitter.splitLines(line, rowWidth, line.style))
        val fullyScrolledDown = maxScroll - scrollAmount < 10
        lines.forEach { addEntry(OutputEntry(it)) }
        if (fullyScrolledDown) {
            scrollAmount = maxScroll.toDouble()
        }
    }

    override fun getSelected(): OutputEntry? = null

    override fun getRowWidth(): Int {
        return Minecraft.getInstance().window.guiScaledWidth - 280 - 10 - 10
    }

    override fun getScrollbarPosition(): Int {
        return this.x + rowWidth + 5
    }

    override fun renderWidget(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        setRectangle(
            Minecraft.getInstance().window.guiScaledWidth - 280 - 10,
            Minecraft.getInstance().window.guiScaledHeight - 10 - 10 - 40,
            280,
            10
        )
        this.x = 280
        super.renderWidget(guiGraphics, mouseX, mouseY, partialTick)
    }

    /**
     * Resets the output list by clearing all entries.
     */
    fun reset() {
        super.clearEntries()
    }
}

/**
 * Entry for the output list, representing a single line of text.
 *
 * @param line The formatted line of text to display.
 */
class OutputEntry(val line: FormattedCharSequence) : Entry<OutputEntry>() {
    override fun getNarration() = "".text()
    override fun render(
        context: GuiGraphics,
        index: Int,
        rowTop: Int,
        rowLeft: Int,
        rowWidth: Int,
        rowHeight: Int,
        mouseX: Int,
        mouseY: Int,
        isHovered: Boolean,
        partialTicks: Float
    ) {
        drawScaledText(
            context,
            line,
            rowLeft,
            rowTop - 2
        )
    }

    override fun isFocused() = false
}

/**
 * Input widget for entering commands to the Showdown service.
 */
class ShowdownInputWidget : EditBox(
    Minecraft.getInstance().font,
    280,
    Minecraft.getInstance().window.guiScaledHeight - 40,
    Minecraft.getInstance().window.guiScaledWidth - 280 - 10,
    30,
    Component.literal("Enter command")
) {
    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        val result = super.keyPressed(keyCode, scanCode, modifiers)
        if (keyCode == 257 && this.value.isNotEmpty()) {
            CobblemonNetwork.sendToServer(ShowdownInputPacket(
                service = activeShowdownService,
                input = this.value
            ))
            deleteCharsToPos(0)
            CobblemonNetwork.sendToServer(RequestShowdownInfoPacket(activeShowdownService))
        }
        return result
    }
}
