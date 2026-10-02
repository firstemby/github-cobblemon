/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.riding.util

import com.cobblemon.mod.common.OrientationControllable
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.math.geometry.toRadians
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import org.joml.Matrix3f
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

/**
 * Class to handle the updating and holding of motion data used in animation. Motion values accumulate for every
 * pokemon on the client, some only while ridden.
 *
 * @author Jackowes
 * @since April 7th, 2025
 */


class DynamicAnimationData(val ride: PokemonEntity)
{
    companion object {
        // Blend time for motion values, they catch up within 1.5x this. Longer is smoother but lags more
        const val MOTION_BLEND_SECONDS = 0.5F
        // Rider input comes straight from key presses so it catches up quicker
        const val INPUT_BLEND_SECONDS = 0.3F
    }

    var prevOrientation: Matrix3f = Matrix3f()
    var prevRot: Vec3 = Vec3.ZERO
    var velocitySpring = Vec3Ease(MOTION_BLEND_SECONDS)
    var rotSpring = Vec3Ease(MOTION_BLEND_SECONDS, isAngle = true)
    var rotDeltaSpring = Vec3Ease(MOTION_BLEND_SECONDS)
    var localVelocitySpring = Vec3Ease(MOTION_BLEND_SECONDS)
    var driverInputSpring = Vec3Ease(INPUT_BLEND_SECONDS)
    var diveSpring = Vec3Ease(MOTION_BLEND_SECONDS)
    private var initialized = false
    private var dismounted = false

    /** Called on dismount. Motion keeps easing on its own, only the ride orientation needs to start fresh. */
    fun clear() {
        prevOrientation = Matrix3f()
        dismounted = true
    }

    fun update() {
        val activeRide = ride.hasControllingPassenger()

        var currRot = Vec3.ZERO
        var angDelta = Vec3.ZERO

        // Update velocity first
        val currentVelocity = Vec3(
            ride.x - ride.xOld,
            ride.y - ride.yOld,
            ride.z - ride.zOld
        )

        val r = activeRide && ride.ifRidingAvailableSupply(false) { behaviour, settings, state ->
            behaviour.shouldRoll(settings, state, ride);
        }
        if( r ) {
            //TODO: should this return?
            val controller = (ride as? OrientationControllable)?.orientationController ?: return
            val orientation = controller.orientation ?: Matrix3f()

            val currPitch = controller.pitch
            val currYaw = controller.yaw
            val currRoll = controller.roll
            currRot = Vec3(currPitch.toDouble(), currYaw.toDouble(), currRoll.toDouble())

            val currOrientation = orientation

            /******************************************************
             * Calculate current 3d euler rotation deltas in the
             * entities local space
             *****************************************************/

            // Get current and previous orientation as quaternions
            val currQuat = Quaternionf().setFromUnnormalized(currOrientation)
            val prevQuat = Quaternionf().setFromUnnormalized(prevOrientation)

            // Compute deltaQuat = current * inverse(previous)
            val deltaQuat = Quaternionf(currQuat).mul(Quaternionf(prevQuat).invert())

            // Transform delta into the local space of the current orientation
            val localDeltaQuat = Quaternionf(currQuat).invert().mul(deltaQuat).mul(currQuat)

            // Convert delta rotation to Euler angles
            val euler = Vector3f()
            localDeltaQuat.getEulerAnglesXYZ(euler) // Angles are in radians

            // Convert to degrees
            val pitchDegrees = Math.toDegrees(euler.x.toDouble()) // Rotation around X axis = pitch
            val yawDegrees = Math.toDegrees(euler.y.toDouble())   // Rotation around Y axis = yaw
            val rollDegrees = Math.toDegrees(euler.z.toDouble())  // Rotation around Z axis = roll
            //Scale rotation delta by 20 to represent degree per second instead of tick
            angDelta = Vec3(pitchDegrees, yawDegrees, rollDegrees).scale(20.0)

            /******************************************************
             * Calculate ride local velocity
             *****************************************************/
            val velocityWorldF = Vector3f(currentVelocity.x.toFloat(), currentVelocity.y.toFloat(), currentVelocity.z.toFloat())

            val localVelocityF = Matrix3f(orientation).transpose().transform(velocityWorldF)
            val localVelocity = Vec3(localVelocityF.x.toDouble(), localVelocityF.y.toDouble(), localVelocityF.z.toDouble())

            localVelocitySpring.update(localVelocity)

            // Update previous values
            prevOrientation.set(currOrientation)
        } else {
            // Pitch only means something while ridden
            val currPitch = if (activeRide) ride.xRot else 0.0f
            val currYaw = ride.yRot
            val currRoll = 0.0f
            currRot = Vec3(currPitch.toDouble(), currYaw.toDouble(), currRoll.toDouble())

            // Start from where the pokemon already is instead of turning in from zero
            if (!initialized) {
                prevRot = currRot
                rotSpring.snap(currRot)
                initialized = true
            }
            // measure turning from here rather than as one big turn
            if (dismounted) {
                prevRot = currRot
                dismounted = false
            }

            //Scale rotation delta by 20 to represent degree per second instead of tick
            angDelta = Vec3(
                (currRot.x - prevRot.x),
                Mth.degreesDifference(currRot.y.toFloat(), prevRot.y.toFloat()).toDouble(),
                Mth.degreesDifference(currRot.z.toFloat(), prevRot.z.toFloat()).toDouble()
            ).scale(20.0)

            /******************************************************
             * Calculate ride local velocity
             *****************************************************/
            val yaw360 = -1.0 * (ride.yRot + 360.0f) % 360.0f
            val orientationNonRollable = Matrix3f().rotateY(yaw360.toRadians())

            //Convert to Vector3f
            var velocityWorldF = Vector3f(currentVelocity.x.toFloat(), currentVelocity.y.toFloat(), currentVelocity.z.toFloat())

            val localVelocityF = Matrix3f(orientationNonRollable).transpose().transform(velocityWorldF)
            val localVelocity = Vec3(localVelocityF.x.toDouble(), localVelocityF.y.toDouble(), localVelocityF.z.toDouble())

            localVelocitySpring.update(localVelocity)
        }

        /******************************************************
         * Update springs
         *****************************************************/
        val driver = ride.controllingPassenger
        val vertInput = when {
            driver != null && driver.jumping-> 1.0
            driver != null && driver.isShiftKeyDown -> -1.0
            else -> 0.0
        }
        val input = if(driver != null && activeRide) Vec3(driver.xxa.toDouble() * -1, vertInput, driver.zza.toDouble()) else Vec3.ZERO
        driverInputSpring.update(input)
        rotDeltaSpring.update(angDelta)
        velocitySpring.update(currentVelocity)
        rotSpring.update(currRot)

        val diving = abs(max(sin(currRot.x.toRadians()),0f))
        diveSpring.update(Vec3(0.0,diving.toDouble(),0.0))
        prevRot = currRot
    }
}