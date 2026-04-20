/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.camera.components

import com.cobblemon.mod.common.api.camera.ICameraController
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.camera.CameraStoppedEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class CameraLifecycleManager {
    private val cameras = ConcurrentHashMap<UUID, ICameraController>()

    fun registerCamera(camera: ICameraController) {
        cameras[camera.id] = camera
    }

    fun unregisterCamera(cameraId: UUID): ICameraController? {
        return cameras.remove(cameraId)
    }

    fun getCamera(cameraId: UUID): ICameraController? = cameras[cameraId]

    fun getAllCameras(): Collection<ICameraController> = cameras.values

    fun tick() {
        cameras.values.forEach { it.tick() }

        cameras.entries.removeIf { (id, camera) ->
            if (!camera.isActive()) {
                CobblemonEvents.CAMERA_STOPPED_POST.emit(
                    CameraStoppedEvent.Post(id, camera, CameraStoppedEvent.StopReason.COMPLETED)
                )
                true
            } else {
                false
            }
        }
    }

    fun clear() {
        cameras.values.forEach {
            it.stop()
            CobblemonEvents.CAMERA_STOPPED_POST.emit(
                CameraStoppedEvent.Post(it.id, it, CameraStoppedEvent.StopReason.MANUAL)
            )
        }
        cameras.clear()
    }

    fun size(): Int = cameras.size
}
