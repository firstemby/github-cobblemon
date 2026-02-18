/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.camera.model

import com.bedrockk.molang.runtime.MoLangRuntime
import com.bedrockk.molang.runtime.value.MoValue
import com.cobblemon.mod.common.api.camera.CameraRotation
import com.cobblemon.mod.common.api.molang.ExpressionLike
import net.minecraft.world.phys.Vec3

/**
 * Type-safe track evaluation result.
 */
sealed class TrackOutput {
    data class Position(val position: Vec3) : TrackOutput()
    data class Focus(val target: CameraFocus) : TrackOutput()
    data class Rotation(val rotation: CameraRotation) : TrackOutput()
    data class Event(val expression: ExpressionLike, val eventId: String?) : TrackOutput()
}

/**
 * Camera track interface - produces outputs over time.
 */
interface CameraTrack {
    val trackType: String
    val duration: Float

    fun evaluate(
        time: Float,
        context: Map<String, MoValue>,
        runtime: MoLangRuntime,
        origin: Vec3
    ): List<TrackOutput>
}

enum class TrackCapability {
    POSITION,
    FOCUS,
    ROTATION,
    EVENTS
}

fun CameraTrack.capabilities(): Set<TrackCapability> {
    val outputs = evaluate(0f, emptyMap(), MoLangRuntime(), Vec3.ZERO)
    return outputs.mapTo(mutableSetOf()) { output ->
        when (output) {
            is TrackOutput.Position -> TrackCapability.POSITION
            is TrackOutput.Focus -> TrackCapability.FOCUS
            is TrackOutput.Rotation -> TrackCapability.ROTATION
            is TrackOutput.Event -> TrackCapability.EVENTS
        }
    }
}
