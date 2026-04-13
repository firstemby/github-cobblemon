/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.scripted

import com.bedrockk.molang.runtime.MoLangRuntime
import com.cobblemon.mod.common.api.gui.scripted.ScriptedGuiMolangContext
import com.cobblemon.mod.common.api.gui.scripted.event.ClientEventProcessor
import com.cobblemon.mod.common.api.gui.scripted.event.WidgetEvent
import com.cobblemon.mod.common.api.gui.scripted.event.WidgetEventType
import com.cobblemon.mod.common.api.gui.scripted.layout.LayoutEngine
import com.cobblemon.mod.common.api.gui.scripted.layout.SizeExpression
import com.cobblemon.mod.common.api.gui.scripted.widget.WidgetDefinition
import com.cobblemon.mod.common.api.gui.scripted.widget.WidgetInstance
import com.cobblemon.mod.common.api.gui.scripted.widget.WidgetType
import com.cobblemon.mod.common.client.gui.scripted.renderer.NumberInputRenderer
import com.cobblemon.mod.common.client.gui.scripted.renderer.ListRenderer
import com.cobblemon.mod.common.client.gui.scripted.renderer.WidgetRendererRegistry
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation
import java.util.UUID

class ScriptedGuiWidgetTree(
    val guiId: ResourceLocation,
    val sessionId: UUID,
    val rootDefinition: WidgetDefinition,
    parameters: Map<String, String>,
    variables: Map<String, Double>
) {
    private val mutableVariables: MutableMap<String, Double> = variables.toMutableMap()
    val runtime: MoLangRuntime = ScriptedGuiMolangContext.createClientRuntime(guiId, sessionId, parameters, mutableVariables)
    val root: WidgetInstance = buildInstance(rootDefinition, null)
    private var guiTime: Float = 0f
    private var hoveredWidget: WidgetInstance? = null

    fun updateParameters(parameters: Map<String, String>) {
        ScriptedGuiMolangContext.updateClientParameters(runtime, parameters)
        rebuildInstances()
    }

    private fun rebuildInstances() {
        root.children.clear()
        for (childDef in rootDefinition.children) {
            root.children.add(buildInstance(childDef, root))
        }
    }

    fun updateVariables(variables: Map<String, Double>) {
        ScriptedGuiMolangContext.updateClientVariables(runtime, variables)
    }

    fun render(guiGraphics: GuiGraphics, screenWidth: Int, screenHeight: Int, mouseX: Double, mouseY: Double, delta: Float) {
        guiTime += delta
        ScriptedGuiMolangContext.updateClientFrame(runtime, guiTime, delta, screenWidth, screenHeight, mouseX, mouseY)

        // Update hover state
        val newHovered = ClientEventProcessor.hitTest(root, mouseX.toFloat(), mouseY.toFloat())
        if (newHovered != hoveredWidget) {
            hoveredWidget?.hovered = false
            newHovered?.hovered = true
            hoveredWidget = newHovered
        }

        // Layout
        LayoutEngine.layout(root, screenWidth.toFloat(), screenHeight.toFloat(), runtime)

        // Render tree depth-first
        renderWidget(root, guiGraphics, mouseX.toFloat(), mouseY.toFloat(), delta)
    }

    fun onClick(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val hit = ClientEventProcessor.hitTest(root, mouseX.toFloat(), mouseY.toFloat()) ?: run {
            com.cobblemon.mod.common.Cobblemon.LOGGER.info("[ScriptedGuiWidgetTree] onClick: hitTest returned null at ($mouseX, $mouseY)")
            return false
        }
        com.cobblemon.mod.common.Cobblemon.LOGGER.info("[ScriptedGuiWidgetTree] onClick: hit widget '${hit.definition.id}' type=${hit.definition.type} at ($mouseX, $mouseY)")

        // Handle number input clicks directly
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
        val hit = ClientEventProcessor.hitTest(root, mouseX.toFloat(), mouseY.toFloat()) ?: run {
            com.cobblemon.mod.common.Cobblemon.LOGGER.info("[ScriptedGuiWidgetTree] onScroll: hitTest returned null at ($mouseX, $mouseY)")
            return false
        }
        com.cobblemon.mod.common.Cobblemon.LOGGER.info("[ScriptedGuiWidgetTree] onScroll: hit='${hit.definition.id}' type=${hit.definition.type} amount=$amount")

        // Check if scrolling inside a SCROLL_PANEL — apply scroll offset
        val scrollPanel = findParentOfType(hit, WidgetType.SCROLL_PANEL)
        if (scrollPanel != null) {
            val scrollSpeed = 10f
            val contentHeight = getContentHeight(scrollPanel)
            scrollPanel.scrollOffset += (amount * scrollSpeed).toFloat()
            val maxScroll = 0f
            val minScroll = -(contentHeight - scrollPanel.computedHeight).coerceAtLeast(0f)
            scrollPanel.scrollOffset = scrollPanel.scrollOffset.coerceIn(minScroll, maxScroll)
            com.cobblemon.mod.common.Cobblemon.LOGGER.info("[ScriptedGuiWidgetTree] onScroll: scrollPanel='${scrollPanel.definition.id}' scrollOffset=${scrollPanel.scrollOffset} contentHeight=$contentHeight panelHeight=${scrollPanel.computedHeight} minScroll=$minScroll")
            return true
        }
        com.cobblemon.mod.common.Cobblemon.LOGGER.info("[ScriptedGuiWidgetTree] onScroll: no SCROLL_PANEL parent found, bubbling event")

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
            com.cobblemon.mod.common.Cobblemon.LOGGER.info("[ScriptedGuiWidgetTree] bubbleEvent: checking '${def.id}' type=${def.type} hasServer=$hasServer hasClient=$hasClient events=${def.events.keys} clientEvents=${def.clientEvents.keys}")
            if (hasServer || hasClient) {
                // Restore list item context if this widget is a child of a LIST
                restoreListItemContext(current)
                val event = WidgetEvent(type, def.id, data)
                com.cobblemon.mod.common.Cobblemon.LOGGER.info("[ScriptedGuiWidgetTree] bubbleEvent: dispatching ${type.id}/${type.clientId} on '${def.id}'")
                ClientEventProcessor.processEvent(event, def, runtime, sessionId)
                return
            }
            current = current.parent
        }
        com.cobblemon.mod.common.Cobblemon.LOGGER.info("[ScriptedGuiWidgetTree] bubbleEvent: no handler found for ${type.id}, walked entire tree from '${target.definition.id}'")
    }

    private fun restoreListItemContext(widget: WidgetInstance) {
        val parent = widget.parent ?: return
        com.cobblemon.mod.common.Cobblemon.LOGGER.info("[ScriptedGuiWidgetTree] restoreListItemContext: widget='${widget.definition.id}' parent='${parent.definition.id}' parentType=${parent.definition.type}")
        if (parent.definition.type == WidgetType.LIST) {
            val index = parent.children.indexOf(widget)
            com.cobblemon.mod.common.Cobblemon.LOGGER.info("[ScriptedGuiWidgetTree] restoreListItemContext: found LIST parent, childIndex=$index")
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
        // No external resources to clean up (unlike GraalVM contexts)
    }

    private fun renderWidget(widget: WidgetInstance, guiGraphics: GuiGraphics, mouseX: Float, mouseY: Float, delta: Float) {
        // Re-evaluate dynamic visibility (MoLang expressions like "v.page == 0")
        val visibleProp = widget.definition.getProperty("visible")
        if (visibleProp != null && !visibleProp.isStatic) {
            widget.visible = visibleProp.resolveBoolean(runtime)
        }
        if (!widget.visible) return
        val renderer = WidgetRendererRegistry.get(widget.definition.type)
        renderer?.render(widget, guiGraphics, runtime, mouseX, mouseY, delta)

        // Scroll panel: scissor clip and offset children
        val isScrollPanel = widget.definition.type == WidgetType.SCROLL_PANEL
        if (isScrollPanel) {
            val x = widget.absoluteX.toInt()
            val y = widget.absoluteY.toInt()
            val w = widget.computedWidth.toInt()
            val h = widget.computedHeight.toInt()
            guiGraphics.enableScissor(x, y, x + w, y + h)
            applyScrollOffset(widget, widget.scrollOffset)
        }

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

        if (isScrollPanel) {
            applyScrollOffset(widget, -widget.scrollOffset)
            guiGraphics.disableScissor()
        }
    }

    private fun applyScrollOffset(widget: WidgetInstance, offset: Float) {
        for (child in widget.children) {
            child.absoluteY += offset
            applyScrollOffset(child, offset)
        }
    }

    private fun getContentHeight(widget: WidgetInstance): Float {
        // Find the total height needed by all descendants
        // Walk down to find the actual content bounds (e.g., LIST children inside a scroll panel)
        var minY = Float.MAX_VALUE
        var maxY = Float.MIN_VALUE
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
