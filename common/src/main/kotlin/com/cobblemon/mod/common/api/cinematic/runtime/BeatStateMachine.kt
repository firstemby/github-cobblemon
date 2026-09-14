/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.runtime

import com.bedrockk.molang.runtime.MoLangRuntime
import com.bedrockk.molang.runtime.value.DoubleValue
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.camera.model.ParameterDefault
import com.cobblemon.mod.common.api.cinematic.model.Beat
import com.cobblemon.mod.common.api.cinematic.model.BeatTransition
import com.cobblemon.mod.common.api.cinematic.model.Scene
import com.cobblemon.mod.common.util.resolveBoolean

/** Non-looping beats remain active at their duration until a transition fires or the scene stops. */
class BeatStateMachine(
    val scene: Scene
) {
    var runtime: MoLangRuntime = MoLangRuntime()
        private set

    private val timeOnCurrentPathValue = DoubleValue(0.0)
    private val totalTimeValue = DoubleValue(0.0)

    private var _currentBeat: Beat? = null
    private var _beatTime = 0.0
    private var _totalTime = 0.0
    private var _active: Boolean = false

    val currentBeat: Beat? get() = _currentBeat
    val beatTime: Float get() = _beatTime.coerceAtMost(Float.MAX_VALUE.toDouble()).toFloat()
    val totalTime: Float get() = _totalTime.coerceAtMost(Float.MAX_VALUE.toDouble()).toFloat()

    fun isActive(): Boolean = _active

    fun start(): Boolean {
        val start = scene.getBeat(scene.startBeatId) ?: return false
        _currentBeat = start
        _beatTime = 0.0
        _totalTime = 0.0
        _active = true
        injectParameterDefaults()
        injectTimeVariables()
        return true
    }

    private fun injectParameterDefaults() {
        val vars = runtime.environment.variable.map
        for ((name, definition) in scene.parameters) {
            // Parameter overrides may already be present; defaults must not replace them.
            when (val default = definition.default) {
                is ParameterDefault.NumberDefault -> {
                    if (name !in vars) vars[name] = DoubleValue(default.value)
                }
                is ParameterDefault.Vec3Default -> {
                    if ("${name}_x" !in vars) vars["${name}_x"] = DoubleValue(default.x)
                    if ("${name}_y" !in vars) vars["${name}_y"] = DoubleValue(default.y)
                    if ("${name}_z" !in vars) vars["${name}_z"] = DoubleValue(default.z)
                }
                is ParameterDefault.StringDefault -> {
                }
                null -> {   }
            }
        }
    }

    fun stop() {
        _active = false
        _currentBeat = null
    }

    internal fun samplePreview(beat: Beat, seconds: Float) {
        require(seconds.isFinite() && seconds >= 0f)
        _currentBeat = beat
        _beatTime = seconds.toDouble()
        _totalTime = seconds.toDouble()
        injectTimeVariables()
    }

    internal fun resetPreview(beat: Beat) {
        runtime = MoLangRuntime()
        injectParameterDefaults()
        samplePreview(beat, 0f)
    }

    /** Delta is clamped to 0–0.5 seconds; the first matching outgoing transition wins. */
    fun advance(deltaSeconds: Float, beforeTransitions: (Beat, Float) -> Unit = { _, _ -> }): AdvanceResult {
        if (!_active) return AdvanceResult.Inactive

        val beat = _currentBeat ?: return AdvanceResult.Inactive
        val clampedDelta = if (deltaSeconds.isFinite()) deltaSeconds.toDouble().coerceIn(0.0, 0.5) else 0.0
        val elapsedBeatSeconds = clampedDelta * beat.speed.toDouble()
        val previousBeatTime = _beatTime

        _beatTime += elapsedBeatSeconds
        _totalTime += clampedDelta
        injectTimeVariables()

        beforeTransitions(beat, beatTime)
        if (!_active) return AdvanceResult.Inactive

        val firedTransition = findFiringTransition(beat)
        if (firedTransition != null) {
            if (firedTransition.endsScene) return AdvanceResult.Finished
            val targetBeat = scene.getBeat(firedTransition.targetBeatId)
            if (targetBeat != null) {
                _currentBeat = targetBeat
                _beatTime = 0.0
                injectTimeVariables()
                return AdvanceResult.Running(targetBeat, entered = true, transition = firedTransition)
            } else {
                Cobblemon.LOGGER.warn(
                    "[BeatStateMachine] Transition from '{}' to '{}' dropped — target beat not found",
                    beat.id, firedTransition.targetBeatId
                )
            }
        }

        val duration = beat.duration
        val effectiveElapsed = if (duration != null && duration > 0f && !beat.isLooping) {
            elapsedBeatSeconds.coerceAtMost((duration.toDouble() - previousBeatTime).coerceAtLeast(0.0))
        } else elapsedBeatSeconds
        if (duration != null && duration > 0f) {
            if (beat.isLooping) {
                if (_beatTime >= duration.toDouble()) {
                    _beatTime %= duration.toDouble()
                    injectTimeVariables()
                    return AdvanceResult.Running(beat, entered = true, elapsedBeatSeconds = effectiveElapsed)
                }
            } else {
                if (_beatTime > duration.toDouble()) {
                    _beatTime = duration.toDouble()
                    injectTimeVariables()
                }
            }
        }

        return AdvanceResult.Running(beat, elapsedBeatSeconds = effectiveElapsed)
    }

    private fun findFiringTransition(beat: Beat): BeatTransition? {
        for (transition in scene.getTransitionsFrom(beat.id)) {
            try {
                if (runtime.resolveBoolean(transition.condition)) return transition
            } catch (e: Exception) {
                Cobblemon.LOGGER.error(
                    "[BeatStateMachine] Condition '{}' on transition {} -> {} threw: {}",
                    transition.condition, transition.sourceBeatId, transition.targetBeatId, e.message
                )
            }
        }
        return null
    }

    private fun injectTimeVariables() {
        timeOnCurrentPathValue.value = _beatTime
        totalTimeValue.value = _totalTime
        runtime.environment.variable.apply {
            map["time_on_current_path"] = timeOnCurrentPathValue
            map["total_time"] = totalTimeValue
        }
    }

    sealed class AdvanceResult {
        object Inactive : AdvanceResult()
        data class Running(
            val beat: Beat,
            val entered: Boolean = false,
            val transition: BeatTransition? = null,
            val elapsedBeatSeconds: Double = 0.0
        ) : AdvanceResult()

        object Finished : AdvanceResult()
    }
}
