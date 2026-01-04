/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.camera

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.data.DataRegistry
import com.cobblemon.mod.common.api.reactive.SimpleObservable
import com.cobblemon.mod.common.util.cobblemonResource
import com.google.gson.GsonBuilder
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.ResourceManager
import java.io.InputStreamReader
import java.util.concurrent.ExecutionException

/**
 * Registry for loading camera timeline state machines from JSON files.
 * Timelines can be loaded from datapacks or resource packs.
 *
 * @author Terrainwax
 * @since October 15th, 2025
 */
object CameraTimelineRegistry : DataRegistry {
    override val id = cobblemonResource("camera_timelines")
    override val type = PackType.SERVER_DATA // Load from datapacks (server-driven)
    override val observable = SimpleObservable<CameraTimelineRegistry>()

    private val gson = GsonBuilder()
        .registerTypeAdapter(CameraTimeline::class.java, CameraTimelineAdapter)
        .create()

    /**
     * Map of timeline ID to CameraTimeline instance.
     */
    val timelines = mutableMapOf<String, CameraTimeline>()

    override fun reload(manager: ResourceManager) {
        timelines.clear()

        // Load from JSON files in data/cobblemon/camera_timelines/
        val resources = manager.listResources("camera_timelines") { path ->
            path.path.endsWith(".json")
        }

        for ((identifier, resource) in resources) {
            try {
                resource.open().use { stream ->
                    val reader = InputStreamReader(stream)
                    val timeline = gson.fromJson(reader, CameraTimeline::class.java)
                    
                    // Validate timeline
                    validateTimeline(timeline, identifier.toString())
                    
                    if (timelines.containsKey(timeline.timelineId)) {
                        Cobblemon.LOGGER.warn("[CobblemonCamera] Duplicate timeline ID '${timeline.timelineId}' in $identifier, overwriting")
                    }
                    
                    timelines[timeline.timelineId] = timeline
                    Cobblemon.LOGGER.debug("[CobblemonCamera] Loaded camera timeline '${timeline.timelineId}' from $identifier")
                }
            } catch (e: Exception) {
                Cobblemon.LOGGER.error("[CobblemonCamera] Error loading camera timeline from $identifier", e)
            }
        }

        Cobblemon.LOGGER.info("[CobblemonCamera] Loaded ${timelines.size} camera timelines")
        observable.emit(this)
    }

    override fun sync(player: ServerPlayer) {
        // Camera timelines are client-only, no sync needed
    }

    /**
     * Validates a timeline for consistency.
     */
    private fun validateTimeline(timeline: CameraTimeline, source: String) {
        // Check if start state exists
        if (timeline.startStateId !in timeline.states) {
            Cobblemon.LOGGER.error("[CobblemonCamera] Timeline '${timeline.timelineId}' references non-existent start state '${timeline.startStateId}' in $source")
        }

        // Check if all transition states exist
        for (transition in timeline.transitions) {
            if (transition.sourceStateId !in timeline.states) {
                Cobblemon.LOGGER.error("[CobblemonCamera] Timeline '${timeline.timelineId}' has transition from non-existent state '${transition.sourceStateId}' in $source")
            }
            if (transition.targetStateId !in timeline.states) {
                Cobblemon.LOGGER.error("[CobblemonCamera] Timeline '${timeline.timelineId}' has transition to non-existent state '${transition.targetStateId}' in $source")
            }
        }

        // Check if all states reference valid paths (will be validated at runtime since paths may not be loaded yet)
        for ((stateId, state) in timeline.states) {
            if (stateId != state.stateId) {
                Cobblemon.LOGGER.warn("[CobblemonCamera] Timeline '${timeline.timelineId}' state key '$stateId' doesn't match state's stateId '${state.stateId}' in $source")
            }
        }
    }

    /**
     * Gets a camera timeline by ID, returns null if not found.
     */
    fun getTimeline(timelineId: String): CameraTimeline? {
        val timeline = timelines[timelineId]
        if (timeline == null) {
            Cobblemon.LOGGER.error("[CobblemonCamera] Camera timeline '$timelineId' not found!")
        }
        return timeline
    }

    /**
     * Checks if a timeline exists.
     */
    fun hasTimeline(timelineId: String): Boolean = timelines.containsKey(timelineId)

    /**
     * Registers a camera timeline programmatically.
     *
     * This method allows third-party mods to register custom timelines created with
     * [CameraTimelineBuilder] without needing JSON files.
     *
     * The timeline will be validated for consistency before registration.
     *
     * @param timeline The camera timeline to register
     */
    fun registerTimeline(timeline: CameraTimeline) {
        // Validate the timeline
        validateTimeline(timeline, "programmatic registration")

        if (timelines.containsKey(timeline.timelineId)) {
            Cobblemon.LOGGER.warn("[CobblemonCamera] Timeline '${timeline.timelineId}' is already registered, overwriting")
        }

        timelines[timeline.timelineId] = timeline
        Cobblemon.LOGGER.debug("[CobblemonCamera] Registered camera timeline '${timeline.timelineId}' programmatically")
    }
}

