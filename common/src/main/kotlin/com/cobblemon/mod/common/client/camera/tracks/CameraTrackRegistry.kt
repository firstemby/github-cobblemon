/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.camera.tracks

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.camera.CameraTrackRegistry
import com.cobblemon.mod.common.api.camera.ICameraTrack
import com.cobblemon.mod.common.api.data.DataRegistry
import com.cobblemon.mod.common.api.reactive.SimpleObservable
import com.cobblemon.mod.common.util.cobblemonResource
import com.google.gson.Gson
import com.google.gson.JsonObject
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.ResourceManager
import java.io.InputStreamReader

/**
 * Registry for reusable camera track definitions.
 *
 * Tracks can be defined in data files at `data/<namespace>/camera_tracks/<id>.json`
 * and referenced by ID in timelines, allowing for better organization and reusability.
 *
 * Example track file (`data/cobblemon/camera_tracks/intro_movement.json`):
 * ```json
 * {
 *   "trackId": "intro_movement",
 *   "trackType": "position",
 *   "keyframes": [...]
 * }
 * ```
 *
 * Then reference in timeline:
 * ```json
 * {
 *   "tracks": [
 *     { "trackType": "reference", "trackId": "intro_movement" }
 *   ]
 * }
 * ```
 *
 * @author Terrainwax
 * @since January 2025
 */
object CameraTrackDefinitionRegistry : DataRegistry {
    override val id = cobblemonResource("camera_tracks")
    override val type = PackType.SERVER_DATA
    override val observable = SimpleObservable<CameraTrackDefinitionRegistry>()

    private val gson = Gson()
    private val tracks = mutableMapOf<String, ICameraTrack>()

    override fun reload(manager: ResourceManager) {
        tracks.clear()

        val resources = manager.listResources("camera_tracks") { path ->
            path.path.endsWith(".json")
        }

        for ((identifier, resource) in resources) {
            try {
                resource.open().use { stream ->
                    val reader = InputStreamReader(stream)
                    val json = gson.fromJson(reader, JsonObject::class.java)

                    val trackId = json.get("trackId")?.asString ?: identifier.path.substringAfterLast('/')
                        .substringBeforeLast('.')
                    val trackType = json.get("trackType")?.asString
                        ?: throw IllegalArgumentException("Track '$trackId' missing 'trackType' field")

                    val track = CameraTrackRegistry.deserializeTrack(trackType, json)
                    if (track != null) {
                        tracks[trackId] = track
                        Cobblemon.LOGGER.debug("[CameraTrackRegistry] Loaded track definition: $trackId (type: $trackType)")
                    } else {
                        Cobblemon.LOGGER.warn("[CameraTrackRegistry] Failed to deserialize track: $trackId")
                    }
                }
            } catch (e: Exception) {
                Cobblemon.LOGGER.error("[CameraTrackRegistry] Error loading track from ${identifier}: ${e.message}", e)
            }
        }

        Cobblemon.LOGGER.info("[CameraTrackRegistry] Loaded ${tracks.size} track definition(s)")
        observable.emit(this)
    }

    override fun sync(player: net.minecraft.server.level.ServerPlayer) {
        // Track definitions are loaded from datapacks, no client sync needed
        // Timelines reference tracks by ID and load from server data
    }

    /**
     * Gets a track definition by ID.
     *
     * @param trackId The track ID (e.g., "intro_movement")
     * @return The track, or null if not found
     */
    fun getTrack(trackId: String): ICameraTrack? = tracks[trackId]

    /**
     * Gets all registered track IDs.
     */
    fun getTrackIds(): Set<String> = tracks.keys.toSet()

    /**
     * Checks if a track is registered.
     */
    fun hasTrack(trackId: String): Boolean = tracks.containsKey(trackId)

    /**
     * Registers a track programmatically (for testing or dynamic creation).
     *
     * @param trackId Unique identifier for this track
     * @param track The track to register
     */
    fun registerTrack(trackId: String, track: ICameraTrack) {
        if (tracks.containsKey(trackId)) {
            Cobblemon.LOGGER.warn("[CameraTrackRegistry] Track '$trackId' already registered, overwriting")
        }
        tracks[trackId] = track
        Cobblemon.LOGGER.debug("[CameraTrackRegistry] Registered track: $trackId")
    }

    /**
     * Unregisters a track.
     */
    fun unregisterTrack(trackId: String): ICameraTrack? {
        return tracks.remove(trackId)
    }

    /**
     * Clears all registered tracks.
     */
    fun clear() {
        tracks.clear()
    }
}
