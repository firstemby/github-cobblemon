/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molangui.layout

import com.cobblemon.mod.common.api.molangui.widget.WidgetInstance
import com.bedrockk.molang.runtime.MoLangRuntime

object LayoutEngine {

    fun layout(root: WidgetInstance, screenWidth: Float, screenHeight: Float, runtime: MoLangRuntime): Unit = root.withItemContext(runtime) {
        require(screenWidth.isFinite() && screenHeight.isFinite() && screenWidth >= 0f && screenHeight >= 0f) {
            "invalid screen dimensions"
        }
        if (!root.visible) return@withItemContext
        root.computedWidth = root.widthExpr.resolve(screenWidth, runtime)
        root.computedHeight = root.heightExpr.resolve(screenHeight, runtime)
        val anchor = root.definition.getAnchor()
        val offsetX = root.offsetXExpr.resolve(screenWidth, runtime)
        val offsetY = root.offsetYExpr.resolve(screenHeight, runtime)
        root.absoluteX = (screenWidth - root.computedWidth) * anchor.xFactor + offsetX
        root.absoluteY = (screenHeight - root.computedHeight) * anchor.yFactor + offsetY
        validateBounds(root)
        layoutChildren(root, runtime)
    }

    private fun layoutChildren(parent: WidgetInstance, runtime: MoLangRuntime): Unit = parent.withItemContext(runtime) {
        val layout = parent.definition.getStringProperty("layout")
        val spacing = parent.definition.getProperty("spacing")?.resolveFloat(runtime) ?: 0f
        require(spacing.isFinite()) { "non-finite spacing at '${parent.definition.id}'" }
        val padding = parent.paddingProperties.map { it.resolveFloat(runtime) }
        require(padding.size == 4 && padding.all { it.isFinite() && it >= 0f }) {
            "invalid padding at '${parent.definition.id}'"
        }
        val innerWidth = (parent.computedWidth - padding[3] - padding[1]).coerceAtLeast(0f)
        val innerHeight = (parent.computedHeight - padding[0] - padding[2]).coerceAtLeast(0f)
        val innerX = parent.absoluteX + padding[3]
        val innerY = parent.absoluteY + padding[0]

        for (child in parent.children) {
            if (!child.visible) continue
            child.computedWidth = child.withItemContext(runtime) { child.widthExpr.resolve(innerWidth, runtime) }
            child.computedHeight = child.withItemContext(runtime) { child.heightExpr.resolve(innerHeight, runtime) }
        }

        when (layout) {
            "stack_vertical" -> layoutStackVertical(parent, innerX, innerY, innerWidth, innerHeight, spacing, runtime)
            "stack_horizontal" -> layoutStackHorizontal(parent, innerX, innerY, innerWidth, innerHeight, spacing, runtime)
            "grid" -> layoutGrid(parent, innerX, innerY, innerWidth, spacing, runtime)
            else -> layoutAnchored(parent, innerX, innerY, innerWidth, innerHeight, runtime)
        }

        for (child in parent.children) {
            if (!child.visible) continue
            validateBounds(child)
            layoutChildren(child, runtime)
        }
    }

    private fun layoutAnchored(parent: WidgetInstance, innerX: Float, innerY: Float, innerWidth: Float, innerHeight: Float, runtime: MoLangRuntime) {
        for (child in parent.children) {
            if (!child.visible) continue
            val anchor = child.definition.getAnchor()
            val offsetX = child.withItemContext(runtime) { child.offsetXExpr.resolve(innerWidth, runtime) }
            val offsetY = child.withItemContext(runtime) { child.offsetYExpr.resolve(innerHeight, runtime) }
            child.absoluteX = innerX + (innerWidth - child.computedWidth) * anchor.xFactor + offsetX
            child.absoluteY = innerY + (innerHeight - child.computedHeight) * anchor.yFactor + offsetY
        }
    }

    private fun layoutStackVertical(parent: WidgetInstance, innerX: Float, innerY: Float, innerWidth: Float, innerHeight: Float, spacing: Float, runtime: MoLangRuntime) {
        val visibleChildren = parent.children.filter { it.visible }
        resolveFillSizes(visibleChildren, innerHeight, spacing, isVertical = true)
        var currentY = innerY
        for (child in visibleChildren) {
            child.absoluteX = innerX + child.withItemContext(runtime) { child.offsetXExpr.resolve(innerWidth, runtime) }
            child.absoluteY = currentY + child.withItemContext(runtime) { child.offsetYExpr.resolve(innerHeight, runtime) }
            currentY += child.computedHeight + spacing
        }
    }

    private fun layoutStackHorizontal(parent: WidgetInstance, innerX: Float, innerY: Float, innerWidth: Float, innerHeight: Float, spacing: Float, runtime: MoLangRuntime) {
        val visibleChildren = parent.children.filter { it.visible }
        resolveFillSizes(visibleChildren, innerWidth, spacing, isVertical = false)
        var currentX = innerX
        for (child in visibleChildren) {
            child.absoluteX = currentX + child.withItemContext(runtime) { child.offsetXExpr.resolve(innerWidth, runtime) }
            child.absoluteY = innerY + child.withItemContext(runtime) { child.offsetYExpr.resolve(innerHeight, runtime) }
            currentX += child.computedWidth + spacing
        }
    }

    private fun layoutGrid(parent: WidgetInstance, innerX: Float, innerY: Float, innerWidth: Float, spacing: Float, runtime: MoLangRuntime) {
        val columns = (parent.definition.getProperty("columns")?.resolveInt(runtime) ?: 1).coerceIn(1, 256)
        val visibleChildren = parent.children.filter { it.visible }
        val cellWidth = ((innerWidth - spacing * (columns - 1)) / columns).coerceAtLeast(0f)
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

    private fun resolveFillSizes(children: List<WidgetInstance>, totalSpace: Float, spacing: Float, isVertical: Boolean) {
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

    private fun validateBounds(widget: WidgetInstance) {
        fun validAxis(position: Float, size: Float): Boolean =
            position.isFinite() && size.isFinite() && size >= 0f && size.toDouble() <= Int.MAX_VALUE &&
                position.toDouble() >= Int.MIN_VALUE && position.toDouble() + size.toDouble() <= Int.MAX_VALUE

        require(validAxis(widget.absoluteX, widget.computedWidth) && validAxis(widget.absoluteY, widget.computedHeight)) {
            "widget '${widget.definition.id}' has invalid or overflowing bounds"
        }
    }
}
