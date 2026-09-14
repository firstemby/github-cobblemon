/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic.viewport

/** Render-thread only; coordinates are GLFW screen pixels with a top-left origin. */
object EditorViewportState {
    var active: Boolean = false
        private set
    var frameX: Int = 0; private set
    var frameY: Int = 0; private set
    var frameWidth: Int = 0; private set
    var frameHeight: Int = 0; private set
    var windowWidth: Int = 0; private set
    var windowHeight: Int = 0; private set

    fun publish(x: Int, y: Int, w: Int, h: Int, winW: Int, winH: Int) {
        frameX = x; frameY = y; frameWidth = w; frameHeight = h
        windowWidth = winW; windowHeight = winH
        active = w > 1 && h > 1
    }

    fun deactivate() {
        active = false
    }
}
