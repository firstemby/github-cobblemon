/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.model

data class SceneGuiTrack(
    val actions: List<SceneGuiAction>
) {
    init {
        require(actions.zipWithNext().all { (a, b) -> a.time <= b.time }) {
            "SceneGuiTrack actions must be sorted by time"
        }
    }
}

data class SceneGuiAction(
    val time: Float,
    val type: SceneGuiActionType,
    val guiId: String,
    val args: Map<String, SceneGuiArgValue> = emptyMap()
) {
    init {
        require(time.isFinite() && time >= 0f) { "SceneGuiAction time must be non-negative, got $time" }
    }
}

enum class SceneGuiActionType {
    OPEN,
    CLOSE,
    UPDATE_ARGS
}

sealed class SceneGuiArgValue {
    data class Expression(val value: String) : SceneGuiArgValue()

    data class StringLiteral(val value: String) : SceneGuiArgValue()
}
