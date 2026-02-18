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
import com.cobblemon.mod.common.api.molang.ExpressionLike
import net.minecraft.world.phys.Vec3

/**
 * Track that executes MoLang expressions at specific times.
 */
data class EventTrack(
    val events: List<TimedEvent>
) : CameraTrack {
    override val trackType = "event"

    override val duration: Float
        get() = events.maxOfOrNull { it.time } ?: 0f

    override fun evaluate(
        time: Float,
        context: Map<String, MoValue>,
        runtime: MoLangRuntime,
        origin: Vec3
    ): List<TrackOutput> {
        return emptyList()
    }

    init {
        require(events.zipWithNext().all { (a, b) -> a.time <= b.time }) {
            "EventTrack events must be sorted by time"
        }
    }
}

/**
 * Timed event - execute expression at specific time.
 */
data class TimedEvent(
    val time: Float,
    val expression: ExpressionLike,
    val eventId: String? = null
) {
    init {
        require(time >= 0f) { "Event time must be non-negative, got $time" }
    }
}
