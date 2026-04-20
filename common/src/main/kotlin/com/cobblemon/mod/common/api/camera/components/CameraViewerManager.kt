/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.camera.components

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class CameraViewerManager {
    private val playerCameras = ConcurrentHashMap<UUID, UUID>()

    fun assignViewerToCamera(playerId: UUID, cameraId: UUID) {
        playerCameras[playerId] = cameraId
    }

    fun removeViewer(playerId: UUID): UUID? {
        return playerCameras.remove(playerId)
    }

    fun getCameraForPlayer(playerId: UUID): UUID? {
        return playerCameras[playerId]
    }

    fun removeViewersForCamera(cameraId: UUID) {
        playerCameras.entries.removeIf { it.value == cameraId }
    }

    fun clear() {
        playerCameras.clear()
    }
}
