/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.gui.scripted

/**
 * Determines how a scripted GUI is displayed.
 *
 * OVERLAY: Renders on top of the game without capturing input (like the party overlay).
 * SCREEN: Opens as a full Minecraft Screen with input capture (like a menu).
 */
enum class GuiMode {
    OVERLAY,
    SCREEN;

    companion object {
        fun fromString(value: String): GuiMode {
            return when (value.lowercase()) {
                "overlay" -> OVERLAY
                "screen" -> SCREEN
                else -> OVERLAY
            }
        }
    }
}
