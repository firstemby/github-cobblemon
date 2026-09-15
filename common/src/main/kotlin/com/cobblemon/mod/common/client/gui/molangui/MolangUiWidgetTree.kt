/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.molangui

import com.bedrockk.molang.runtime.MoLangRuntime
import com.mojang.blaze3d.systems.RenderSystem
import com.cobblemon.mod.common.client.render.RenderAlphaScope
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.molangui.MolangUiDefinition
import com.cobblemon.mod.common.api.molangui.ParameterValue
import com.cobblemon.mod.common.api.molangui.event.ClientEventProcessor
import com.cobblemon.mod.common.api.molangui.event.WidgetEvent
import com.cobblemon.mod.common.api.molangui.event.WidgetEventType
import com.cobblemon.mod.common.api.molangui.layout.LayoutEngine
import com.cobblemon.mod.common.api.molangui.widget.WidgetDefinition
import com.cobblemon.mod.common.api.molangui.widget.WidgetExpansion
import com.cobblemon.mod.common.api.molangui.widget.WidgetInstance
import com.cobblemon.mod.common.api.molangui.widget.WidgetType
import com.cobblemon.mod.common.client.gui.molangui.renderer.NumberInputRenderer
import com.cobblemon.mod.common.client.gui.molangui.renderer.WidgetRendererRegistry
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.Minecraft
import com.cobblemon.mod.common.util.lang
import net.minecraft.resources.ResourceLocation
import org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE
import java.util.UUID

class MolangUiWidgetTree(
    val guiId: ResourceLocation,
    val sessionId: UUID,
    var rootDefinition: WidgetDefinition,
    parameters: Map<String, ParameterValue>,
    variables: Map<String, Double>,
    private val nanoTime: () -> Long = System::nanoTime
) {
    private val startedAt = nanoTime()
    private var previousFrame = startedAt
    private val currentParameters = parameters.toMutableMap()
    val runtime: MoLangRuntime = MolangUiClientRuntimeFactory.create(guiId, parameters, variables)
    var root: WidgetInstance = WidgetExpansion.refresh(rootDefinition, runtime)
        private set
    private var hoveredPath: List<WidgetInstance> = emptyList()
    private var hoverRevision = 0L
    private var expansionError: String? = null
    private var closed = false
    private var lastWidth = 0
    private var lastHeight = 0

    fun updateParameters(parameters: Map<String, ParameterValue>) {
        MolangUiClientRuntimeFactory.updateParameters(runtime, parameters)
        currentParameters.putAll(parameters)
    }

    fun updateVariables(variables: Map<String, Double>) {
        MolangUiClientRuntimeFactory.updateVariables(runtime, variables)
    }

    fun rebuildFromDefinition(newDefinition: MolangUiDefinition) {
        this.rootDefinition = newDefinition.root

        val newVariables = newDefinition.variables.filterKeys { it.removePrefix("v.") !in runtime.environment.variable.map }
        MolangUiClientRuntimeFactory.updateVariables(runtime, newVariables)

        MolangUiClientRuntimeFactory.updateParameters(runtime, currentParameters)

        prepare(lastWidth, lastHeight)
    }

    internal fun prepare(width: Int, height: Int): Boolean {
        if (closed) return false
        lastWidth = width
        lastHeight = height
        try {
            root = WidgetExpansion.refresh(rootDefinition, runtime, root)
            fun visibility(widget: WidgetInstance, parentEnabled: Boolean) {
                widget.withItemContext(runtime) {
                    widget.visible = widget.definition.getProperty("visible")?.resolveBoolean(runtime) ?: true
                    widget.enabled = parentEnabled && (widget.definition.getProperty("enabled")?.resolveBoolean(runtime) ?: true)
                }
                widget.children.forEach { visibility(it, widget.enabled) }
            }
            visibility(root, true)
            LayoutEngine.layout(root, width.toFloat(), height.toFloat(), runtime)
            clampScrollOffsets(root)
            expansionError = null
            return true
        } catch (error: Exception) {
            clearHover()
            val reason = error.message ?: error.javaClass.simpleName
            if (reason != expansionError) Cobblemon.LOGGER.warn("Invalid Molang UI {}: {}", guiId, reason)
            expansionError = reason
            return false
        }
    }

    fun render(guiGraphics: GuiGraphics, screenWidth: Int, screenHeight: Int, mouseX: Double, mouseY: Double, delta: Float) {
        if (closed) return
        val now = nanoTime()
        val elapsed = ((now - startedAt).coerceAtLeast(0) / 1_000_000_000.0).toFloat()
        val frameDuration = ((now - previousFrame).coerceAtLeast(0) / 1_000_000_000.0).toFloat()
        previousFrame = now
        MolangUiClientRuntimeFactory.updateFrame(runtime, elapsed, frameDuration, screenWidth, screenHeight, mouseX, mouseY)
        if (!prepare(screenWidth, screenHeight)) {
            if (!closed) guiGraphics.drawString(Minecraft.getInstance().font, lang("molang_ui.invalid_view"), 8, 8, 0xFFFFFF)
            return
        }

        if (!updateHover(mouseX.toFloat(), mouseY.toFloat())) return

        renderWidget(root, guiGraphics, mouseX.toFloat(), mouseY.toFloat(), delta)
    }

    private fun updateHover(mouseX: Float, mouseY: Float): Boolean {
        val target = ClientEventProcessor.hitTest(root, mouseX, mouseY)
        val next = generateSequence(target) { it.parent }.toList().asReversed()
        if (next == hoveredPath) return true
        val previous = hoveredPath
        val shared = previous.zip(next).takeWhile { (old, new) -> old === new }.size
        val exited = previous.drop(shared).asReversed()
        val entered = next.drop(shared)
        exited.forEach { it.hovered = false }
        entered.forEach { it.hovered = true }
        hoveredPath = next
        val revision = ++hoverRevision
        var handled = false
        for (widget in exited) {
            handled = dispatchHover(widget, WidgetEventType.HOVER_EXIT) || handled
            if (closed || hoverRevision != revision) return false
        }
        for (widget in entered) {
            handled = dispatchHover(widget, WidgetEventType.HOVER_ENTER) || handled
            if (closed || hoverRevision != revision) return false
        }
        return !handled || prepare(lastWidth, lastHeight)
    }

    private fun dispatchHover(widget: WidgetInstance, type: WidgetEventType): Boolean {
        val definition = widget.definition
        if (type.id !in definition.events && type.clientId !in definition.clientEvents) return false
        widget.withItemContext(runtime) {
            ClientEventProcessor.processEvent(WidgetEvent(type, definition.id), definition, runtime, sessionId)
        }
        return true
    }

    private fun clearHover() {
        hoveredPath.forEach { it.hovered = false }
        hoveredPath = emptyList()
        hoverRevision++
    }

    fun onClick(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (!prepare(lastWidth, lastHeight)) return false
        val hit = ClientEventProcessor.hitTest(root, mouseX.toFloat(), mouseY.toFloat()) ?: return false

        if (button == 0 && hit.definition.type == WidgetType.NUMBER_INPUT) {
            var contentY = mouseY.toFloat()
            var parent = hit.parent
            while (parent != null) {
                if (parent.definition.type == WidgetType.SCROLL_PANEL) contentY -= parent.scrollOffset
                parent = parent.parent
            }
            if (hit.withItemContext(runtime) {
                NumberInputRenderer.handleClick(hit, runtime, mouseX.toFloat(), contentY) { value ->
                    bubbleEvent(hit, WidgetEventType.CHANGE, mapOf("value" to value.toDouble()))
                }
            }) {
                return true
            }
        }

        val data = mapOf("button" to button.toDouble())
        return bubbleEvent(hit, WidgetEventType.CLICK, data)
    }

    fun onScroll(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        if (!amount.isFinite() || !prepare(lastWidth, lastHeight)) return false
        val hit = ClientEventProcessor.hitTest(root, mouseX.toFloat(), mouseY.toFloat()) ?: return false

        val scrollPanel = findParentOfType(hit, WidgetType.SCROLL_PANEL)
        if (scrollPanel != null) {
            scrollPanel.scrollOffset = (scrollPanel.scrollOffset.toDouble() + amount * 10.0)
                .coerceIn(minimumScroll(scrollPanel).toDouble(), 0.0).toFloat()
            bubbleEvent(hit, WidgetEventType.SCROLL, mapOf("amount" to amount))
            return true
        }

        val data = mapOf("amount" to amount)
        return bubbleEvent(hit, WidgetEventType.SCROLL, data)
    }

    private fun findParentOfType(widget: WidgetInstance, type: WidgetType): WidgetInstance? {
        var current: WidgetInstance? = widget
        while (current != null) {
            if (current.definition.type == type) return current
            current = current.parent
        }
        return null
    }

    private fun bubbleEvent(target: WidgetInstance, type: WidgetEventType, data: Map<String, Double>): Boolean {
        var current: WidgetInstance? = target
        while (current != null) {
            val def = current.definition
            val hasServer = def.events.containsKey(type.id)
            val hasClient = def.clientEvents.containsKey(type.clientId)
            if (hasServer || hasClient) {
                val event = WidgetEvent(type, target.definition.id, data)
                target.withItemContext(runtime) { ClientEventProcessor.processEvent(event, def, runtime, sessionId) }
                return true
            }
            current = current.parent
        }
        return false
    }

    fun onKeyPress(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (keyCode == GLFW_KEY_ESCAPE || !prepare(lastWidth, lastHeight) || !root.visible || !root.enabled) return false
        val target = root
        val hasHandler = target.definition.events.containsKey("on_key") || target.definition.clientEvents.containsKey("on_key_client")
        if (!hasHandler) return false
        val event = WidgetEvent(WidgetEventType.KEY, target.definition.id,
            mapOf("key" to keyCode.toDouble(), "scan" to scanCode.toDouble(), "modifiers" to modifiers.toDouble()))
        target.withItemContext(runtime) { ClientEventProcessor.processEvent(event, target.definition, runtime, sessionId) }
        return true
    }

    fun cleanup() {
        closed = true
        clearHover()
        root.children.clear()
        root.rendererState = null
    }

    private fun renderWidget(widget: WidgetInstance, guiGraphics: GuiGraphics, mouseX: Float, mouseY: Float, delta: Float): Unit = widget.withItemContext(runtime) {
        if (!widget.visible) return
        val opacity = widget.definition.getProperty("opacity")?.resolveFloat(runtime) ?: 1f
        if (!opacity.isFinite() || opacity <= 0f) return
        if (opacity >= 1f) {
            renderContents(widget, guiGraphics, mouseX, mouseY, delta)
            return
        }
        val color = RenderSystem.getShaderColor().copyOf()
        try {
            guiGraphics.setColor(color[0], color[1], color[2], color[3] * opacity)
            renderContents(widget, guiGraphics, mouseX, mouseY, delta)
        } finally {
            guiGraphics.setColor(color[0], color[1], color[2], color[3])
        }
    }

    private fun renderContents(widget: WidgetInstance, guiGraphics: GuiGraphics, mouseX: Float, mouseY: Float, delta: Float) {
        val renderer = WidgetRendererRegistry.get(widget.definition.type)
        if (widget.definition.type == WidgetType.POKEMON_RENDER || widget.definition.type == WidgetType.ITEM_SLOT) {
            RenderAlphaScope.render { renderer?.render(widget, guiGraphics, runtime, mouseX, mouseY, delta) }
        } else {
            renderer?.render(widget, guiGraphics, runtime, mouseX, mouseY, delta)
        }
        val isScrollPanel = widget.definition.type == WidgetType.SCROLL_PANEL
        val scrollOffset = widget.scrollOffset
        if (isScrollPanel) {
            val x = widget.absoluteX.toInt()
            val y = widget.absoluteY.toInt()
            val w = widget.computedWidth.toInt()
            val h = widget.computedHeight.toInt()
            guiGraphics.enableScissor(x, y, x + w, y + h)
        }

        try {
            if (isScrollPanel) applyScrollOffset(widget, scrollOffset)
            for (child in widget.children) {
                renderWidget(child, guiGraphics, mouseX, mouseY, delta)
            }
        } finally {
            if (isScrollPanel) {
                applyScrollOffset(widget, -scrollOffset)
                guiGraphics.disableScissor()
            }
        }
    }

    private fun applyScrollOffset(widget: WidgetInstance, offset: Float) {
        for (child in widget.children) {
            child.absoluteY += offset
            applyScrollOffset(child, offset)
        }
    }

    private fun clampScrollOffsets(widget: WidgetInstance) {
        if (!widget.visible) return
        if (widget.definition.type == WidgetType.SCROLL_PANEL) {
            widget.scrollOffset = if (widget.scrollOffset.isFinite()) {
                widget.scrollOffset.coerceIn(minimumScroll(widget), 0f)
            } else 0f
        }
        widget.children.forEach(::clampScrollOffsets)
    }

    private fun minimumScroll(widget: WidgetInstance): Float {
        val viewportBottom = widget.absoluteY.toDouble() + widget.computedHeight
        var contentBottom = viewportBottom
        fun collect(node: WidgetInstance) {
            if (!node.visible) return
            contentBottom = maxOf(contentBottom, node.absoluteY.toDouble() + node.computedHeight)
            if (node.definition.type != WidgetType.SCROLL_PANEL) node.children.forEach(::collect)
        }
        widget.children.forEach(::collect)
        return (viewportBottom - contentBottom).toFloat()
    }
}
