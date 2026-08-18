/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molangui.container

data class SlotLayout(
    val offsetX: Int,
    val offsetY: Int,
    val columns: Int,
    val rows: Int,
    val slotWidth: Int = 18,
    val slotHeight: Int = 18,
    val spacingX: Int = 0,
    val spacingY: Int = 0
) {
    val slotCount: Int get() = columns * rows
}
