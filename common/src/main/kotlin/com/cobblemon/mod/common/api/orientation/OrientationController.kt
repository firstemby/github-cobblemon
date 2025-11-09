/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.orientation

import com.cobblemon.mod.common.util.math.geometry.toDegrees
import com.cobblemon.mod.common.util.math.geometry.toRadians
import com.mojang.blaze3d.Blaze3D
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import org.joml.AxisAngle4f
import org.joml.Matrix3f
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

open class OrientationController(val entity: LivingEntity) {

    companion object {
        val FORWARDS: Vector3f = Vector3f(0.0f, 0.0f, -1.0f)
        val UP: Vector3f = Vector3f(0.0f, 1.0f, 0.0f)
        val LEFT: Vector3f = Vector3f(-1.0f, 0.0f, 0.0f)
    }

    open var active: Boolean = false

    var orientation: Matrix3f? = null
        private set

    private var renderOrientationO: Matrix3f? = null
    private var renderOrientation: Matrix3f? = null


    private var angularVelocity: Vector3f = Vector3f(0.0f, 0.0f, 0.0f)
    private var lastHandledRotationTime = Double.MIN_VALUE
    private var frameTime = 0.0

    /** Adding this simply because it irritates me seeing `getActive` when most of this is Java. */
    fun isActive() = active

    fun updateOrientation(fn: (Matrix3f) -> Matrix3f?) {
        if (!active) return
        if (orientation == null) {
            orientation = Matrix3f()
            rotate(entity.yRot - 180, entity.xRot, 0f)
        }
        orientation = fn(orientation!!)
        entity.yRot = yaw
        entity.xRot = pitch
    }

    fun reset() {
        orientation = null
        renderOrientationO = null
        renderOrientation = null
        active = false
    }

    fun rotate(yaw: Float, pitch: Float, roll: Float) {
        rotateYaw(yaw)
        rotatePitch(pitch)
        rotateRoll(roll)
    }

    fun getRenderOrientation(delta: Float): Quaternionf {
        val old = renderOrientationO ?: renderOrientation ?: orientation ?: Matrix3f()
        val new = renderOrientation ?: old
        val oldQuat = Quaternionf().setFromUnnormalized(old)
        val newQuat  = Quaternionf().setFromUnnormalized(new)
        oldQuat.slerp(newQuat, delta)
        return oldQuat
    }

    fun tick() {
//        val d = Blaze3D.getTime();
//        this.frameTime =
//            if (this.lastHandledRotationTime != Double.MIN_VALUE) d - this.lastHandledRotationTime else 0.0;
//        this.lastHandledRotationTime = d;
//
//        if (!active || orientation == null) return
//
//        // Find the desired angular velocity
//        // lerp the current ang vel towards the desired at a set rate (does this need to take into account frametime?
//        val current = orientation
//        val renderMatrix = this.renderOrientation ?: Matrix3f()
//        val renderQuat = Quaternionf().setFromUnnormalized(renderMatrix)
//        val targetQuat = Quaternionf().setFromUnnormalized(current)
//
//        // Max rate of angular change in radians per second
//        val maxAngVel = Math.toRadians(180.0).toFloat()
//
//        // Find the difference between the current render orientation and the target orientation
//        val qDiff: Quaternionf = targetQuat.mul(renderQuat.invert())
//
//        // Construct the target rate of change from the difference quat
//        val axAng = qDiff.get(AxisAngle4f())
//        val capdRate = axAng.angle.coerceIn(-maxAngVel, maxAngVel)
//        val targetAngVel = Vector3f(
//            axAng.x,
//            axAng.y,
//            axAng.z
//        ).mul(capdRate)
//
//        // lerp the current angular velocity towards the target
//        val k = 10.0f // Some smoothing constant k
//        val smoothingFactor = Math.min(1.0f, this.frameTime.toFloat() * k)
//        angularVelocity = angularVelocity.lerp(targetAngVel, smoothingFactor)
//
//        // Use the angular velocity to slerp the renderQuat toward the targetQuat
//        val frameDisplacement =
//            angularVelocity.mul(this.frameTime.toFloat()) // Angular displacement happening on this frame
//
//        val fAng = frameDisplacement.length() // Angle of displacement for this given pass/frame
//        if (fAng > 0.0001f) { // Ensure that the angle is significant enough that its worth normalizing and carrying out rotation
//            val fAx = frameDisplacement.normalize()
//            // Construct a rotation quat from the
//            val rotQuat = Quaternionf().fromAxisAngleRad(fAx, fAng)
//
//            // Set the new orientation post frame stepped rotation.
//            renderOrientation = Matrix3f().set(renderQuat.mul(rotQuat))
//        }
//
////        this.renderOrientation = orientation
//
//        // apply the new current angular velocity to the current render orientation
//        // construct a quat out of the angular velocity
//        // slerp the quat with a value of frametime

        val d = Blaze3D.getTime();
        this.frameTime = if (this.lastHandledRotationTime != Double.MIN_VALUE) d - this.lastHandledRotationTime else 0.0;
        this.lastHandledRotationTime = d;

        if (!active || orientation == null) return

        val current = orientation
        val renderMatrix = this.renderOrientation ?: Matrix3f()

        // Create mutable copies for manipulation
        val renderQuat = Quaternionf().setFromUnnormalized(renderMatrix)
        var targetQuat = Quaternionf().setFromUnnormalized(current)

        // --- Shortest Path Check (Still mandatory for Slerp) ---
        if (renderQuat.dot(targetQuat) < 0) {
            targetQuat = targetQuat.invert()
        }
        // --------------------------------------------------------

        // K is the speed (rad/s) we want to close the gap at.
        val k_rate = 10.0f // E.g., target 10 units/second closing rate.

        // Calculate the frame-rate independent alpha (t) for Slerp.
        // This makes the smoothing constant 'k_rate' a rate of change, not an arbitrary alpha value.
        val slerpAlpha = 1.0f - Math.exp(-(k_rate * this.frameTime)).toFloat()

        // Use Slerp to interpolate the renderQuat towards the targetQuat
        val newRenderQuat = renderQuat.slerp(targetQuat, slerpAlpha, Quaternionf())

        // Set the new orientation
        renderOrientation = Matrix3f().set(newRenderQuat)

        // Angular velocity is now redundant for rendering, but could be used for particle effects, etc.
    }

    val forwardVector: Vector3f
        get() = orientation?.transform(FORWARDS, Vector3f()) ?: Vector3f(FORWARDS)

    val leftVector: Vector3f
        get() = orientation?.transform(LEFT, Vector3f()) ?: Vector3f(LEFT)

    val upVector : Vector3f
        get() = orientation?.transform(UP, Vector3f()) ?: Vector3f(UP)

    fun rotateYaw(yaw: Float) = updateOrientation { it.rotateY(-yaw.toRadians()) }
    fun rotatePitch(pitch: Float) = updateOrientation { it.rotateX(-pitch.toRadians()) }
    fun rotateRoll(roll: Float) = updateOrientation { it.rotateZ(-roll.toRadians()) }

    val yaw: Float
        get() = Mth.wrapDegrees(-FORWARDS.angleSigned(forwardVector, UP).toDegrees() + 180)

    val pitch: Float
        get() = Mth.wrapDegrees(-asin(forwardVector.y).toDegrees())

    val roll: Float
        get() = Mth.wrapDegrees(-upVector.angleSigned(UP, forwardVector).toDegrees())

    fun applyGlobalYaw(deltaYawDegrees: Float) = updateOrientation { original ->
        return@updateOrientation original.rotateLocalY(-deltaYawDegrees.toRadians())
    }

    fun applyGlobalPitch(deltaPitchDegrees: Float) = updateOrientation { original ->
        val currQuat = Quaternionf().setFromUnnormalized(original)
        val horzLeftVector = Vector3f(0.0f, -abs(this.upVector.y.sign), 0.0f).cross(this.forwardVector)
        // Avoid NaN issue when normalizing and the forwardVector and upVector are equal
        val pitchAxis = if (horzLeftVector.lengthSquared() < 0.01) this.leftVector else horzLeftVector

        val globalPitch = Quaternionf().fromAxisAngleRad(pitchAxis.normalize(), -deltaPitchDegrees.toRadians())
        val resultQuat = globalPitch.mul(currQuat)
        return@updateOrientation Matrix3f().set(resultQuat)
    }

}
