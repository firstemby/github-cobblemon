/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.serverhandling.editor

import net.minecraft.network.chat.Component
import com.cobblemon.mod.common.api.cinematic.model.CinematicValidationException
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.cinematic.model.ActorBinding
import com.cobblemon.mod.common.api.cinematic.model.Scene
import com.cobblemon.mod.common.api.camera.result.ValidationError
import com.cobblemon.mod.common.api.cinematic.registry.SceneDeserializer
import com.cobblemon.mod.common.api.cinematic.runtime.CinematicManager
import com.cobblemon.mod.common.api.cinematic.runtime.PreviewPlayback
import com.cobblemon.mod.common.net.messages.client.editor.EditorPreviewStateS2CPacket
import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.net.messages.SerializedBinding
import com.cobblemon.mod.common.net.messages.client.editor.EditorPreviewAckS2CPacket
import com.cobblemon.mod.common.net.messages.server.editor.EditorPreviewStartC2SPacket
import com.google.gson.GsonBuilder
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

object EditorPreviewStartHandler : ServerNetworkPacketHandler<EditorPreviewStartC2SPacket> {

    private const val PERMISSION_LEVEL = 2

    private val gson = GsonBuilder()
        .registerTypeAdapter(Scene::class.java, SceneDeserializer)
        .create()

    override fun handle(packet: EditorPreviewStartC2SPacket, server: MinecraftServer, player: ServerPlayer) {
        if (!player.hasPermissions(PERMISSION_LEVEL)) {
            ackFailure(player, packet.previewId, Component.translatable("cobblemon.cinematic_editor.preview.permission", PERMISSION_LEVEL))
            return
        }
        val scene = try {
            requireNotNull(gson.fromJson(packet.sceneJson, Scene::class.java))
        } catch (error: Exception) {
            Cobblemon.LOGGER.warn("Could not parse editor preview scene", error)
            ackFailure(player, packet.previewId, Component.translatable("cobblemon.cinematic_editor.preview.parse_failed"))
            return
        }

        // Wire-supplied scenes need the same reference validation as datapack scenes before runtime evaluation.
        val blocking = scene.validate().filter { it.severity == ValidationError.Severity.ERROR }
        if (blocking.isNotEmpty()) {
            val detail = Component.empty()
            blocking.take(16).forEachIndexed { index, error ->
                if (index > 0) detail.append("\n")
                detail.append(Component.translatable("cobblemon.cinematic_editor.validation.detail", error.path, error.message))
            }
            ackFailure(player, packet.previewId, Component.translatable("cobblemon.cinematic_editor.preview.validation_failed", blocking.size, minOf(blocking.size, 16), detail))
            return
        }

        val beatId = packet.beatId.ifEmpty { scene.startBeatId }
        val bindings = try {
            if (!packet.origin.x.isFinite() || !packet.origin.y.isFinite() || !packet.origin.z.isFinite()) {
                throw CinematicValidationException(Component.translatable("cobblemon.cinematic_editor.preview.invalid_origin"))
            }
            val beat = scene.getBeat(beatId) ?: throw CinematicValidationException(
                Component.translatable("cobblemon.cinematic_editor.preview.unknown_beat", beatId))
            PreviewPlayback(beat).seek(packet.time, packet.paused)
            buildBindings(packet.bindings)
        } catch (error: Exception) {
            Cobblemon.LOGGER.warn("Invalid editor preview settings", error)
            ackFailure(player, packet.previewId, (error as? CinematicValidationException)?.reason
                ?: Component.translatable("cobblemon.cinematic_editor.preview.invalid_settings"))
            return
        }

        if (EditorPreviewSessionRegistry.contains(packet.previewId)) {
            ackFailure(player, packet.previewId, Component.translatable("cobblemon.cinematic_editor.preview.already_active"))
            return
        }
        EditorPreviewSessionRegistry.onPlayerDisconnect(player.uuid)

        // Register viewers before starting or actors spawn with an empty scope and stop receiving movement updates.
        val sceneId = CinematicManager.startScene(scene = scene, origin = packet.origin, actorBindings = bindings,
            initialViewers = setOf(player.uuid), previewMode = true)
        if (sceneId == null) {
            ackFailure(player, packet.previewId, Component.translatable("cobblemon.cinematic_editor.preview.start_failed"))
            return
        }
        try {
            val instance = checkNotNull(CinematicManager.getScene(sceneId))
            // UUID-only viewer registration provides no level; virtual actor spawning still needs one.
            instance.actorDirector.setServerLevel(player.serverLevel())
            instance.configurePreview(beatId, packet.time, packet.paused)
            val session = EditorPreviewSessionRegistry.register(packet.previewId, sceneId, player.uuid)
            instance.beforePreviewTick = { EditorPreviewSessionRegistry.applyPending(session) }
            instance.onPreviewTick = {
                instance.previewPlayback?.let { playback ->
                    EditorPreviewStateS2CPacket(packet.previewId, session.revision, playback.beat.id,
                        playback.time, playback.paused, instance.actorDirector.previewError ?: session.error,
                        instance.actorDirector.previewActorBounds().entries.take(EditorPreviewStateS2CPacket.MAX_ACTORS)
                            .associate { it.toPair() }).sendToPlayer(player)
                }
            }
            EditorPreviewAckS2CPacket(packet.previewId, true, sceneId, null).sendToPlayer(player)
        } catch (error: Exception) {
            EditorPreviewSessionRegistry.consume(packet.previewId, player.uuid)
            CinematicManager.stopScene(sceneId)
            Cobblemon.LOGGER.warn("Could not initialize editor preview", error)
            ackFailure(player, packet.previewId, (error as? CinematicValidationException)?.reason
                ?: Component.translatable("cobblemon.cinematic_editor.preview.start_failed"))
        }
    }

    private fun ackFailure(player: ServerPlayer, previewId: UUID, message: Component) {
        Cobblemon.LOGGER.warn("Editor preview {} failed: {}", previewId, message.string)
        EditorPreviewAckS2CPacket(previewId, false, null, message).sendToPlayer(player)
    }

    private fun buildBindings(serialized: List<SerializedBinding>): Map<String, ActorBinding> = serialized.associate { binding ->
        val actor = when (binding) {
            is SerializedBinding.Pokemon -> ActorBinding.SynthPokemon(
                PokemonProperties.parse(binding.propertiesSpec), binding.aspects.toSet())
            is SerializedBinding.Bedrock -> {
                if (!binding.scale.isFinite() || binding.scale <= 0f) throw CinematicValidationException(
                    Component.translatable("cobblemon.cinematic_editor.preview.invalid_scale", binding.actorId))
                val category = ResourceLocation.tryParse(if (':' in binding.category) binding.category else "cobblemon:${binding.category}")
                    ?: throw CinematicValidationException(Component.translatable("cobblemon.cinematic_editor.preview.invalid_category", binding.actorId))
                ActorBinding.SynthBedrock(category, binding.aspects.toSet(), binding.scale)
            }
        }
        binding.actorId to actor
    }
}
