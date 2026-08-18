/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molangui.layout

import com.cobblemon.mod.common.api.molangui.widget.WidgetInstance
import com.cobblemon.mod.common.api.molangui.widget.WidgetProperty
import com.bedrockk.molang.runtime.MoLangRuntime

object LayoutEngine {

    fun layout(root: WidgetInstance, screenWidth: Float, screenHeight: Float, runtime: MoLangRuntime) {
        root.computedWidth = root.widthExpr.resolve(screenWidth, runtime)
        root.computedHeight = root.heightExpr.resolve(screenHeight, runtime)
        val anchor = root.definition.getAnchor()
        val offsetX = root.offsetXExpr.resolve(screenWidth, runtime)
        val offsetY = root.offsetYExpr.resolve(screenHeight, runtime)
        root.absoluteX = (screenWidth - root.computedWidth) * anchor.xFactor + offsetX
        root.absoluteY = (screenHeight - root.computedHeight) * anchor.yFactor + offsetY
        layoutChildren(root, runtime)
    }

    private fun layoutChildren(parent: WidgetInstance, runtime: MoLangRuntime) {
        val layout = parent.definition.getStringProperty("layout")
        val spacing = parent.definition.getProperty("spacing")?.resolveFloat(runtime) ?: 0f
        val padding = parsePadding(parent.definition.getProperty("padding"), runtime)

        val innerWidth = parent.computedWidth - padding.left - padding.right
        val innerHeight = parent.computedHeight - padding.top - padding.bottom
        val innerX = parent.absoluteX + padding.left
        val innerY = parent.absoluteY + padding.top

        for (child in parent.children) {
            val visibleProp = child.definition.getProperty("visible")
            if (visibleProp != null && !visibleProp.isStatic) child.visible = visibleProp.resolveBoolean(runtime)
            if (!child.visible) continue
            child.computedWidth = child.widthExpr.resolve(innerWidth, runtime)
            child.computedHeight = child.heightExpr.resolve(innerHeight, runtime)
        }

        when (layout) {
            "stack_vertical" -> layoutStackVertical(parent, innerX, innerY, innerWidth, innerHeight, spacing, runtime)
            "stack_horizontal" -> layoutStackHorizontal(parent, innerX, innerY, innerWidth, innerHeight, spacing, runtime)
            "grid" -> layoutGrid(parent, innerX, innerY, innerWidth, innerHeight, spacing, runtime)
            else -> layoutAnchored(parent, innerX, innerY, innerWidth, innerHeight, runtime)
        }

        for (child in parent.children) {
            if (!child.visible) continue
            layoutChildren(child, runtime)
        }
    }

    private fun layoutAnchored(parent: WidgetInstance, innerX: Float, innerY: Float, innerWidth: Float, innerHeight: Float, runtime: MoLangRuntime) {
        for (child in parent.children) {
            if (!child.visible) continue
            val anchor = child.definition.getAnchor()
            val offsetX = child.offsetXExpr.resolve(innerWidth, runtime)
            val offsetY = child.offsetYExpr.resolve(innerHeight, runtime)
            child.absoluteX = innerX + (innerWidth - child.computedWidth) * anchor.xFactor + offsetX
            child.absoluteY = innerY + (innerHeight - child.computedHeight) * anchor.yFactor + offsetY
        }
    }

    private fun layoutStackVertical(parent: WidgetInstance, innerX: Float, innerY: Float, innerWidth: Float, innerHeight: Float, spacing: Float, runtime: MoLangRuntime) {
        val visibleChildren = parent.children.filter { it.visible }
        resolveFillSizes(visibleChildren, innerHeight, spacing, isVertical = true, runtime = runtime)
        var currentY = innerY
        for (child in visibleChildren) {
            child.absoluteX = innerX + child.offsetXExpr.resolve(innerWidth, runtime)
            child.absoluteY = currentY + child.offsetYExpr.resolve(innerHeight, runtime)
            currentY += child.computedHeight + spacing
        }
    }

    private fun layoutStackHorizontal(parent: WidgetInstance, innerX: Float, innerY: Float, innerWidth: Float, innerHeight: Float, spacing: Float, runtime: MoLangRuntime) {
        val visibleChildren = parent.children.filter { it.visible }
        resolveFillSizes(visibleChildren, innerWidth, spacing, isVertical = false, runtime = runtime)
        var currentX = innerX
        for (child in visibleChildren) {
            child.absoluteX = currentX + child.offsetXExpr.resolve(innerWidth, runtime)
            child.absoluteY = innerY + child.offsetYExpr.resolve(innerHeight, runtime)
            currentX += child.computedWidth + spacing
        }
    }

    private fun layoutGrid(parent: WidgetInstance, innerX: Float, innerY: Float, innerWidth: Float, innerHeight: Float, spacing: Float, runtime: MoLangRuntime) {
        val columns = parent.definition.getProperty("columns")?.resolveInt(runtime) ?: 1
        val visibleChildren = parent.children.filter { it.visible }
        val cellWidth = (innerWidth - spacing * (columns - 1)) / columns
        var col = 0
        var currentY = innerY
        var rowHeight = 0f
        for (child in visibleChildren) {
            if (child.widthExpr is SizeExpression.Fill) child.computedWidth = cellWidth
            child.absoluteX = innerX + col * (cellWidth + spacing)
            child.absoluteY = currentY
            rowHeight = maxOf(rowHeight, child.computedHeight)
            col++
            if (col >= columns) {
                col = 0
                currentY += rowHeight + spacing
                rowHeight = 0f
            }
        }
    }

    private fun resolveFillSizes(children: List<WidgetInstance>, totalSpace: Float, spacing: Float, isVertical: Boolean, runtime: MoLangRuntime) {
        val totalSpacing = spacing * (children.size - 1).coerceAtLeast(0)
        var usedSpace = totalSpacing
        var fillCount = 0
        for (child in children) {
            val expr = if (isVertical) child.heightExpr else child.widthExpr
            if (expr is SizeExpression.Fill) {
                fillCount++
            } else {
                usedSpace += if (isVertical) child.computedHeight else child.computedWidth
            }
        }
        if (fillCount > 0) {
            val fillSize = ((totalSpace - usedSpace) / fillCount).coerceAtLeast(0f)
            for (child in children) {
                val expr = if (isVertical) child.heightExpr else child.widthExpr
                if (expr is SizeExpression.Fill) {
                    if (isVertical) child.computedHeight = fillSize else child.computedWidth = fillSize
                }
            }
        }
    }

    data class Padding(val top: Float, val right: Float, val bottom: Float, val left: Float)

    private fun parsePadding(property: WidgetProperty?, runtime: MoLangRuntime): Padding {
        if (property == null) return Padding(0f, 0f, 0f, 0f)
        val raw = property.raw
        if (raw is List<*> && raw.size == 4) {
            return Padding(
                (raw[0] as? Number)?.toFloat() ?: 0f,
                (raw[1] as? Number)?.toFloat() ?: 0f,
                (raw[2] as? Number)?.toFloat() ?: 0f,
                (raw[3] as? Number)?.toFloat() ?: 0f
            )
        }
        return Padding(0f, 0f, 0f, 0f)
    }
}
