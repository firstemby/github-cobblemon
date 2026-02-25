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
import com.cobblemon.mod.common.api.camera.model.CameraFocus
import com.cobblemon.mod.common.api.camera.track.FiredGuiAction
import com.cobblemon.mod.common.api.camera.track.GuiActionType
import net.minecraft.core.SectionPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.Mth
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.phys.Vec3

/**
 * Executes camera timeline and manages focus resolution.
 *
 * Coordinates between CameraInstance, viewer/chunk managers, and network sync.
 */
class CameraExecutor(
    private val instance: CameraInstance,
    private val runtime: MoLangRuntime,
    private val context: Map<String, MoValue>,
    private val guiActionHandler: ((FiredGuiAction) -> Unit)? = null
) {
    companion object {
        private const val CHUNK_UPDATE_DISTANCE_SQ = (16 * 16).toDouble()
    }

    private var lastCenterChunkUpdate: Vec3? = null

    fun tick(
        level: ServerLevel,
        networkSync: CameraNetworkSync,
        onChunkUpdate: (ChunkPos) -> Unit
    ): Boolean {
        if (!instance.isActive()) return false

        val transform = instance.tick() ?: run {
            if (!instance.isActive()) instance.stop()
            return false
        }

        val shouldUpdateCenter = lastCenterChunkUpdate?.let { last ->
            transform.position.distanceToSqr(last) > CHUNK_UPDATE_DISTANCE_SQ
        } ?: true

        if (shouldUpdateCenter) {
            val chunkPos = chunkPosFrom(transform.position)
            onChunkUpdate(chunkPos)
            lastCenterChunkUpdate = transform.position
        }

        val instantTransition = instance.consumeInstantTransitionFlag()

        networkSync.sendTransform(transform, shouldUpdateCenter)

        when (val focus = transform.focus) {
            null -> networkSync.sendFocusNone()
            is CameraFocus.Bone -> networkSync.sendFocusBone(focus, context, runtime)
            else -> {
                val resolvedFocus = resolveFocusPosition(focus)
                val finalFocusPos = instance.focusOverride ?: resolvedFocus
                networkSync.sendFocusPoint(finalFocusPos)
            }
        }

        networkSync.sendState(
            instantTransition = instantTransition,
            renderPlayerBody = instance.getCurrentRenderPlayerBody(),
            hiddenHudElements = instance.getCurrentHiddenHudElements()
        )

        if (guiActionHandler != null) {
            val guiActions = instance.consumeGuiActions()
            for (action in guiActions) {
                guiActionHandler.invoke(action)
            }
        }

        return true
    }

    fun resolveFocusPosition(focus: CameraFocus): Vec3? {
        return when (focus) {
            is CameraFocus.Entity -> {
                val entity = CameraFocus.resolveEntity(focus.selector, context, runtime)
                if (entity != null) {
                    val position = entity.position()
                    instance.updateLastValidFocusPosition(position)
                    position
                } else {
                    val cached = instance.lastValidFocusPosition
                    if (cached != null) {
                        Cobblemon.LOGGER.debug("[CameraExecutor] Entity focus invalid, using last valid position")
                        cached
                    } else {
                        Cobblemon.LOGGER.warn("[CameraExecutor] Entity focus invalid and no cached position, using origin")
                        instance.origin
                    }
                }
            }
            is CameraFocus.Point -> {
                val position = focus.position.toVec3(context, runtime, instance.origin, false)
                instance.updateLastValidFocusPosition(position)
                position
            }
            is CameraFocus.Bone -> {
                // Bone focus is resolved client-side, so return entity position as fallback
                CameraFocus.resolveEntity(focus.selector, context, runtime)?.position()
                    ?: instance.lastValidFocusPosition
            }
        }
    }

    private fun chunkPosFrom(position: Vec3): ChunkPos {
        val chunkX = SectionPos.blockToSectionCoord(Mth.floor(position.x))
        val chunkZ = SectionPos.blockToSectionCoord(Mth.floor(position.z))
        return ChunkPos(chunkX, chunkZ)
    }
}
