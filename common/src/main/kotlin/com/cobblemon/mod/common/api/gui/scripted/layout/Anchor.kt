/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.gui.scripted.layout

enum class Anchor(val id: String, val xFactor: Float, val yFactor: Float) {
    TOP_LEFT("top_left", 0f, 0f),
    TOP_CENTER("top_center", 0.5f, 0f),
    TOP_RIGHT("top_right", 1f, 0f),
    CENTER_LEFT("center_left", 0f, 0.5f),
    CENTER("center", 0.5f, 0.5f),
    CENTER_RIGHT("center_right", 1f, 0.5f),
    BOTTOM_LEFT("bottom_left", 0f, 1f),
    BOTTOM_CENTER("bottom_center", 0.5f, 1f),
    BOTTOM_RIGHT("bottom_right", 1f, 1f);

    companion object {
        private val BY_ID = entries.associateBy { it.id }
        fun fromId(id: String): Anchor = BY_ID[id] ?: TOP_LEFT
    }
}
