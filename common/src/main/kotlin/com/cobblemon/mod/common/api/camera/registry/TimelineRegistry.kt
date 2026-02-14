/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.camera.registry

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.camera.model.Timeline
import com.cobblemon.mod.common.api.camera.result.ValidationError
import com.cobblemon.mod.common.api.data.DataRegistry
import com.cobblemon.mod.common.api.reactive.SimpleObservable
import com.cobblemon.mod.common.util.cobblemonResource
import com.google.gson.GsonBuilder
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.ResourceManager
import java.io.InputStreamReader

/**
 * Registry for loading Timeline (V2 format) from JSON files.
 *
 * Loads timelines directly into the new immutable Timeline model
 * without legacy conversion.
 */
object TimelineRegistry : DataRegistry {
    override val id = cobblemonResource("camera_timelines")
    override val type = PackType.SERVER_DATA
    override val observable = SimpleObservable<TimelineRegistry>()

    private val gson = GsonBuilder()
        .registerTypeAdapter(Timeline::class.java, TimelineDeserializer)
        .create()

    private val timelines = mutableMapOf<String, Timeline>()

    override fun reload(manager: ResourceManager) {
        timelines.clear()

        val resources = manager.listResources("camera_timelines") { path ->
            path.path.endsWith(".json")
        }

        for ((identifier, resource) in resources) {
            try {
                resource.open().use { stream ->
                    val reader = InputStreamReader(stream)
                    val timeline = gson.fromJson(reader, Timeline::class.java)

                    val errors = timeline.validate()
                    val hasErrors = errors.any { it.severity == ValidationError.Severity.ERROR }

                    if (hasErrors) {
                        Cobblemon.LOGGER.error("[TimelineRegistry] Validation failed for '$identifier':")
                        errors.forEach { error ->
                            val level = if (error.severity == ValidationError.Severity.ERROR) "ERROR" else "WARN"
                            Cobblemon.LOGGER.error("  [$level] ${error.path}: ${error.message}")
                        }
                        continue
                    }

                    if (errors.isNotEmpty()) {
                        Cobblemon.LOGGER.warn("[TimelineRegistry] Warnings for timeline '${timeline.id}':")
                        errors.forEach { Cobblemon.LOGGER.warn("  ${it.path}: ${it.message}") }
                    }

                    if (timelines.containsKey(timeline.id)) {
                        Cobblemon.LOGGER.warn("[TimelineRegistry] Duplicate timeline ID '${timeline.id}' in $identifier, overwriting")
                    }

                    timelines[timeline.id] = timeline
                    Cobblemon.LOGGER.debug("[TimelineRegistry] Loaded timeline '${timeline.id}' from $identifier")
                }
            } catch (e: Exception) {
                Cobblemon.LOGGER.error("[TimelineRegistry] Error loading timeline from $identifier", e)
            }
        }

        Cobblemon.LOGGER.info("[TimelineRegistry] Loaded ${timelines.size} timelines")
        observable.emit(this)
    }

    override fun sync(player: ServerPlayer) {
        // Timelines are server-driven, no sync needed
    }

    fun getTimeline(id: String): Timeline? {
        val timeline = timelines[id]
        if (timeline == null) {
            Cobblemon.LOGGER.error("[TimelineRegistry] Timeline '$id' not found")
        }
        return timeline
    }

    fun hasTimeline(id: String): Boolean = timelines.containsKey(id)

    fun registerTimeline(timeline: Timeline) {
        val errors = timeline.validate()
        if (errors.any { it.severity == ValidationError.Severity.ERROR }) {
            Cobblemon.LOGGER.error("[TimelineRegistry] Cannot register invalid timeline '${timeline.id}':")
            errors.forEach { Cobblemon.LOGGER.error("  ${it.path}: ${it.message}") }
            return
        }

        if (timelines.containsKey(timeline.id)) {
            Cobblemon.LOGGER.warn("[TimelineRegistry] Timeline '${timeline.id}' already registered, overwriting")
        }

        timelines[timeline.id] = timeline
        Cobblemon.LOGGER.debug("[TimelineRegistry] Registered timeline '${timeline.id}' programmatically")
    }

    fun getAllTimelines(): Collection<Timeline> = timelines.values
}
