/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.camera

import com.cobblemon.mod.common.api.camera.CameraRotation
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import kotlin.math.atan2
import kotlin.math.sqrt

object CameraInterpolationUtils {

    fun lerpPosition(from: Vec3, to: Vec3, progress: Float): Vec3 {
        return Vec3(
            Mth.lerp(progress.toDouble(), from.x, to.x),
            Mth.lerp(progress.toDouble(), from.y, to.y),
            Mth.lerp(progress.toDouble(), from.z, to.z)
        )
    }

    private fun angleDelta(from: Float, to: Float): Float =
        Mth.wrapDegrees(Mth.wrapDegrees(to) - Mth.wrapDegrees(from))

    internal fun lerpAngle(from: Float, to: Float, progress: Float): Float =
        Mth.wrapDegrees(Mth.wrapDegrees(from) + angleDelta(from, to) * progress)

    fun lerpRotation(from: CameraRotation, to: CameraRotation, progress: Float): CameraRotation = CameraRotation(
        lerpAngle(from.yaw, to.yaw, progress),
        lerpAngle(from.pitch, to.pitch, progress)
    )

    fun lerpRotationClamped(
        from: CameraRotation,
        to: CameraRotation,
        progress: Float,
        deltaSeconds: Float,
        maxDegreesPerSecond: Float
    ): CameraRotation {
        val limit = maxDegreesPerSecond * deltaSeconds
        val yawChange = (angleDelta(from.yaw, to.yaw) * progress).coerceIn(-limit, limit)
        val pitchChange = (angleDelta(from.pitch, to.pitch) * progress).coerceIn(-limit, limit)
        return CameraRotation(
            Mth.wrapDegrees(Mth.wrapDegrees(from.yaw) + yawChange),
            Mth.wrapDegrees(Mth.wrapDegrees(from.pitch) + pitchChange)
        )
    }

    fun calculateLookAtRotation(from: Vec3, to: Vec3): CameraRotation {
        val dx = to.x - from.x
        val dy = to.y - from.y
        val dz = to.z - from.z

        val horizontalDistance = sqrt(dx * dx + dz * dz)

        val yaw = Math.toDegrees(atan2(dz, dx)).toFloat() - 90f
        val pitch = -Math.toDegrees(atan2(dy, horizontalDistance)).toFloat()

        return CameraRotation(yaw, pitch)
    }
}
