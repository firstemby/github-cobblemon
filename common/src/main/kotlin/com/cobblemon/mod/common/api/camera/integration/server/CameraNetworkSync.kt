/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.camera.integration.server

import com.bedrockk.molang.runtime.MoLangRuntime
import com.bedrockk.molang.runtime.value.MoValue
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonNetwork.sendPacket
import com.cobblemon.mod.common.api.camera.engine.CameraTransform
import com.cobblemon.mod.common.api.camera.model.CameraFocus
import com.cobblemon.mod.common.net.messages.client.camera.CameraEndPacket
import com.cobblemon.mod.common.net.messages.client.camera.CameraFocusPacket
import com.cobblemon.mod.common.net.messages.client.camera.CameraStartPacket
import com.cobblemon.mod.common.net.messages.client.camera.CameraStatePacket
import com.cobblemon.mod.common.net.messages.client.camera.CameraTransformPacket
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import java.util.UUID

/**
 * Handles camera packet transmission to clients.
 *
 * Manages synchronization of camera state with connected viewers.
 * Uses modular packets with change tracking to minimize network traffic.
 */
class CameraNetworkSync(
    private val cameraId: UUID,
    private val viewers: Set<UUID>
) {
    private var lastFocusPosition: Vec3? = null
    private var lastRenderPlayerBody: Boolean = true
    private var lastInstantTransition: Boolean = false

    fun sendStart(startPosition: Vec3) {
        getPlayers().forEach { player ->
            player.sendPacket(CameraStartPacket(cameraId, startPosition))
        }

        // Send initial focus (None by default)
        sendFocusNone(force = true)

        // Send initial state
        sendState(
            instantTransition = false,
            renderPlayerBody = true,
            force = true
        )
    }

    fun sendTransform(
        transform: CameraTransform,
        updateCenterChunk: Boolean
    ) {
        val packet = CameraTransformPacket(
            cameraId = cameraId,
            position = transform.position,
            rotation = transform.rotation,
            updateCenterChunk = updateCenterChunk
        )

        getPlayers().forEach { it.sendPacket(packet) }
    }

    fun sendFocusNone(force: Boolean = false) {
        if (!force && lastFocusPosition == null) {
            return
        }

        val packet = CameraFocusPacket(
            cameraId = cameraId,
            focus = CameraFocusPacket.FocusData.None
        )

        getPlayers().forEach { it.sendPacket(packet) }
        lastFocusPosition = null
    }

    fun sendFocusPoint(focusPosition: Vec3?, force: Boolean = false) {
        if (!force && focusPosition == lastFocusPosition) {
            return
        }

        val focusData = when (focusPosition) {
            null -> CameraFocusPacket.FocusData.None
            else -> CameraFocusPacket.FocusData.Point(focusPosition)
        }

        val packet = CameraFocusPacket(
            cameraId = cameraId,
            focus = focusData
        )

        getPlayers().forEach { it.sendPacket(packet) }
        lastFocusPosition = focusPosition
    }

    fun sendFocusBone(
        boneFocus: CameraFocus.Bone,
        context: Map<String, MoValue>,
        runtime: MoLangRuntime,
        force: Boolean = false
    ) {
        val entity = CameraFocus.resolveEntity(boneFocus.selector, context, runtime)

        if (entity == null) {
            Cobblemon.LOGGER.debug("[CameraNetworkSync] Bone focus entity invalid")
            sendFocusNone(force)
            return
        }

        val offset = boneFocus.offset.toVec3(context, runtime)

        Cobblemon.LOGGER.debug("[CameraNetworkSync] Sending bone focus: entityId=${entity.id}, bone=${boneFocus.boneName}, offset=$offset, smooth=${boneFocus.smooth}")

        val focusData = CameraFocusPacket.FocusData.Bone(
            entityId = entity.id,
            boneName = boneFocus.boneName,
            offset = offset,
            smooth = boneFocus.smooth
        )

        val packet = CameraFocusPacket(
            cameraId = cameraId,
            focus = focusData
        )

        getPlayers().forEach { it.sendPacket(packet) }

        // Invalidate lastFocusPosition when switching to bone focus.
        // This ensures that when transitioning back to a point focus,
        // the packet will always be sent (not incorrectly deduplicated).
        lastFocusPosition = null
    }

    fun sendState(
        instantTransition: Boolean,
        renderPlayerBody: Boolean,
        force: Boolean = false
    ) {
        if (!force &&
            instantTransition == lastInstantTransition &&
            renderPlayerBody == lastRenderPlayerBody) {
            return
        }

        val packet = CameraStatePacket(
            cameraId = cameraId,
            instantTransition = instantTransition,
            renderPlayerBody = renderPlayerBody
        )

        getPlayers().forEach { it.sendPacket(packet) }
        lastInstantTransition = instantTransition
        lastRenderPlayerBody = renderPlayerBody
    }

    fun sendEnd() {
        val packet = CameraEndPacket(cameraId)
        getPlayers().forEach { it.sendPacket(packet) }
    }

    /**
     * Send end packet to a specific player.
     * Used when removing a single viewer without stopping the entire camera.
     */
    fun sendEndToPlayer(player: ServerPlayer) {
        player.sendPacket(CameraEndPacket(cameraId))
    }

    private fun getPlayers(): List<ServerPlayer> {
        val server = Cobblemon.implementation.server() ?: return emptyList()
        return viewers.mapNotNull { server.playerList.getPlayer(it) }
    }
}
