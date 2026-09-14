/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.runtime

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.entity.generic.GenericBedrockEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.net.messages.client.spawn.SpawnGenericBedrockPacket
import com.cobblemon.mod.common.net.messages.client.spawn.SpawnPokemonPacket
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket
import net.minecraft.network.protocol.game.VecDeltaCodec
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import java.util.UUID

/** Entities never enter the world tracker; packets are viewer-scoped. */
abstract class PreviewActor(
    val entity: Entity,
    val actorId: String,
    private val viewerProvider: () -> Collection<ServerPlayer>
) {

    private class Viewer(val player: ServerPlayer, entity: Entity) {
        val position = VecDeltaCodec().apply { setBase(entity.position()) }
        var yaw = entity.yRot
        var pitch = entity.xRot
        var headYaw = entity.yHeadRot
        var onGround = entity.onGround()
    }

    private val viewers = mutableMapOf<UUID, Viewer>()
    private var visible = false

    fun despawn() {
        visible = false
        val previous = viewers.values.toList()
        viewers.clear()
        previous.forEach { removeFrom(it.player) }
    }

    fun retainViewers(allowed: Set<UUID>) {
        val removed = viewers.filterKeys { it !in allowed }
        removed.keys.forEach(viewers::remove)
        removed.values.forEach { removeFrom(it.player) }
    }

    private fun removeFrom(player: ServerPlayer) {
        try {
            player.connection.send(ClientboundRemoveEntitiesPacket(entity.id))
        } catch (error: Exception) {
            Cobblemon.LOGGER.warn("Could not remove preview actor '{}' from viewer {}", actorId, player.uuid, error)
        }
    }

    fun syncTransform() {
        visible = true
        val current = viewerProvider().associateBy { it.uuid }
        retainViewers(current.keys)
        current.values.forEach(::spawnFor)
        val metadata = entity.entityData.packDirty()?.let { ClientboundSetEntityDataPacket(entity.id, it) }
        viewers.values.forEach { viewer ->
            metadata?.let { viewer.player.connection.send(it) }
            val position = entity.position()
            val yaw = entity.yRot
            val pitch = entity.xRot
            val headYaw = entity.yHeadRot
            val onGround = entity.onGround()
            val moved = position != viewer.position.base || yaw != viewer.yaw || pitch != viewer.pitch || onGround != viewer.onGround
            // Head rotation precedes movement, matching the world entity tracker.
            if (headYaw != viewer.headYaw) {
                viewer.player.connection.send(ClientboundRotateHeadPacket(entity, packedRotation(headYaw)))
            }
            if (moved) viewer.player.connection.send(buildPosPacket(viewer, yaw, pitch))
            viewer.position.setBase(position)
            viewer.yaw = yaw
            viewer.pitch = pitch
            viewer.headYaw = headYaw
            viewer.onGround = onGround
        }
    }

    fun sendCatchUpTo(player: ServerPlayer) {
        if (visible && viewerProvider().any { it === player }) spawnFor(player)
    }

    private fun spawnFor(player: ServerPlayer) {
        val previous = viewers[player.uuid]
        if (previous?.player === player) return
        val packet = buildSpawnPacket()
        previous?.let { removeFrom(it.player) }
        // Track before sending so partial spawn failures can still be cleaned up.
        viewers[player.uuid] = Viewer(player, entity)
        player.connection.send(packet)
        entity.entityData.nonDefaultValues?.let { player.connection.send(ClientboundSetEntityDataPacket(entity.id, it)) }
        player.connection.send(ClientboundRotateHeadPacket(entity, packedRotation(entity.yHeadRot)))
        player.connection.send(ClientboundTeleportEntityPacket(entity))
    }

    protected abstract fun buildSpawnPacket(): Packet<*>

    protected fun buildVanillaAddEntityPacket(): ClientboundAddEntityPacket =
        ClientboundAddEntityPacket(
            entity.id,
            entity.uuid,
            entity.x, entity.y, entity.z,
            entity.xRot, entity.yRot,
            entity.type,
            0,
            entity.deltaMovement,
            entity.yHeadRot.toDouble()
        )

    private fun buildPosPacket(viewer: Viewer, yaw: Float, pitch: Float): Packet<*> {
        val position = entity.position()
        val dx = viewer.position.encodeX(position)
        val dy = viewer.position.encodeY(position)
        val dz = viewer.position.encodeZ(position)
        val inShort = dx in Short.MIN_VALUE.toLong()..Short.MAX_VALUE.toLong()
            && dy in Short.MIN_VALUE.toLong()..Short.MAX_VALUE.toLong()
            && dz in Short.MIN_VALUE.toLong()..Short.MAX_VALUE.toLong()
        return if (inShort) {
            ClientboundMoveEntityPacket.PosRot(
                entity.id,
                dx.toShort(), dy.toShort(), dz.toShort(),
                packedRotation(yaw), packedRotation(pitch), entity.onGround()
            )
        } else {
            ClientboundTeleportEntityPacket(entity)
        }
    }

    private fun packedRotation(degrees: Float) = Mth.floor(degrees * 256f / 360f).toByte()

}

class PreviewPokemonActor(
    entity: PokemonEntity,
    actorId: String,
    viewerProvider: () -> Collection<ServerPlayer>
) : PreviewActor(entity, actorId, viewerProvider) {

    override fun buildSpawnPacket(): Packet<*> {
        val pokemon = entity as PokemonEntity
        return ClientboundCustomPayloadPacket(
            SpawnPokemonPacket(pokemon, buildVanillaAddEntityPacket())
        )
    }
}

class PreviewBedrockActor(
    entity: GenericBedrockEntity,
    actorId: String,
    viewerProvider: () -> Collection<ServerPlayer>
) : PreviewActor(entity, actorId, viewerProvider) {

    override fun buildSpawnPacket(): Packet<*> {
        val bedrock = entity as GenericBedrockEntity
        return ClientboundCustomPayloadPacket(
            SpawnGenericBedrockPacket(
                category = bedrock.category,
                aspects = bedrock.aspects,
                poseType = bedrock.entityData.get(GenericBedrockEntity.POSE_TYPE),
                scale = bedrock.scale,
                width = bedrock.colliderWidth,
                height = bedrock.colliderHeight,
                startAge = bedrock.tickCount,
                vanillaSpawnPacket = buildVanillaAddEntityPacket()
            )
        )
    }
}
