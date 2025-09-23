/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.riding.behaviour.types.air

import com.bedrockk.molang.Expression
import com.bedrockk.molang.runtime.MoLangMath.lerp
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.OrientationControllable
import com.cobblemon.mod.common.api.orientation.OrientationController
import com.cobblemon.mod.common.api.riding.RidingStyle
import com.cobblemon.mod.common.api.riding.behaviour.*
import com.cobblemon.mod.common.api.riding.posing.PoseOption
import com.cobblemon.mod.common.api.riding.posing.PoseProvider
import com.cobblemon.mod.common.api.riding.sound.RideSoundSettingsList
import com.cobblemon.mod.common.api.riding.stats.RidingStat
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.*
import com.cobblemon.mod.common.util.math.geometry.toRadians
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.SmoothDouble
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import kotlin.math.*

class BirdBehaviour : RidingBehaviour<BirdSettings, BirdState> {
    companion object {
        val KEY = cobblemonResource("air/bird")
    }

    override val key: ResourceLocation = KEY

    override fun getRidingStyle(settings: BirdSettings, state: BirdState): RidingStyle {
        return RidingStyle.AIR
    }

    val poseProvider = PoseProvider<BirdSettings, BirdState>(PoseType.HOVER)
        .with(PoseOption(PoseType.FLY) { _, state, _ -> state.rideVelocity.get().z > 0.2 })

    override fun isActive(settings: BirdSettings, state: BirdState, vehicle: PokemonEntity): Boolean {
        return true
    }

    override fun pose(settings: BirdSettings, state: BirdState, vehicle: PokemonEntity): PoseType {
        return poseProvider.select(settings, state, vehicle)
    }

    override fun speed(
        settings: BirdSettings,
        state: BirdState,
        vehicle: PokemonEntity,
        driver: Player
    ): Float {
        return state.rideVelocity.get().length().toFloat()
    }

    override fun tick(
        settings: BirdSettings,
        state: BirdState,
        vehicle: PokemonEntity,
        driver: Player,
        input: Vec3
    ) {
        if(vehicle.level().isClientSide) {
            tickStamina(settings, state, vehicle, driver)
            checkUpsideDown(state, vehicle, driver)
        }
    }

    private fun checkUpsideDown(
        state: BirdState,
        vehicle: PokemonEntity,
        driver: Player,
    ) {
        // Also checks stamina
        val controller = (driver as OrientationControllable).orientationController
        if (cos(controller.roll.toRadians()) < 0.0) {
            val upsideDownRate = 1.0 / (2.0 * 20.0) // 2 seconds to max out upsideDown force
            state.currUpsideDownForce.set( min(1.0, state.currUpsideDownForce.get() + upsideDownRate * abs(cos(controller.roll.toRadians()))))
        } else if (state.stamina.get() == 0.0f) {
            val upsideDownRate = 1.0 / (2.0 * 20.0) // 1 seconds to max out
            state.currUpsideDownForce.set( min(1.0, state.currUpsideDownForce.get() + upsideDownRate))
        } else { // Its right side up or has stamina so reset it
            state.currUpsideDownForce.set(0.0)
        }
    }

    fun tickStamina(
        settings: BirdSettings,
        state: BirdState,
        vehicle: PokemonEntity,
        driver: Player
    ) {
        val stam = state.stamina.get()

        // Grab the boost time in seconds and convert to ticks. Then calculate the drain rate as inversely
        // proportional to the number of ticks of boost thus making a full boost take x ticks
        // in short: "Stamina drains slower at higher values and also replenishes slower"
        val totalStam = vehicle.runtime.resolveDouble(settings.staminaExpr) * 20.0f
        val stamDrainRate = (1.0f / totalStam).toFloat()

        val newStam = if (driver.zza > 0.0) max(0.0f,stam - stamDrainRate)
            else if (!state.gliding.get()) max(0.0f,stam - stamDrainRate * 0.5f) // if hovering half the stam drain
            else stam

        state.stamina.set(newStam)
    }

    override fun rotation(
        settings: BirdSettings,
        state: BirdState,
        vehicle: PokemonEntity,
        driver: LivingEntity
    ): Vec2 {
        return Vec2(driver.xRot * 0.5f, driver.yRot)
    }

    override fun velocity(
        settings: BirdSettings,
        state: BirdState,
        vehicle: PokemonEntity,
        driver: Player,
        input: Vec3
    ): Vec3 {
        var upForce = 0.0
        var forwardForce = 0.0

        //Perform ride velocity update
        calculateRideSpaceVel(settings, state, vehicle, driver)

        //Translate ride space velocity to world space velocity.
        val controller = (driver as? OrientationControllable)?.orientationController
        if (controller != null) {
            //Need to deadzone this when straight up or down

            upForce += -1.0 * sin(Math.toRadians(controller.pitch.toDouble())) * state.rideVelocity.get().z
            forwardForce += cos(Math.toRadians(controller.pitch.toDouble())) * state.rideVelocity.get().z

            upForce += cos(Math.toRadians(controller.pitch.toDouble())) * state.rideVelocity.get().y
            forwardForce += sin(Math.toRadians(controller.pitch.toDouble())) * state.rideVelocity.get().y
        }

        // The downward force used to encourage players to stop flying upside down.
        val extraDownwardForce = state.currUpsideDownForce.get().pow(2) * -0.3 // 6 blocks a second downward

        val velocity = Vec3(state.rideVelocity.get().x , upForce + extraDownwardForce, forwardForce)
        return velocity
    }

    /*
    *  Calculates the change in the ride space vector due to player input and ride state
    */
    fun calculateRideSpaceVel(settings: BirdSettings, state: BirdState, vehicle: PokemonEntity, driver: Player) {
        // retrieve stats
        val topSpeed = (vehicle.runtime.resolveDouble(settings.speedExpr) / 20.0)
        val accel = topSpeed / (vehicle.runtime.resolveDouble(settings.accelerationExpr) * 20.0)
        val glideTopSpeed = topSpeed * vehicle.runtime.resolveDouble(settings.glidespeedExpr)
        var glideSpeedChange = 0.0
        var activeInput = false

        var newVelocity = Vec3(state.rideVelocity.get().x, state.rideVelocity.get().y, state.rideVelocity.get().z)

        // Account for collision
        if (vehicle.verticalCollision || vehicle.horizontalCollision) {
            newVelocity = newVelocity.normalize().scale(vehicle.deltaMovement.length())
        }

        //speed up and slow down based on input
        if (driver.zza != 0.0f && state.stamina.get() > 0.0) {
            //make sure it can't exceed top speed
            val forwardInput = when {
                state.stamina.get() == 0.0f -> 0.0
                driver.zza > 0 && newVelocity.z > topSpeed -> 0.0
                driver.zza < 0 && newVelocity.z < (-topSpeed / 3.0) -> 0.0
                else -> driver.zza.sign
            }

            newVelocity = Vec3(
                newVelocity.x,
                newVelocity.y,
                (newVelocity.z + (accel * forwardInput.toDouble())))

            activeInput = true
        }

        val controller = (driver as? OrientationControllable)?.orientationController
        if (controller != null) {
            //Base glide speed change on current pitch of the ride.
            glideSpeedChange = sin(Math.toRadians(controller.pitch.toDouble()))
            glideSpeedChange = glideSpeedChange * 0.25

            if (glideSpeedChange <= 0.0) {
                //Ensures that a propelling force is still able to be applied when
                //climbing in height
                if (driver.zza <= 0) {
                    //speed decrease should be 2x speed increase?
                    //state.currSpeed = max(state.currSpeed + (0.0166) * glideSpeedChange, 0.0 )
                    newVelocity = Vec3(
                        newVelocity.x,
                        newVelocity.y,
                        lerp( newVelocity.z, 0.0,glideSpeedChange * -0.0166 * 2 )
                    )
                }
            } else {
                // only add to the speed if it hasn't exceeded the current
                //glide angles maximum amount of speed that it can give.
                //state.currSpeed = min(state.currSpeed + ((0.0166 * 2) * glideSpeedChange), maxGlideSpeed)
                newVelocity = Vec3(
                    newVelocity.x,
                    newVelocity.y,
                    min(newVelocity.z + ((0.0166 * 2) * glideSpeedChange), glideTopSpeed)
                )
            }
        }

        //Vertical movement based on driver input.
        val vertTopSpeed = topSpeed / 2.0
        val vertInput = when {
            state.stamina.get() == 0.0f -> 0.0
            driver.jumping && state.rideVelocity.get().length() < 0.3 -> 1.0
            driver.isShiftKeyDown && state.rideVelocity.get().length() < 0.3  -> -1.0
            else -> 0.0
        }

        if (vertInput != 0.0 && state.stamina.get() > 0.0) {
            newVelocity = Vec3(
                newVelocity.x,
                (newVelocity.y + (accel * vertInput)).coerceIn(-vertTopSpeed, vertTopSpeed),
                newVelocity.z)
            activeInput = true
        }
        else {
            newVelocity = Vec3(
                newVelocity.x,
                lerp(newVelocity.y, 0.0, vertTopSpeed / 20.0),
                newVelocity.z)
        }

        //Check if the ride should be gliding
        if (driver.isLocalPlayer) {
            if ((activeInput && state.stamina.get() > 0.0) || state.rideVelocity.get().z < 0.2) {
                state.gliding.set(false)
            } else {
                state.gliding.set(true)
            }
        }

        //air resistance
        if( abs(newVelocity.z) > 0) {
            newVelocity = newVelocity.subtract(0.0, 0.0, min(0.0015 * newVelocity.z.sign, newVelocity.z))
        }
        state.rideVelocity.set(newVelocity)
    }

    override fun angRollVel(
        settings: BirdSettings,
        state: BirdState,
        vehicle: PokemonEntity,
        driver: Player,
        deltaTime: Double
    ): Vec3 {
        if (Cobblemon.config.disableRoll) return Vec3.ZERO
        if (driver !is OrientationControllable) return Vec3.ZERO
        val controller = (driver as OrientationControllable).orientationController

        val handling = vehicle.runtime.resolveDouble(settings.handlingExpr)
        val topSpeed = vehicle.runtime.resolveDouble(settings.speedExpr) / 20.0

        val yawDeltaDeg =  deltaTime * handling * sin(Math.toRadians(controller.roll.toDouble())).pow(2) * sin(Math.toRadians(controller.roll.toDouble())).sign
        val trueYawDelt = yawDeltaDeg * abs(cos(Math.toRadians(controller.pitch.toDouble()))) * sqrt(RidingBehaviour.scaleToRange(state.rideVelocity.get().length(), 0.0, topSpeed))

        // Dampen yaw when upside down
        val yawDampen = (1 - abs(min(cos(controller.roll.toRadians()),0.0f)))
        controller.applyGlobalYaw(trueYawDelt.toFloat() * yawDampen)

        // Correct orientation when at low speeds
        correctOrientation(settings, state, vehicle, controller, deltaTime)

        //yaw, pitch, roll
        return Vec3(0.0, state.currPitchCorrectionForce.get(), state.currRollCorrectionForce.get())
    }

    private fun correctOrientation(
        settings: BirdSettings,
        state: BirdState,
        vehicle: PokemonEntity,
        controller: OrientationController,
        deltaTime: Double
    ) {
        // Calculate correcting roll force.
        // Mult by cos(pitch) to ensure that it doesn't roll correct at when diving or climbing
        val rollCorrectionTimer = vehicle.runtime.resolveDouble(settings.timeToRollCorrect)
        val maxRollCorrectionRate = if (state.rideVelocity.get().length() < 0.1) 15.0f else 8.0f * (cos(controller.pitch.toRadians())).pow(2) * ((state.noInputTimeRoll.get() - rollCorrectionTimer) * 2).coerceIn(0.0, 1.0).toFloat()
        val maxRollForce = maxRollCorrectionRate / 40.0
        val rollArrivalDeg = 90.0
        if(state.rideVelocity.get().length() < 0.1 || state.noInputTimeRoll.get() > rollCorrectionTimer) {
            val arrivalInfluence = (min(rollArrivalDeg,abs(controller.roll).toDouble()) / rollArrivalDeg)
            val desiredRollForce = (0 - controller.roll).sign * maxRollCorrectionRate * arrivalInfluence
            var steeredRollForce = desiredRollForce - state.currRollCorrectionForce.get()

            // Ensure the 'steering' correction force doesn't exceed our maxForce
            if (abs(steeredRollForce) > maxRollForce) {
                steeredRollForce = steeredRollForce.sign * maxRollForce
            }

            // Apply the 'steered' force (its really not a steering force I need a better name)
            state.currRollCorrectionForce.set(
                state.currRollCorrectionForce.get() + steeredRollForce
            )
        } else {
            state.currRollCorrectionForce.set(
                //reduce rollCorrection gradually
                lerp(state.currRollCorrectionForce.get(), 0.0, deltaTime * 0.96)
            )
        }

        //Calculate correcting pitch force.
        val maxPitchCorrectionRate = if (state.currUpsideDownForce.get() != 0.0) 8f * state.currUpsideDownForce.get() else 15f
        val maxPitchForce = maxPitchCorrectionRate.toFloat() / 40.0f
        val pitchArrivalDeg = 90.0f
        val desiredPitch = if (state.currUpsideDownForce.get() != 0.0) 90.0 else 0.0
        if((state.rideVelocity.get().length() < 0.1 && controller.pitch < 0.0) || state.currUpsideDownForce.get() != 0.0) {
            val arrivalInfluence = (min(pitchArrivalDeg,abs(controller.pitch - desiredPitch).toFloat()) / pitchArrivalDeg)
            val desiredPitchForce = (desiredPitch - controller.pitch).sign * maxPitchCorrectionRate.toFloat() * arrivalInfluence
            var steeredPitchForce = desiredPitchForce - state.currPitchCorrectionForce.get()

            // Ensure the 'steering' correction force doesn't exceed our maxForce
            if (abs(steeredPitchForce) > maxPitchForce) {
                steeredPitchForce = steeredPitchForce.sign * maxPitchForce
            }

            // Apply the 'steered' force (its really not a steering force I need a better name)
            state.currPitchCorrectionForce.set(
                state.currPitchCorrectionForce.get() + steeredPitchForce
            )
        } else {
            state.currPitchCorrectionForce.set(
                //reduce rollCorrection gradually
                lerp(state.currPitchCorrectionForce.get(), 0.0, deltaTime * 0.98)
            )
        }
    }

    override fun rotationOnMouseXY(
        settings: BirdSettings,
        state: BirdState,
        vehicle: PokemonEntity,
        driver: Player,
        mouseY: Double,
        mouseX: Double,
        mouseYSmoother: SmoothDouble,
        mouseXSmoother: SmoothDouble,
        sensitivity: Double,
        deltaTime: Double
    ): Vec3 {
        // Begin the roll correction time counter if not enough mouse input
        // is detected
        if (abs(mouseX) < 1 && abs(mouseY) < 1) {
            state.noInputTimeRoll.set(state.noInputTimeRoll.get() + deltaTime)
        } else {
            state.noInputTimeRoll.set(0.0)
        }

        if (abs(mouseY) < 1) {
            state.noInputTimePitch.set(state.noInputTimePitch.get() + deltaTime)
        } else {
            state.noInputTimePitch.set(0.0)
        }

        return when {
            Cobblemon.config.disableRoll -> noRollRotation(
                settings,
                state,
                vehicle,
                driver,
                mouseY,
                mouseX,
                mouseYSmoother,
                mouseXSmoother,
                sensitivity,
                deltaTime
            )
            else -> rollRotation(
                settings,
                state,
                vehicle,
                driver,
                mouseY,
                mouseX,
                mouseYSmoother,
                mouseXSmoother,
                sensitivity,
                deltaTime
            )
        }
    }

    fun noRollRotation(
        settings: BirdSettings,
        state: BirdState,
        vehicle: PokemonEntity,
        driver: Player,
        mouseY: Double,
        mouseX: Double,
        mouseYSmoother: SmoothDouble,
        mouseXSmoother: SmoothDouble,
        sensitivity: Double,
        deltaTime: Double
    ): Vec3 {
        if (driver !is OrientationControllable) return Vec3.ZERO
        val controller = (driver as OrientationControllable).orientationController

        // Set roll to zero if transitioning to noroll config
        controller.rotateRoll(controller.roll * -1.0f)

        val handling = vehicle.runtime.resolveDouble(settings.handlingExpr)
        val mouseInputMult = vehicle.runtime.resolveDouble(settings.mouseInputMult)

        //Smooth out mouse input.
        val smoothingSpeed = 4.0
        val mouseXc = ((mouseX).coerceIn(-60.0, 60.0) / 60.0) * mouseInputMult
        val mouseYc = ((mouseY).coerceIn(-60.0, 60.0) / 60.0) * mouseInputMult
        val xInput = mouseXSmoother.getNewDeltaValue(mouseXc * 0.1, deltaTime * smoothingSpeed);
        val yInput = mouseYSmoother.getNewDeltaValue(mouseYc * 0.1, deltaTime * smoothingSpeed);

        //Give the ability to yaw with x mouse input when at low speeds.
        val yawForce =  xInput  * handling //* ( 1.0 - min(sqrt(RidingBehaviour.scaleToRange(state.rideVelocity.get().length(), 0.0, topSpeed)), 0.5))

        //Apply yaw globally as we don't want roll or pitch changes due to local yaw when looking up or down.
        controller.applyGlobalYaw(yawForce.toFloat() * abs(cos(controller.pitch.toRadians())).pow(2))

        var pitchRot = yInput * handling

        // Pitch up globally
        if (abs(controller.pitch + pitchRot) >= 89.5 ) {
            pitchRot = 0.0
            mouseYSmoother.reset()
        } else {
            controller.applyGlobalPitch(pitchRot.toFloat()  * -1.0f)
        }

        //yaw, pitch, roll
        return Vec3.ZERO
    }

    fun rollRotation(
        settings: BirdSettings,
        state: BirdState,
        vehicle: PokemonEntity,
        driver: Player,
        mouseY: Double,
        mouseX: Double,
        mouseYSmoother: SmoothDouble,
        mouseXSmoother: SmoothDouble,
        sensitivity: Double,
        deltaTime: Double
    ): Vec3 {
        if (driver !is OrientationControllable) return Vec3.ZERO
        val controller = (driver as OrientationControllable).orientationController

        val topSpeed = vehicle.runtime.resolveDouble(settings.speedExpr) / 20.0
        val mouseInputMult = vehicle.runtime.resolveDouble(settings.mouseInputMult)

        //Smooth out mouse input.
        val smoothingSpeed = 4.0
        val mouseXc = (mouseX).coerceIn(-45.0, 45.0) * mouseInputMult
        // When upside down apply a pitching force to nudge the player towards a dive. This reduces the 'weirdness'
        // of being upside down
        val rolledOverPitchCorrection = -8*sqrt(abs(min(cos(controller.roll.toRadians()),0.0f))) * (cos(controller.pitch.toRadians())).pow(2)
        val mouseYc = ((mouseY).coerceIn(-45.0, 45.0) * mouseInputMult) + rolledOverPitchCorrection
        val xInput = mouseXSmoother.getNewDeltaValue(mouseXc * 0.1, deltaTime * smoothingSpeed);
        val yInput = mouseYSmoother.getNewDeltaValue(mouseYc * 0.1, deltaTime * smoothingSpeed);
        val currSpeed = state.rideVelocity.get().length()
        val currHorzSpeed = state.rideVelocity.get().horizontalDistance()
        val hoverSpeed = 0.1

        // Yaw locally when at or below hoverSpeed
        val yawDampen = abs(cos(controller.pitch.toRadians())).pow(2) * abs(cos(controller.roll.toRadians())).pow(2)
        val localYaw = if (currHorzSpeed < hoverSpeed) xInput * (1 - sqrt(RidingBehaviour.scaleToRange(currHorzSpeed, hoverSpeed, topSpeed))).coerceIn(0.0, 1.0)
            else xInput * 0.4 * cos(controller.roll.toRadians()).coerceIn(0.0f, 1.0f) * yawDampen

        // Pitch locally up or down depending upon a number of factors:
        // - Reduce pitch substantially when rolled and in a steep yaw. This prevents the player from ignoring a slow handling stat
        // - Do not pitch at all when at or below hover speed
        // - pitch faster at higher speeds
        val p = 1.0 - vehicle.runtime.resolveDouble(settings.horizontalPitchExpr)
        var localPitch = yInput * (1 - abs(sin(controller.roll.toRadians())).pow(2) * p * hypot(controller.forwardVector.x.toDouble(), controller.forwardVector.z.toDouble()).toFloat())
        localPitch *= if (currSpeed > hoverSpeed || yInput > 0.0) RidingBehaviour.scaleToRange(currSpeed, 0.0, topSpeed).coerceIn(0.2, 1.0)
            else 0.0

        // Roll less when slow and not at all when at hoverSpeed or lower.
        // Apply a roll righting force when trying to pitch hard while rolled sideways. This nudges the player towards
        // pitching globally
        val pitchInfluencedRollCorrection = 0.0
        val rollForce = if (currHorzSpeed > hoverSpeed) xInput * sqrt(abs(RidingBehaviour.scaleToRange(currHorzSpeed, hoverSpeed, topSpeed)).coerceIn(0.0,1.0)) + pitchInfluencedRollCorrection
            else 0.0

        //yaw, pitch, roll
        return Vec3(localYaw, localPitch, rollForce)
    }


    override fun canJump(
        settings: BirdSettings,
        state: BirdState,
        vehicle: PokemonEntity,
        driver: Player
    ): Boolean {
        return false
    }

    override fun setRideBar(
        settings: BirdSettings,
        state: BirdState,
        vehicle: PokemonEntity,
        driver: Player
    ): Float {
        return (state.stamina.get() / 1.0f)
    }

    override fun jumpForce(
        settings: BirdSettings,
        state: BirdState,
        vehicle: PokemonEntity,
        driver: Player,
        jumpStrength: Int
    ): Vec3 {
        return Vec3.ZERO
    }

    override fun gravity(
        settings: BirdSettings,
        state: BirdState,
        vehicle: PokemonEntity,
        regularGravity: Double
    ): Double {
        return 0.0
    }

    override fun rideFovMultiplier(
        settings: BirdSettings,
        state: BirdState,
        vehicle: PokemonEntity,
        driver: Player
    ): Float {
        val topSpeed = vehicle.runtime.resolveDouble(settings.speedExpr) / 20.0
        val glideTopSpeed = topSpeed * vehicle.runtime.resolveDouble(settings.glidespeedExpr)
        val currSpeed = state.rideVelocity.get().length()

        // Must I ensure that topspeed is greater than minimum?
        val normalizedGlideSpeed = RidingBehaviour.scaleToRange(currSpeed, topSpeed, glideTopSpeed)

        // Only ever want the fov change to be a max of 0.2 and for it to have non linear scaling.
        return 1.0f + normalizedGlideSpeed.pow(2).toFloat() * 0.2f
    }

    override fun useAngVelSmoothing(
        settings: BirdSettings,
        state: BirdState,
        vehicle: PokemonEntity
    ): Boolean {
        return false
    }

    override fun useRidingAltPose(
        settings: BirdSettings,
        state: BirdState,
        vehicle: PokemonEntity,
        driver: Player
    ): ResourceLocation {
        if (state.gliding.get()) {
            return cobblemonResource("gliding")
        }
        return cobblemonResource("no_pose")
    }

    override fun inertia(settings: BirdSettings, state: BirdState, vehicle: PokemonEntity): Double {
        return 0.5
    }

    override fun shouldRoll(settings: BirdSettings, state: BirdState, vehicle: PokemonEntity): Boolean {
        return true
    }

    override fun turnOffOnGround(settings: BirdSettings, state: BirdState, vehicle: PokemonEntity): Boolean {
        return false
    }

    override fun dismountOnShift(settings: BirdSettings, state: BirdState, vehicle: PokemonEntity): Boolean {
        return false
    }

    override fun shouldRotatePokemonHead(
        settings: BirdSettings,
        state: BirdState,
        vehicle: PokemonEntity
    ): Boolean {
        return false
    }

    override fun shouldRotateRiderHead(
        settings: BirdSettings,
        state: BirdState,
        vehicle: PokemonEntity
    ): Boolean {
        return false
    }

    override fun damageOnCollision(
        settings: BirdSettings,
        state: BirdState,
        vehicle: PokemonEntity,
        impactVec: Vec3
    ): Boolean {
        if (!state.gliding.get()) return false
        val impactSpeed = impactVec.horizontalDistance().toFloat() * 10f
        return vehicle.causeFallDamage(impactSpeed, 1f, vehicle.damageSources().flyIntoWall())
    }

    override fun getRideSounds(
        settings: BirdSettings,
        state: BirdState,
        vehicle: PokemonEntity
    ): RideSoundSettingsList {
        return settings.rideSounds
    }

    override fun createDefaultState(settings: BirdSettings) = BirdState()
}

class BirdSettings : RidingBehaviourSettings {
    override val key = BirdBehaviour.KEY
    override val stats = mutableMapOf<RidingStat, IntRange>()

    var infiniteAltitude: Expression = "false".asExpression()
        private set

    var infiniteStamina: Expression = "false".asExpression()
        private set

    var timeToRollCorrect: Expression = "0.5".asExpression()
        private set

    var handlingExpr: Expression = "q.get_ride_stats('SKILL', 'AIR', 45.0, 10.0)".asExpression()
    var horizontalPitchExpr: Expression = "q.get_ride_stats('SKILL', 'AIR', 0.2, 0.1)".asExpression()
    var mouseInputMult: Expression = "q.get_ride_stats('SKILL', 'AIR', 1.2, 0.8)".asExpression()
    var speedExpr: Expression = "q.get_ride_stats('SPEED', 'AIR', 20.0, 4.0)".asExpression()
    // Seconds from stationary to top speed
    var accelerationExpr: Expression = "q.get_ride_stats('ACCELERATION', 'AIR', 2.0, 8.0)".asExpression()
    var staminaExpr: Expression = "q.get_ride_stats('STAMINA', 'AIR', 60.0, 15.0)".asExpression()

    var glidespeedExpr: Expression =  "q.get_ride_stats('JUMP', 'AIR', 2.0, 1.0)".asExpression()
        private set

    var rideSounds: RideSoundSettingsList = RideSoundSettingsList()

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeResourceLocation(key)
        buffer.writeRidingStats(stats)
        rideSounds.encode(buffer)
        buffer.writeExpression(infiniteAltitude)
        buffer.writeExpression(infiniteStamina)
        buffer.writeExpression(glidespeedExpr)
        buffer.writeExpression(handlingExpr)
        buffer.writeExpression(horizontalPitchExpr)
        buffer.writeExpression(mouseInputMult)
        buffer.writeExpression(speedExpr)
        buffer.writeExpression(accelerationExpr)
        buffer.writeExpression(staminaExpr)
        buffer.writeExpression(timeToRollCorrect)
    }

    override fun decode(buffer: RegistryFriendlyByteBuf) {
        stats.putAll(buffer.readRidingStats())
        rideSounds = RideSoundSettingsList.decode(buffer)
        infiniteAltitude = buffer.readExpression()
        infiniteStamina = buffer.readExpression()
        glidespeedExpr = buffer.readExpression()
        handlingExpr = buffer.readExpression()
        horizontalPitchExpr = buffer.readExpression()
        mouseInputMult = buffer.readExpression()
        speedExpr = buffer.readExpression()
        accelerationExpr = buffer.readExpression()
        staminaExpr = buffer.readExpression()
        timeToRollCorrect = buffer.readExpression()
    }
}

class BirdState : RidingBehaviourState() {
    var gliding = ridingState(false, Side.BOTH)
    var lastGlide = ridingState(-100L, Side.CLIENT)
    var currRollCorrectionForce = ridingState(0.0, Side.CLIENT)
    var currPitchCorrectionForce = ridingState(0.0, Side.CLIENT)
    var noInputTimeRoll = ridingState(0.0, Side.CLIENT)
    var noInputTimePitch = ridingState(0.0, Side.CLIENT)
    var currUpsideDownForce = ridingState(0.0, Side.CLIENT)

    override fun encode(buffer: FriendlyByteBuf) {
        super.encode(buffer)
        buffer.writeBoolean(gliding.get())
    }

    override fun decode(buffer: FriendlyByteBuf) {
        super.decode(buffer)
        gliding.set(buffer.readBoolean(), forced = true)
    }

    override fun reset() {
        super.reset()
        gliding.set(false, forced = true)
        lastGlide.set(-100L, forced = true)
        currRollCorrectionForce.set(0.0, forced = true)
        currPitchCorrectionForce.set(0.0, forced = true)
        noInputTimeRoll.set(0.0, forced = true)
        noInputTimePitch.set(0.0, forced = true)
    }

    override fun toString(): String {
        return "BirdState(rideVelocity=${rideVelocity.get()}, stamina=${stamina.get()}, gliding=${gliding.get()})"
    }

    override fun copy() = BirdState().also {
        it.rideVelocity.set(this.rideVelocity.get(), forced = true)
        it.stamina.set(this.stamina.get(), forced = true)
        it.gliding.set(this.gliding.get(), forced = true)
        it.lastGlide.set(this.lastGlide.get(), forced = true)
        it.currRollCorrectionForce.set(this.currRollCorrectionForce.get(), forced = true)
        it.currPitchCorrectionForce.set(this.currPitchCorrectionForce.get(), forced = true)
        it.noInputTimeRoll.set(this.noInputTimeRoll.get(), forced = true)
        it.noInputTimePitch.set(this.noInputTimePitch.get(), forced = true)
    }

    override fun shouldSync(previous: RidingBehaviourState): Boolean {
        if (previous !is BirdState) return false
        if (previous.gliding.get() != gliding.get()) return true
        return super.shouldSync(previous)
    }
}
