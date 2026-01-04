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

    fun slerpRotation(from: CameraRotation, to: CameraRotation, progress: Float): CameraRotation {
        fun normalizeAngle(angle: Float): Float {
            var normalized = angle % 360f
            if (normalized > 180f) normalized -= 360f
            if (normalized < -180f) normalized += 360f
            return normalized
        }

        fun shortestAngleDifference(from: Float, to: Float): Float {
            return normalizeAngle(to - from)
        }

        val yawDiff = shortestAngleDifference(from.yaw, to.yaw)
        val pitchDiff = shortestAngleDifference(from.pitch, to.pitch)

        return CameraRotation(
            yaw = normalizeAngle(from.yaw + yawDiff * progress),
            pitch = normalizeAngle(from.pitch + pitchDiff * progress)
        )
    }

    fun slerpRotationClamped(
        from: CameraRotation,
        to: CameraRotation,
        progress: Float,
        deltaSeconds: Float,
        maxDegreesPerSecond: Float
    ): CameraRotation {
        fun normalizeAngle(angle: Float): Float {
            var normalized = angle % 360f
            if (normalized > 180f) normalized -= 360f
            if (normalized < -180f) normalized += 360f
            return normalized
        }

        fun shortestAngleDifference(from: Float, to: Float): Float {
            return normalizeAngle(to - from)
        }

        val yawDiff = shortestAngleDifference(from.yaw, to.yaw)
        val pitchDiff = shortestAngleDifference(from.pitch, to.pitch)

        val targetYawChange = yawDiff * progress
        val targetPitchChange = pitchDiff * progress

        val maxRotationThisFrame = maxDegreesPerSecond * deltaSeconds

        val clampedYawChange = targetYawChange.coerceIn(-maxRotationThisFrame, maxRotationThisFrame)
        val clampedPitchChange = targetPitchChange.coerceIn(-maxRotationThisFrame, maxRotationThisFrame)

        return CameraRotation(
            yaw = normalizeAngle(from.yaw + clampedYawChange),
            pitch = normalizeAngle(from.pitch + clampedPitchChange)
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
