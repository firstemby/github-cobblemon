/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.camera.integration.server

import com.bedrockk.molang.runtime.MoLangRuntime
import com.bedrockk.molang.runtime.MoParams
import com.bedrockk.molang.runtime.value.MoValue
import com.cobblemon.mod.common.api.camera.engine.TimelineEngine
import com.cobblemon.mod.common.api.camera.model.State
import com.cobblemon.mod.common.api.camera.model.Timeline
import com.cobblemon.mod.common.api.molang.MoLangFunctions.setup
import net.minecraft.world.phys.Vec3
import java.util.UUID

/**
 * Server-side camera instance - pure state and identity.
 *
 * Represents a single camera with its configuration and execution engine.
 * Does NOT handle networking, chunk loading, or viewer management.
 *
 * @param id Unique identifier for this camera instance
 * @param origin The world position origin for relative coordinates
 * @param initialContext Initial context values (parameter values) to inject into MoLang.
 *                       These are validated against the timeline's declared parameters when starting.
 * @param queryFunctions Custom MoLang query functions to register
 */
class CameraInstance(
    val id: UUID = UUID.randomUUID(),
    val origin: Vec3,
    private val initialContext: Map<String, MoValue> = emptyMap(),
    queryFunctions: Map<String, java.util.function.Function<MoParams, Any>> = emptyMap()
) {
    private val runtime: MoLangRuntime = MoLangRuntime().setup().apply {
        environment.query.functions.putAll(queryFunctions)
    }

    private var engine: TimelineEngine = TimelineEngine(id, createStubTimeline(), origin, runtime, initialContext)

    @Volatile
    var focusOverride: Vec3? = null

    var lastValidFocusPosition: Vec3? = null
        private set

    fun startTimeline(timeline: Timeline): Boolean {
        engine = TimelineEngine(id, timeline, origin, runtime, initialContext)
        return engine.start()
    }

    fun tick() = engine.tick()

    fun stop() = engine.stop()

    fun isActive() = engine.isActive()

    fun setVariable(name: String, value: Double) = engine.setVariable(name, value)
    
    fun triggerInstantTransition() = engine.triggerInstantTransition()

    fun consumeInstantTransitionFlag() = engine.consumeInstantTransitionFlag()

    fun updateLastValidFocusPosition(position: Vec3) {
        lastValidFocusPosition = position
    }

    private fun createStubTimeline(): Timeline {
        return Timeline(
            id = "stub",
            startStateId = "stub",
            states = mapOf(
                "stub" to State(
                    id = "stub",
                    tracks = emptyList()
                )
            ),
            transitions = emptyList()
        )
    }
}
