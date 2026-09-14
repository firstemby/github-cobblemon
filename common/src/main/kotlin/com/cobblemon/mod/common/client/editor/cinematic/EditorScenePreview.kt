/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic

import net.minecraft.client.resources.language.I18n
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.cinematic.model.ActorBinding
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import com.cobblemon.mod.common.net.messages.SerializedBinding
import com.cobblemon.mod.common.net.messages.client.editor.EditorPreviewStateS2CPacket
import com.cobblemon.mod.common.net.messages.server.editor.EditorPreviewControlC2SPacket
import com.cobblemon.mod.common.net.messages.server.editor.EditorPreviewStartC2SPacket
import com.cobblemon.mod.common.net.messages.server.editor.EditorPreviewStopC2SPacket
import com.google.gson.Gson
import java.util.UUID
import net.minecraft.world.phys.AABB
import net.minecraft.client.Minecraft

class EditorScenePreview(
    private val playState: EditorPlayState,
    private val sceneProvider: () -> EditorScene?,
    private val clock: () -> Long = System::nanoTime,
    private val connectionProvider: () -> Any? = { Minecraft.getInstance().connection },
    private val levelProvider: () -> Any? = { Minecraft.getInstance().level }
) {
    private data class Request(val editor: EditorScene, val connection: Any, val level: Any, val sent: Long)
    private var request: Request? = null
    private var runningSceneInstanceId: UUID? = null
    private var runningPreviewId: UUID? = null
    private var pendingPreviewId: UUID? = null
    private var lastAppliedEditGen = -1
    private var requestedBeat = ""
    private var revision = 0L
    private var controlDirty = false
    private var lastControlNanos = 0L
    private val gson = Gson()
    private var actorBounds: Map<String, AABB> = emptyMap()

    fun actorBounds(actorId: String): AABB? = actorBounds[actorId].takeIf {
        sceneProvider()?.editGeneration == lastAppliedEditGen && requestedBeat == sceneProvider()?.let(::activeBeat)
    }

    var paused = true
        private set
    var error = ""
        private set

    init {
        playState.onModeChanged { mode ->
            if (mode == EditorPlayMode.EDIT) {
                stop()
                paused = true
            } else {
                paused = false
                sceneProvider()?.let(::restart)
            }
        }
    }

    private fun activeBeat(editor: EditorScene) =
        SceneEditorOverlay.selection.getSelectedBeatId()?.takeIf { it in editor.beats } ?: editor.startBeatId

    fun togglePlayback() {
        val editor = sceneProvider() ?: return
        val duration = editor.beats[activeBeat(editor)]?.duration
        if ((paused || playState.mode == EditorPlayMode.EDIT) && duration != null &&
            SceneEditorOverlay.currentPlayheadSeconds >= duration) SceneEditorOverlay.applyPreviewTime(0f)
        if (playState.mode == EditorPlayMode.EDIT) {
            playState.setMode(EditorPlayMode.PLAY)
        } else {
            paused = !paused
            requestControl()
        }
    }

    fun seek() {
        paused = true
        if (playState.mode != EditorPlayMode.EDIT) requestControl()
    }

    private fun requestControl() {
        actorBounds = emptyMap()
        revision++
        controlDirty = true
    }

    fun tickTransport() {
        val active = request ?: return
        if (connectionProvider() !== active.connection || levelProvider() !== active.level || sceneProvider() !== active.editor) {
            fail(I18n.get("cobblemon.cinematic_editor.preview.context_changed"))
        } else if (pendingPreviewId != null && clock() - active.sent >= 15_000_000_000L) {
            fail(I18n.get("cobblemon.cinematic_editor.preview.request_timeout"))
        }
    }

    internal fun trackRequest(id: UUID, editor: EditorScene): Boolean {
        val connection = connectionProvider() ?: return false
        val level = levelProvider() ?: return false
        request = Request(editor, connection, level, clock())
        pendingPreviewId = id
        runningPreviewId = id
        return true
    }

    fun tick() {
        tickTransport()
        if (playState.mode == EditorPlayMode.EDIT) return
        val editor = sceneProvider() ?: run { stop(); return }
        if (editor.editGeneration != lastAppliedEditGen) {
            restart(editor)
            return
        }
        val beat = activeBeat(editor)
        if (requestedBeat != beat) {
            requestedBeat = beat
            paused = true
            requestControl()
        }
        val previewId = runningPreviewId ?: return
        val now = clock()
        if (pendingPreviewId == null && controlDirty && now - lastControlNanos >= 100_000_000L) {
            try {
                EditorPreviewControlC2SPacket(previewId, revision, requestedBeat,
                    SceneEditorOverlay.currentPlayheadSeconds, paused).sendToServer()
                controlDirty = false
                lastControlNanos = now
            } catch (exception: Exception) {
                Cobblemon.LOGGER.warn("Could not update editor preview", exception)
                fail(I18n.get("cobblemon.cinematic_editor.preview.control_failed"))
            }
        }
    }

    private fun restart(editor: EditorScene) {
        val keepPaused = paused
        stop()
        paused = keepPaused
        error = ""
        requestedBeat = activeBeat(editor)
        lastAppliedEditGen = editor.editGeneration
        try {
            val bindings = EditorActorBindingProvider.collect(editor).mapNotNull { (actorId, binding) ->
                toSerialized(actorId, binding)
            }
            val previewId = UUID.randomUUID()
            if (!trackRequest(previewId, editor)) {
                fail(I18n.get("cobblemon.cinematic_editor.preview.context_changed"))
                return
            }
            EditorPreviewStartC2SPacket(previewId, gson.toJson(EditorPreviewScene.build(editor, SceneEditorOverlay.previewOrigin)),
                SceneEditorOverlay.previewOrigin, bindings, requestedBeat,
                SceneEditorOverlay.currentPlayheadSeconds, paused).sendToServer()
        } catch (exception: Exception) {
            Cobblemon.LOGGER.warn("Could not prepare editor preview", exception)
            fail(I18n.get("cobblemon.cinematic_editor.preview.start_failed"))
        }
    }

    private fun stop() {
        actorBounds = emptyMap()
        val previous = runningPreviewId
        val active = request
        request = null
        lastAppliedEditGen = -1
        runningSceneInstanceId = null
        runningPreviewId = null
        pendingPreviewId = null
        revision = 0L
        controlDirty = false
        requestedBeat = ""
        if (active != null && connectionProvider() === active.connection) previous?.let(::sendStop)
    }

    fun runningSceneInstanceIdOrNull(): UUID? = runningSceneInstanceId

    fun onAckSuccess(previewId: UUID, sceneId: UUID) {
        tickTransport()
        if (previewId == runningPreviewId && pendingPreviewId == null && runningSceneInstanceId == sceneId) return
        if (previewId != pendingPreviewId) {
            sendStop(previewId)
            return
        }
        pendingPreviewId = null
        runningSceneInstanceId = sceneId
    }

    fun onState(packet: EditorPreviewStateS2CPacket) {
        tickTransport()
        val editor = sceneProvider() ?: return
        if (packet.previewId != runningPreviewId || packet.revision != revision || controlDirty || !packet.time.isFinite()) return
        if (packet.error != null) { fail(packet.error.string); return }
        if (packet.beatId != activeBeat(editor)) return
        paused = packet.paused
        error = ""
        actorBounds = packet.actorBounds
        SceneEditorOverlay.applyPreviewTime(packet.time)
    }

    fun onAckFailure(previewId: UUID, message: String) {
        if (previewId == pendingPreviewId) fail(message)
    }

    private fun sendStop(previewId: UUID) {
        try {
            EditorPreviewStopC2SPacket(previewId).sendToServer()
        } catch (exception: Exception) {
            Cobblemon.LOGGER.warn("Could not stop editor preview", exception)
        }
    }

    private fun fail(message: String) {
        stop()
        paused = true
        playState.setMode(EditorPlayMode.EDIT)
        error = message
        Cobblemon.LOGGER.warn("Editor preview failed: {}", message)
    }

    internal fun toSerialized(actorId: String, binding: ActorBinding): SerializedBinding? = when (binding) {
        is ActorBinding.SynthPokemon -> SerializedBinding.Pokemon(
            actorId = actorId,
            propertiesSpec = binding.properties.originalString.ifBlank { binding.properties.asString() },
            aspects = binding.aspects.toList()
        )
        is ActorBinding.SynthBedrock -> SerializedBinding.Bedrock(
            actorId = actorId,
            category = binding.category.toString(),
            aspects = binding.aspects.toList(),
            scale = binding.scale
        )
        is ActorBinding.Player, is ActorBinding.Real -> null
    }
}
