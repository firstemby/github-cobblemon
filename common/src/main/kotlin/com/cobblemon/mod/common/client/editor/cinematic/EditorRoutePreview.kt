/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic

import com.cobblemon.mod.common.Cobblemon
import net.minecraft.client.resources.language.I18n
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import com.cobblemon.mod.common.net.messages.client.editor.EditorRouteResultS2CPacket
import com.cobblemon.mod.common.net.messages.server.editor.EditorRouteRequestC2SPacket
import net.minecraft.client.Minecraft
import java.util.UUID

object EditorRoutePreview {
    private data class Request(val id: UUID, val scene: EditorScene, val generation: Int,
        val beat: String, val actor: String, val track: Int, val level: Any?, val started: Long = System.nanoTime())
    private var request: Request? = null
    private var result: EditorRouteResultS2CPacket? = null
    var message = ""
        private set
    var pending = false
        private set

    fun matches(scene: EditorScene, beat: String, actor: String, track: Int): Boolean {
        val current = request ?: return false
        if (current.scene !== scene || current.generation != scene.editGeneration || current.level !== Minecraft.getInstance().level) {
            clear()
            return false
        }
        if (pending && System.nanoTime() - current.started > 60_000_000_000L) {
            pending = false
            message = I18n.get("cobblemon.cinematic_editor.route.request_timed_out")
        }
        return current.beat == beat && current.actor == actor && current.track == track
    }

    fun result(scene: EditorScene, beat: String, actor: String, track: Int) =
        result.takeIf { matches(scene, beat, actor, track) }

    fun compute(scene: EditorScene, beat: String, actor: String, track: Int) {
        clear()
        val current = Request(UUID.randomUUID(), scene, scene.editGeneration, beat, actor, track, Minecraft.getInstance().level)
        request = current
        try {
            val binding = EditorActorBindingProvider.collect(scene)[actor] ?: run {
                message = I18n.get("cobblemon.cinematic_editor.route.missing_actor")
                return
            }
            val serialized = SceneEditorOverlay.preview.toSerialized(actor, binding) ?: run {
                message = I18n.get("cobblemon.cinematic_editor.route.unsupported_actor")
                return
            }
            val data = scene.beats[beat]?.actorDirections?.get(actor)?.tracks?.getOrNull(track)
            if (data == null || data.trackType != "pathfinding") {
                message = I18n.get("cobblemon.cinematic_editor.route.invalid_track")
                return
            }
            EditorRouteRequestC2SPacket(current.id, data.data.toString(), serialized).sendToServer()
            pending = true
            message = I18n.get("cobblemon.cinematic_editor.route.computing")
        } catch (error: Exception) {
            Cobblemon.LOGGER.warn("Could not request editor route", error)
            message = I18n.get("cobblemon.cinematic_editor.route.request_failed")
        }
    }

    fun accept(packet: EditorRouteResultS2CPacket) {
        val current = request ?: return
        if (packet.requestId != current.id || !matches(current.scene, current.beat, current.actor, current.track)) return
        pending = false
        result = packet
        message = packet.error?.string.orEmpty()
    }

    fun clear() { request = null; result = null; message = ""; pending = false }

    fun diagnostics(): EditorRouteResultS2CPacket? = result
}
