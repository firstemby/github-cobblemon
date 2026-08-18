/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.molangui

import com.bedrockk.molang.runtime.MoLangRuntime
import com.cobblemon.mod.common.api.molangui.MolangUiDefinition
import com.cobblemon.mod.common.api.molangui.event.ClientEventProcessor
import com.cobblemon.mod.common.api.molangui.event.WidgetEvent
import com.cobblemon.mod.common.api.molangui.event.WidgetEventType
import com.cobblemon.mod.common.api.molangui.layout.LayoutEngine
import com.cobblemon.mod.common.api.molangui.layout.SizeExpression
import com.cobblemon.mod.common.api.molangui.widget.WidgetDefinition
import com.cobblemon.mod.common.api.molangui.widget.WidgetInstance
import com.cobblemon.mod.common.api.molangui.widget.WidgetType
import com.cobblemon.mod.common.client.gui.molangui.renderer.NumberInputRenderer
import com.cobblemon.mod.common.client.gui.molangui.renderer.ListRenderer
import com.cobblemon.mod.common.client.gui.molangui.renderer.WidgetRendererRegistry
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation
import java.util.UUID

class MolangUiWidgetTree(
    val guiId: ResourceLocation,
    val sessionId: UUID,
    var rootDefinition: WidgetDefinition,
    parameters: Map<String, String>,
    variables: Map<String, Double>
) {
    private val mutableVariables: MutableMap<String, Double> = variables.toMutableMap()
    private val currentParameters: MutableMap<String, String> = parameters.toMutableMap()
    val runtime: MoLangRuntime = MolangUiClientRuntimeFactory.create(guiId, parameters, mutableVariables)
    var root: WidgetInstance = buildInstance(rootDefinition, null)
        private set
    private var guiTime: Float = 0f
    private var hoveredWidget: WidgetInstance? = null

    fun updateParameters(parameters: Map<String, String>) {
        currentParameters.putAll(parameters)
        MolangUiClientRuntimeFactory.updateParameters(runtime, parameters)
        rebuildInstances()
    }

    private fun rebuildInstances() {
        root.children.clear()
        for (childDef in rootDefinition.children) {
            root.children.add(buildInstance(childDef, root))
        }
    }

    fun updateVariables(variables: Map<String, Double>) {
        mutableVariables.putAll(variables)
        MolangUiClientRuntimeFactory.updateVariables(runtime, variables)
    }

    fun rebuildFromDefinition(newDefinition: MolangUiDefinition) {
        this.rootDefinition = newDefinition.root

        for ((key, value) in newDefinition.variables) {
            if (!mutableVariables.containsKey(key)) {
                mutableVariables[key] = value
            }
        }

        MolangUiClientRuntimeFactory.updateVariables(runtime, mutableVariables)

        MolangUiClientRuntimeFactory.updateParameters(runtime, currentParameters)

        root = buildInstance(rootDefinition, null)
    }

    fun render(guiGraphics: GuiGraphics, screenWidth: Int, screenHeight: Int, mouseX: Double, mouseY: Double, delta: Float) {
        guiTime += delta
        MolangUiClientRuntimeFactory.updateFrame(runtime, guiTime, delta, screenWidth, screenHeight, mouseX, mouseY)

        val newHovered = ClientEventProcessor.hitTest(root, mouseX.toFloat(), mouseY.toFloat())
        if (newHovered != hoveredWidget) {
            hoveredWidget?.hovered = false
            newHovered?.hovered = true
            hoveredWidget = newHovered
        }

        LayoutEngine.layout(root, screenWidth.toFloat(), screenHeight.toFloat(), runtime)

        renderWidget(root, guiGraphics, mouseX.toFloat(), mouseY.toFloat(), delta)
    }

    fun onClick(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val hit = ClientEventProcessor.hitTest(root, mouseX.toFloat(), mouseY.toFloat()) ?: return false

        if (hit.definition.type == WidgetType.NUMBER_INPUT) {
            if (NumberInputRenderer.handleClick(hit, runtime, mouseX.toFloat(), mouseY.toFloat())) {
                return true
            }
        }

        val data = mapOf("button" to button.toString())
        bubbleEvent(hit, WidgetEventType.CLICK, data)
        return true
    }

    fun onScroll(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        val hit = ClientEventProcessor.hitTest(root, mouseX.toFloat(), mouseY.toFloat()) ?: return false

        val scrollPanel = findParentOfType(hit, WidgetType.SCROLL_PANEL)
        if (scrollPanel != null) {
            val scrollSpeed = 10f
            val contentHeight = getContentHeight(scrollPanel)
            scrollPanel.scrollOffset += (amount * scrollSpeed).toFloat()
            val maxScroll = 0f
            val minScroll = -(contentHeight - scrollPanel.computedHeight).coerceAtLeast(0f)
            scrollPanel.scrollOffset = scrollPanel.scrollOffset.coerceIn(minScroll, maxScroll)
            return true
        }

        val data = mapOf("amount" to amount.toString())
        bubbleEvent(hit, WidgetEventType.SCROLL, data)
        return true
    }

    private fun findParentOfType(widget: WidgetInstance, type: WidgetType): WidgetInstance? {
        var current: WidgetInstance? = widget
        while (current != null) {
            if (current.definition.type == type) return current
            current = current.parent
        }
        return null
    }

    private fun bubbleEvent(target: WidgetInstance, type: WidgetEventType, data: Map<String, String>) {
        var current: WidgetInstance? = target
        while (current != null) {
            val def = current.definition
            val hasServer = def.events.containsKey(type.id)
            val hasClient = def.clientEvents.containsKey(type.clientId)
            if (hasServer || hasClient) {
                restoreListItemContext(current)
                val event = WidgetEvent(type, def.id, data)
                ClientEventProcessor.processEvent(event, def, runtime, sessionId)
                return
            }
            current = current.parent
        }
    }

    private fun restoreListItemContext(widget: WidgetInstance) {
        val parent = widget.parent ?: return
        if (parent.definition.type == WidgetType.LIST) {
            val index = parent.children.indexOf(widget)
            if (index >= 0) {
                val sourceType = parent.definition.getStringProperty("source_type") ?: "array"
                val dataItems = if (sourceType == "data") ListRenderer.parseDataSource(parent, runtime) else null
                ListRenderer.setItemContext(runtime, index, dataItems?.getOrNull(index))
            }
        }
    }

    fun onKeyPress(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        val target = root
        val hasHandler = target.definition.events.containsKey("on_key") || target.definition.clientEvents.containsKey("on_key_client")
        if (!hasHandler) return false
        val event = WidgetEvent(WidgetEventType.KEY, target.definition.id,
            mapOf("key" to keyCode.toString(), "scan" to scanCode.toString(), "modifiers" to modifiers.toString()))
        ClientEventProcessor.processEvent(event, target.definition, runtime, sessionId)
        return true
    }

    fun cleanup() {
    }

    private fun renderWidget(widget: WidgetInstance, guiGraphics: GuiGraphics, mouseX: Float, mouseY: Float, delta: Float) {
        val visibleProp = widget.definition.getProperty("visible")
        if (visibleProp != null && !visibleProp.isStatic) {
            widget.visible = visibleProp.resolveBoolean(runtime)
        }
        if (!widget.visible) return
        val renderer = WidgetRendererRegistry.get(widget.definition.type)
        renderer?.render(widget, guiGraphics, runtime, mouseX, mouseY, delta)

        val isScrollPanel = widget.definition.type == WidgetType.SCROLL_PANEL
        val scrollOffset = widget.scrollOffset
        if (isScrollPanel) {
            val x = widget.absoluteX.toInt()
            val y = widget.absoluteY.toInt()
            val w = widget.computedWidth.toInt()
            val h = widget.computedHeight.toInt()
            guiGraphics.enableScissor(x, y, x + w, y + h)
            applyScrollOffset(widget, scrollOffset)
        }

        try {
            if (widget.definition.type == WidgetType.LIST) {
                val sourceType = widget.definition.getStringProperty("source_type") ?: "array"
                val dataItems = if (sourceType == "data") ListRenderer.parseDataSource(widget, runtime) else null
                for ((index, child) in widget.children.withIndex()) {
                    val itemData = dataItems?.getOrNull(index)
                    ListRenderer.setItemContext(runtime, index, itemData)
                    renderWidget(child, guiGraphics, mouseX, mouseY, delta)
                }
            } else {
                for (child in widget.children) {
                    renderWidget(child, guiGraphics, mouseX, mouseY, delta)
                }
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

    private fun getContentHeight(widget: WidgetInstance): Float {
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        collectLeafBounds(widget, { y -> if (y < minY) minY = y }, { y -> if (y > maxY) maxY = y })
        return if (maxY > minY) maxY - minY else 0f
    }

    private fun collectLeafBounds(widget: WidgetInstance, onMinY: (Float) -> Unit, onMaxY: (Float) -> Unit) {
        if (widget.children.isEmpty()) {
            onMinY(widget.absoluteY)
            onMaxY(widget.absoluteY + widget.computedHeight)
        } else {
            for (child in widget.children) {
                collectLeafBounds(child, onMinY, onMaxY)
            }
        }
    }

    private fun buildInstance(definition: WidgetDefinition, parent: WidgetInstance?): WidgetInstance {
        val instance = WidgetInstance(definition, parent)

        val sizeRaw = definition.getProperty("size")?.raw
        if (sizeRaw is List<*> && sizeRaw.size == 2) {
            instance.widthExpr = SizeExpression.parse(sizeRaw[0] ?: 0)
            instance.heightExpr = SizeExpression.parse(sizeRaw[1] ?: 0)
        }

        val offsetRaw = definition.getProperty("offset")?.raw
        if (offsetRaw is List<*> && offsetRaw.size == 2) {
            instance.offsetXExpr = SizeExpression.parse(offsetRaw[0] ?: 0)
            instance.offsetYExpr = SizeExpression.parse(offsetRaw[1] ?: 0)
        }

        val visibleProp = definition.getProperty("visible")
        if (visibleProp != null) {
            instance.visible = visibleProp.resolveBoolean(runtime)
        }

        if (definition.type == WidgetType.LIST) {
            val expandedChildren = ListRenderer.expandListChildren(instance, runtime)
            for (childDef in expandedChildren) {
                instance.children.add(buildInstance(childDef, instance))
            }
        } else {
            for (childDef in definition.children) {
                instance.children.add(buildInstance(childDef, instance))
            }
        }

        return instance
    }
}
