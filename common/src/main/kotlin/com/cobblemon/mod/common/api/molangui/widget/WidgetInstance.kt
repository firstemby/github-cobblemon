/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molangui.widget

import com.cobblemon.mod.common.api.molangui.animation.TransitionState
import com.cobblemon.mod.common.api.molangui.layout.SizeExpression

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

    val children: MutableList<WidgetInstance> = mutableListOf()
    val transitions: MutableMap<String, TransitionState> = mutableMapOf()

    var visible: Boolean = true
    var hovered: Boolean = false
    var pressed: Boolean = false
    var focused: Boolean = false

    var enterProgress: Float = -1f
    var exitProgress: Float = -1f
    var scrollOffset: Float = 0f

    fun containsPoint(x: Float, y: Float): Boolean {
        return x >= absoluteX && x <= absoluteX + computedWidth &&
               y >= absoluteY && y <= absoluteY + computedHeight
    }
}
