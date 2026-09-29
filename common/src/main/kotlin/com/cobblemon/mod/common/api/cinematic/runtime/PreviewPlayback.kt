/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.runtime

import net.minecraft.network.chat.Component
import com.cobblemon.mod.common.api.cinematic.model.CinematicValidationException
import com.cobblemon.mod.common.api.cinematic.model.Beat
import kotlin.math.floor

class PreviewPlayback(val beat: Beat) {
    val duration = beat.duration ?: MAX_SECONDS
    var paused = true
        private set
    var time = 0f
        private set
    private var clock = 0.0
    private var sampledFrame = -1
    private var sampledEnd = false
    private var resetPending = true

    init {
        if (!duration.isFinite() || duration !in 0f..MAX_SECONDS) throw CinematicValidationException(Component.translatable("cobblemon.cinematic_editor.preview.invalid_duration", MAX_SECONDS))
        if (!beat.speed.isFinite() || beat.speed <= 0f) throw CinematicValidationException(Component.translatable("cobblemon.cinematic_editor.preview.invalid_speed"))
    }
    fun seek(seconds: Float, paused: Boolean) {
        if (!seconds.isFinite()) throw CinematicValidationException(Component.translatable("cobblemon.cinematic_editor.preview.invalid_time"))
        clock = seconds.coerceIn(0f, duration).toDouble(); this.paused = paused; updateTime(); resetPending = true
    }
    fun advance(deltaSeconds: Float) {
        if (paused || !deltaSeconds.isFinite()) return
        clock += deltaSeconds.coerceIn(0f, 0.5f) * beat.speed
        if (clock >= duration) {
            if (beat.isLooping && duration > 0f) { clock %= duration; resetPending = true }
            else { clock = duration.toDouble(); paused = true }
        }
        updateTime()
    }
    private fun updateTime() { time = if (clock >= duration) duration else (floor(clock * HZ + 0.00001) / HZ).toFloat() }
    fun evaluate(reset: () -> Unit, sample: (Float) -> Unit): Boolean {
        var changed = false
        if (resetPending) { reset(); sampledFrame = -1; sampledEnd = false; resetPending = false; changed = true }
        val targetFrame = floor(time.toDouble() * HZ + 0.00001).toInt()
        while (sampledFrame < targetFrame) {
            val nextFrame = sampledFrame + 1
            sample(nextFrame / HZ)
            sampledFrame = nextFrame
            changed = true
        }
        if (time == duration && duration > targetFrame / HZ && !sampledEnd) { sample(duration); sampledEnd = true; changed = true }
        return changed
    }
    companion object { const val HZ = 20f; const val MAX_SECONDS = 600f }
}
