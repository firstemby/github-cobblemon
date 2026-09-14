/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.camera.compositor

import com.cobblemon.mod.common.client.render.camera.compositor.layout.DiagonalSplitLayout
import com.cobblemon.mod.common.client.render.camera.compositor.layout.PictureInPictureLayout
import com.cobblemon.mod.common.client.render.camera.compositor.layout.SideBySideLayout
import com.cobblemon.mod.common.client.render.camera.compositor.layout.SingleFeedLayout
import net.minecraft.network.RegistryFriendlyByteBuf
import java.util.UUID

object CompositorLayoutSerializer {

    fun encode(layout: CompositorLayout, buffer: RegistryFriendlyByteBuf) {
        buffer.writeUtf(layout.type)
        when (layout) {
            is SingleFeedLayout -> {
                buffer.writeUUID(layout.cameraId)
            }
            is DiagonalSplitLayout -> {
                buffer.writeUUID(layout.primaryCameraId)
                buffer.writeUUID(layout.secondaryCameraId)
                buffer.writeFloat(layout.splitAngleDegrees)
                buffer.writeFloat(layout.gapWidth)
            }
            is PictureInPictureLayout -> {
                buffer.writeUUID(layout.mainCameraId)
                buffer.writeUUID(layout.pipCameraId)
                buffer.writeFloat(layout.pipX)
                buffer.writeFloat(layout.pipY)
                buffer.writeFloat(layout.pipWidth)
                buffer.writeFloat(layout.pipHeight)
                buffer.writeFloat(layout.borderWidth)
            }
            is SideBySideLayout -> {
                buffer.writeUUID(layout.leftCameraId)
                buffer.writeUUID(layout.rightCameraId)
                buffer.writeBoolean(layout.vertical)
                buffer.writeFloat(layout.splitRatio)
                buffer.writeFloat(layout.gapWidth)
            }
            else -> throw IllegalArgumentException("Unknown layout type: ${layout.type}")
        }
    }

    fun decode(buffer: RegistryFriendlyByteBuf): CompositorLayout {
        return when (val type = buffer.readUtf()) {
            SingleFeedLayout.TYPE -> SingleFeedLayout(
                cameraId = buffer.readUUID()
            )
            DiagonalSplitLayout.TYPE -> DiagonalSplitLayout(
                primaryCameraId = buffer.readUUID(),
                secondaryCameraId = buffer.readUUID(),
                splitAngleDegrees = buffer.readFloat(),
                gapWidth = buffer.readFloat()
            )
            PictureInPictureLayout.TYPE -> PictureInPictureLayout(
                mainCameraId = buffer.readUUID(),
                pipCameraId = buffer.readUUID(),
                pipX = buffer.readFloat(),
                pipY = buffer.readFloat(),
                pipWidth = buffer.readFloat(),
                pipHeight = buffer.readFloat(),
                borderWidth = buffer.readFloat()
            )
            SideBySideLayout.TYPE -> SideBySideLayout(
                leftCameraId = buffer.readUUID(),
                rightCameraId = buffer.readUUID(),
                vertical = buffer.readBoolean(),
                splitRatio = buffer.readFloat(),
                gapWidth = buffer.readFloat()
            )
            else -> throw IllegalArgumentException("Unknown layout type: $type")
        }
    }
}
