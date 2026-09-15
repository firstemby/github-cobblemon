/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molangui.widget

import com.cobblemon.mod.common.api.molangui.layout.SizeExpression
import com.bedrockk.molang.runtime.MoLangRuntime
import com.bedrockk.molang.runtime.struct.QueryStruct

class WidgetInstance(
    val definition: WidgetDefinition,
    val parent: WidgetInstance? = null
) {
    var absoluteX: Float = 0f
    var absoluteY: Float = 0f
    var computedWidth: Float = 0f
    var computedHeight: Float = 0f

    var widthExpr: SizeExpression = SizeExpression.Pixels(0f)
    var heightExpr: SizeExpression = SizeExpression.Pixels(0f)
    var offsetXExpr: SizeExpression = SizeExpression.Pixels(0f)
    var offsetYExpr: SizeExpression = SizeExpression.Pixels(0f)

    internal val paddingProperties: List<WidgetProperty> =
        (definition.getProperty("padding")?.raw as? List<*>)?.map {
            when (it) {
                is Number -> WidgetProperty.literal(it)
                is WidgetExpression -> WidgetProperty.molang(it.source)
                else -> throw IllegalArgumentException("invalid padding at '${definition.id}'")
            }
        } ?: List(4) { WidgetProperty.literal(0) }

    val children: MutableList<WidgetInstance> = mutableListOf()

    var visible: Boolean = true
    var enabled: Boolean = true
    var hovered: Boolean = false

    var scrollOffset: Float = 0f
    var rendererState: Any? = null
    var itemContext: QueryStruct? = null

    inline fun <T> withItemContext(runtime: MoLangRuntime, action: () -> T): T =
        withItemContext(runtime, itemContext, action)

    fun containsPoint(x: Float, y: Float): Boolean {
        return x >= absoluteX && x <= absoluteX + computedWidth &&
               y >= absoluteY && y <= absoluteY + computedHeight
    }
}

inline fun <T> withItemContext(runtime: MoLangRuntime, context: QueryStruct?, action: () -> T): T {
    val functions = runtime.environment.query.functions
    val previous = functions.remove("item")
    if (context != null) runtime.environment.query.addFunction("item") { context }
    try {
        return action()
    } finally {
        functions.remove("item")
        if (previous != null) functions["item"] = previous
    }
}
