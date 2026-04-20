/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.camera.track

import com.bedrockk.molang.runtime.MoLangRuntime
import com.bedrockk.molang.runtime.value.MoValue
import com.cobblemon.mod.common.api.camera.model.CameraTrack
import com.cobblemon.mod.common.api.camera.model.TrackOutput
import net.minecraft.world.phys.Vec3

/**
 * Track that fires timed GUI actions (open, close, update) during camera timelines.
 *
 * This track produces no camera outputs itself (no position/focus/rotation).
 * The actions are executed by the CameraExecutor when their time is crossed.
 */
data class GuiTrack(
    val actions: List<TimedGuiAction>
) : CameraTrack {
    override val trackType = "gui"

    override val duration: Float
        get() = actions.maxOfOrNull { it.time } ?: 0f

    override fun evaluate(
        time: Float,
        context: Map<String, MoValue>,
        runtime: MoLangRuntime,
        origin: Vec3
    ): List<TrackOutput> {
        // GUI actions are handled by the engine/executor, not via TrackOutput
        return emptyList()
    }

    init {
        require(actions.zipWithNext().all { (a, b) -> a.time <= b.time }) {
            "GuiTrack actions must be sorted by time"
        }
    }
}

/**
 * Determines the type of GUI action to fire.
 */
enum class GuiActionType {
    OPEN,
    CLOSE,
    UPDATE_ARGS
}

/**
 * A timed GUI action within a timeline.
 *
 * @param time The time in seconds when this action should fire
 * @param action The type of action
 * @param guiId The scripted GUI resource location (e.g., "cobblemon:battle_intro")
 * @param args Static argument values
 * @param argsExpressions MoLang expressions to evaluate at fire time, merged with static args
 */
data class TimedGuiAction(
    val time: Float,
    val action: GuiActionType,
    val guiId: String,
    val args: Map<String, String> = emptyMap(),
    val argsExpressions: Map<String, String> = emptyMap()
) {
    init {
        require(time >= 0f) { "GUI action time must be non-negative, got $time" }
    }
}

/**
 * Represents a GUI action that has fired (time crossed).
 */
data class FiredGuiAction(
    val action: GuiActionType,
    val guiId: String,
    val args: Map<String, String>,
    val argsExpressions: Map<String, String>
)
