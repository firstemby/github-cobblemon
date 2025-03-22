package com.cobblemon.mod.common.client.gui.debug

import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.client.gui.CobblemonRenderable
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.util.lang
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.ObjectSelectionList
import net.minecraft.client.gui.components.ObjectSelectionList.Entry
import net.minecraft.client.gui.screens.Screen
import net.minecraft.locale.Language
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence
import java.awt.Color

class ShowdownDebugGUI : Screen(lang("ui.debug.showdown")), CobblemonRenderable {

    var outputWindow = addRenderableWidget(ShowdownOutputList())

    init {
        addRenderableWidget(
            Button.builder(Component.literal("Start")) { button ->
                println("Starting connection to showdown")
            }.bounds(10, 10, 50, 20).build()
        )

        addRenderableWidget(
            Button.builder(Component.literal("Stop")) { button ->
                for (i in 1..10) {
                    outputWindow.addNewEntry("test".text())
                }
            }.bounds(70, 10, 50, 20).build()
        )

        addRenderableWidget(
            Button.builder(Component.literal("Restart")) { button ->
                println("Restarting connection to showdown")
            }.bounds(130, 10, 50, 20).build()
        )

        addRenderableWidget(
            Button.builder(Component.literal("Start Queue")) { button ->
                println("Queue button pressed")
                button.message = Component.literal("Cancel Queue")
            }.bounds(10, 40, 100, 20).build()
        )

        addRenderableWidget(ShowdownInputWidget())
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        guiGraphics.fill(0, 0, width, height, Color(0, 0, 0, 100).rgb)
        super.render(guiGraphics, mouseX, mouseY, partialTick)
        guiGraphics.fill(190, 15, 200, 25, Color(255, 0, 0, 255).rgb)
        drawScaledText(
            context = guiGraphics,
            text = Component.literal("Disconnected"),
            x = 205,
            y = 17
        )
    }

}

class ShowdownOutputList : ObjectSelectionList<OutputEntry>(
    Minecraft.getInstance(),
    Minecraft.getInstance().window.guiScaledWidth - 280 - 10,
    Minecraft.getInstance().window.guiScaledHeight - 10 - 40,
    10,
    14
) {
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
        setRectangle(Minecraft.getInstance().window.guiScaledWidth - 280 - 10, Minecraft.getInstance().window.guiScaledHeight - 10 - 10 - 40, 280, 10)
        this.x = 280
        super.renderWidget(guiGraphics, mouseX, mouseY, partialTick)
    }
}

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
        if (keyCode == 257) {
            println(this.value)
            deleteCharsToPos(0)
        }
        return result
    }
}
