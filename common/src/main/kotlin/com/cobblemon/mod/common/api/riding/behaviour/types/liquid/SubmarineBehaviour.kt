/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.riding.behaviour.types.liquid

import com.bedrockk.molang.Expression
import com.bedrockk.molang.runtime.MoLangMath.lerp
import com.cobblemon.mod.common.CobblemonRideSettings
import com.cobblemon.mod.common.OrientationControllable
import com.cobblemon.mod.common.api.orientation.OrientationController
import com.cobblemon.mod.common.api.riding.RidingStyle
import com.cobblemon.mod.common.api.riding.behaviour.RidingBehaviour
import com.cobblemon.mod.common.api.riding.behaviour.RidingBehaviourSettings
import com.cobblemon.mod.common.api.riding.behaviour.RidingBehaviourState
import com.cobblemon.mod.common.api.riding.behaviour.Side
import com.cobblemon.mod.common.api.riding.behaviour.ridingState
import com.cobblemon.mod.common.api.riding.posing.PoseOption
import com.cobblemon.mod.common.api.riding.posing.PoseProvider
import com.cobblemon.mod.common.api.riding.sound.RideSoundSettingsList
import com.cobblemon.mod.common.api.riding.stats.RidingStat
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.math.geometry.toRadians
import com.cobblemon.mod.common.util.readNullableExpression
import com.cobblemon.mod.common.util.readRidingStats
import com.cobblemon.mod.common.util.resolveBoolean
import com.cobblemon.mod.common.util.resolveDouble
import com.cobblemon.mod.common.util.toVec3d
import com.cobblemon.mod.common.util.writeNullableExpression
import com.cobblemon.mod.common.util.writeRidingStats
import kotlin.math.*
import net.minecraft.client.Minecraft
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.FluidTags
import net.minecraft.util.SmoothDouble
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f

class SubmarineBehaviour : RidingBehaviour<SubmarineSettings, SubmarineState> {
    companion object {
        val KEY = cobblemonResource("liquid/submarine")
    }

    override val key = KEY
    val globalSubmarine: SubmarineSettings
        get() = CobblemonRideSettings.submarine

    override fun getRidingStyle(settings: SubmarineSettings, state: SubmarineState): RidingStyle {
        return RidingStyle.LIQUID
    }

    val poseProvider = PoseProvider<SubmarineSettings, SubmarineState>(PoseType.FLOAT)
        .with(PoseOption(PoseType.SWIM) { _, state, entity -> abs(state.rideVelocity.get().z) > 0.05 })

    override fun isActive(settings: SubmarineSettings, state: SubmarineState, vehicle: PokemonEntity): Boolean {
        return vehicle.isInWater || !vehicle.onGround()
    }

    override fun pose(settings: SubmarineSettings, state: SubmarineState, vehicle: PokemonEntity): PoseType {
        return poseProvider.select(settings, state, vehicle)
    }

    override fun speed(settings: SubmarineSettings, state: SubmarineState, vehicle: PokemonEntity, driver: Player): Float {
        return state.speed.get().toFloat()
    }

    override fun tick(
        settings: SubmarineSettings,
        state: SubmarineState,
        vehicle: PokemonEntity,
        driver: Player,
        input: Vec3
    ) {
        if(vehicle.level().isClientSide) {
            tickStamina(settings, state, vehicle)
        }
    }

    fun tickStamina(
        settings: SubmarineSettings,
        state: SubmarineState,
        vehicle: PokemonEntity
    ) {
        val stam = state.stamina.get()

        if (vehicle.runtime.resolveBoolean(settings.infiniteStamina ?: globalSubmarine.infiniteStamina!!)) {
            return
        }

        // Grab the boost time in seconds and convert to ticks. Then calculate the drain rate as inversely
        // proportional to the number of ticks of boost thus making a full boost take x ticks
        // in short: "Stamina drains slower at higher values and also replenishes slower"
        val breathTime = vehicle.runtime.resolveDouble(settings.staminaExpr ?: globalSubmarine.staminaExpr!!) * 20.0f
        val stamDrainRate = (1.0f / breathTime).toFloat()

        val newStam = if (vehicle.isInWater || vehicle.isUnderWater) max(0.0f,stam - stamDrainRate)
            else min(1.0f,stam + stamDrainRate) // TODO: Make this consistent across all rides.

        state.stamina.set(newStam)
    }

    override fun rotation(
        settings: SubmarineSettings,
        state: SubmarineState,
        vehicle: PokemonEntity,
        driver: LivingEntity
    ): Vec2 {
        return Vec2(driver.xRot * 0.5f, driver.yRot)
    }

    override fun velocity(
        settings: SubmarineSettings,
        state: SubmarineState,
        vehicle: PokemonEntity,
        driver: Player,
        input: Vec3
    ): Vec3 {
        if (vehicle !is OrientationControllable) return Vec3.ZERO
        val controller = (vehicle as OrientationControllable).orientationController
        val orientation = controller.orientation ?: return Vec3.ZERO

        val runtime = vehicle.runtime
        val topSpeed = (vehicle.runtime.resolveDouble(settings.speedExpr ?: globalSubmarine.speedExpr!!) / 20.0)
        val accel = (topSpeed / (vehicle.runtime.resolveDouble(settings.accelerationExpr ?: globalSubmarine.accelerationExpr!!) * 20.0))
        val strafeFactor = runtime.resolveDouble(settings.strafeFactor ?: globalSubmarine.strafeFactor!!)
        val reverseDriveFactor = runtime.resolveDouble(settings.reverseDriveFactor ?: globalSubmarine.reverseDriveFactor!!)
        var currVel = state.rideVelocity.get()


        /*********************************************
         * Handle collisions
         *********************************************/
        if (vehicle.horizontalCollision || vehicle.verticalCollision) {
            // not actually friction
            val frictionLimit = 0.1
            val postColSpeed = min(max(state.rideVelocity.get().length(), frictionLimit), state.speed.get())
            state.speed.set(postColSpeed)
        }

        /*********************************************
         * Calculate Forward Velocity
         *********************************************/
        val currZVel = state.rideVelocity.get().z
        val newZVel = when {
            driver.zza > 0.0 -> min(topSpeed, currZVel + accel)
            driver.zza < 0.0 -> max( -topSpeed * reverseDriveFactor, currZVel - accel)
            else -> lerp(currZVel, 0.0, 0.05)
        }
        currVel = Vec3(currVel.x, currVel.y, newZVel)
//        state.rideVelocity.set(Vec3(
//            lerp(min(currVel.x, entryMaxVel), 0.0, 0.2), // Slow both of these in case of transitions from other mounts.
//            lerp(min(currVel.y, entryMaxVel), 0.0, 0.2),
//            newZVel
//        ))

        /*********************************************
         * Calculate up/down Velocity and gravity
         *********************************************/
        val vertTopSpeed = 0.25 //TODO: move to jump stat
        val currYVel = state.rideVelocity.get().y
        val gravity = (9.8 / ( 20.0)) * 0.2 * 0.4
        val terminalYVel = 2.0
        val newYVel = when {
            (!vehicle.isInWater && !vehicle.isUnderWater) -> max(-terminalYVel, currYVel - gravity)
            driver.jumping -> min(vertTopSpeed, currYVel + accel)
            driver.isShiftKeyDown -> max(-vertTopSpeed, currYVel - accel)
            else -> lerp(currYVel, 0.0, 0.05)
        }
        currVel = Vec3(currVel.x, newYVel, currVel.z)

        /*********************************************
         * Calculate left/right strafing Velocity
         *********************************************/
        val currXVel = state.rideVelocity.get().x
        val horzTopSpeed = 0.25 //TODO: move to jump stat
        val newXVel = when {
            driver.xxa > 0.0 -> min(horzTopSpeed, currXVel + accel)
            driver.xxa < 0.0 -> max( -horzTopSpeed, currXVel - accel)
            else -> lerp(currXVel, 0.0, 0.05)
        }
        currVel = Vec3(newXVel, currVel.y, currVel.z)

        // Set the new velocity
        state.rideVelocity.set(currVel)

        /*********************************************
         * Separate and apply  velocities
         *********************************************/
        // If the driver and vehicles eyes are out of the water but the vehicle is still in it then move only horizontally or downwards as you have surfaced
        if (!driver.isEyeInFluid(FluidTags.WATER) && !vehicle.isEyeInFluid(FluidTags.WATER) && vehicle.isInWater) {
            val worldVelVec = Vec3(currVel.x, min(0.0, currVel.y), currVel.z)
            return worldVelVec
        } else {
            val vertVec = Vec3(0.0, currVel.y, 0.0)
            val horzVec = Vec3(currVel.x, 0.0, 0.0)
            val localZVelVec = Vector3f(0.0f, 0.0f, currVel.z.toFloat() * -1.0f) // Flip the z axis to make this left handed
            // Unrotate preemptively as this vector gets rotated later down the line in MC logic.
            val worldVelVec = localZVelVec.mul(orientation).toVec3d().yRot(vehicle.yRot.toRadians())
            // Add back the horizontal and vertical components.
            return worldVelVec.add(vertVec).add(horzVec)
        }
    }


    // TODO: determine if not needed
    override fun angRollVel(
        settings: SubmarineSettings,
        state: SubmarineState,
        vehicle: PokemonEntity,
        driver: Player,
        deltaTime: Double
    ): Vec3 {
        //if (Cobblemon.config.disableRoll) return Vec3.ZERO
        if (vehicle !is OrientationControllable) return Vec3.ZERO
        val controller = (vehicle as OrientationControllable).orientationController

        // Grab the roll and see if its hit the limit yet. If it hasn't then trend it towards its desired roll
        val currRoll = controller.roll
        val maxHorzSpeed = 0.25 //TODO: CONNECT UP TO JUMP
        val maxRollDeg = 15 //in degrees
        val currHorzSpeed = state.rideVelocity.get().x // TODO: make function that calls to here with partialtick from the riding animation
        val desiredRoll = -(currHorzSpeed/maxHorzSpeed) * maxRollDeg
        val rotAmount = lerp(currRoll.toDouble(), desiredRoll, 0.5) - currRoll
        //val rotAmount   //Maybe use some lerp?
        controller.rotateRoll(rotAmount.toFloat())
//
//        val currSpeed = vehicle.deltaMovement.length()
//
//        val handling = vehicle.runtime.resolveDouble(settings.handlingExpr ?: globalSubmarine.handlingExpr!!)
//        val topSpeed = vehicle.runtime.resolveDouble(settings.speedExpr ?: globalSubmarine.speedExpr!!) / 20.0
//
//        val yawDeltaDeg =  deltaTime * handling * sin(Math.toRadians(controller.roll.toDouble())).pow(2) * sin(Math.toRadians(controller.roll.toDouble())).sign
//        val trueYawDelt = yawDeltaDeg * abs(cos(Math.toRadians(controller.pitch.toDouble()))*2).coerceIn(-1.0, 1.0) * sqrt(RidingBehaviour.scaleToRange(currSpeed, 0.0, topSpeed))
//
//        // Dampen yaw when upside down
//        val yawDampen = (1 - abs(min(cos(controller.roll.toRadians()),0.0f)))
//        controller.applyGlobalYaw(trueYawDelt.toFloat() * yawDampen)
//
//        // Correct orientation when at low speeds
//        //correctOrientation(settings, state, vehicle, controller, driver, deltaTime)
//
//        //yaw, pitch, roll
//        return Vec3(0.0, state.currPitchCorrectionForce.get(), state.currRollCorrectionForce.get())
        return Vec3.ZERO
    }

    private fun correctOrientation(
        settings: SubmarineSettings,
        state: SubmarineState,
        vehicle: PokemonEntity,
        controller: OrientationController,
        driver: Player,
        deltaTime: Double
    ) {

// TODO: remove if not needed

//        // Calculate correcting roll force.
//        // If the ride is moving then have the correction rate be an equal proportion of:
//        // - how rolled are you
//        // - how long has it been since last mouse input (higher is stronger correction)
//        // Also the correction is proportional as a whole to how NOT pitched are you so you don't spin endlessly when looking up or down
//        val howRolledAmI =  sqrt(abs((cos(controller.roll.toRadians()) - 1.0f) * 0.5f)) // sqrt so it trends towards one pretty quickly
//        val maxRollCorrectionRate =
//            if (state.rideVelocity.get().length() < 0.2) 10.0f * abs(cos(controller.pitch.toRadians()))
//            else 10.0f * cos(controller.pitch.toRadians()).pow(2) * (
//                    howRolledAmI*1.0f +
//                            (state.noInputTime.get() - rollCorrectionTimer).coerceIn(0.0, 1.0).pow(2).toFloat()*0.5f
//                    )
//        val maxRollForce = maxRollCorrectionRate / 40.0f
//        val rollArrivalDeg = 60.0f
//        val desiredRoll = 0.0f
//
//        // Calculate the signed error (how far and in what direction)
//        val rollError = Mth.wrapDegrees(desiredRoll.angleDifference(controller.roll))
//
//        // Use the absolute error to     calculate the magnitude of the influence
//        val influenceMagnitude = (min(rollArrivalDeg, abs(rollError)) / rollArrivalDeg)
//        val desiredRollForce = maxRollCorrectionRate * influenceMagnitude * -rollError.sign
//        var steeredRollForce = desiredRollForce - state.currRollCorrectionForce.get()
//
//        // Ensure the 'steering' correction force doesn't exceed our maxForce
//        if (abs(steeredRollForce) > maxRollForce) {
//            steeredRollForce = steeredRollForce.sign * maxRollForce
//        }
//
//        // Apply the 'steered' force (its really not a steering force I need a better name)
//        state.currRollCorrectionForce.set(
//            state.currRollCorrectionForce.get() + steeredRollForce
//        )

        //Calculate correcting pitch force.
//        val rollCorrectionTimer = vehicle.runtime.resolveDouble(settings.timeToRollCorrect ?: globalSubmarine.timeToRollCorrect!!)
//        val maxPitchCorrectionRate = 5.0f * (state.noInputTime.get() - rollCorrectionTimer).coerceIn(0.0, 1.0).pow(2).toFloat()*0.5f
//        val maxPitchForce = maxPitchCorrectionRate / 40.0f
//        val pitchArrivalDeg = 45.0f
//        val desiredPitch = 0.0f
//
//        if(state.rideVelocity.get().length() < 0.1 && state.noInputTime.get() > rollCorrectionTimer) {
//            val pitchErrror = Mth.wrapDegrees(desiredPitch.angleDifference(controller.pitch))
//            val arrivalInfluence = (min(pitchArrivalDeg,abs(pitchErrror)) / pitchArrivalDeg)
//            val desiredPitchForce = maxPitchCorrectionRate * arrivalInfluence * -pitchErrror.sign
//            var steeredPitchForce = desiredPitchForce - state.currPitchCorrectionForce.get()
//
//            // Ensure the 'steering' correction force doesn't exceed our maxForce
//            if (abs(steeredPitchForce) > maxPitchForce) {
//                steeredPitchForce = steeredPitchForce.sign * maxPitchForce
//            }
//
//            // Apply the 'steered' force (its really not a steering force I need a better name)
//            state.currPitchCorrectionForce.set(
//                state.currPitchCorrectionForce.get() + steeredPitchForce
//            )
//        } else {
//            state.currPitchCorrectionForce.set(
//                //reduce rollCorrection gradually
//                lerp(state.currPitchCorrectionForce.get(), 0.0, deltaTime * 0.98)
//            )
//        }
    }

    override fun rotationOnMouseXY(
        settings: SubmarineSettings,
        state: SubmarineState,
        vehicle: PokemonEntity,
        driver: Player,
        mouseY: Double,
        mouseX: Double,
        mouseYSmoother: SmoothDouble,
        mouseXSmoother: SmoothDouble,
        sensitivity: Double,
        deltaTime: Double
    ): Vec3 {
        if (vehicle !is OrientationControllable) return Vec3.ZERO
        val controller = (vehicle as OrientationControllable).orientationController

        // Set roll to zero if transitioning to noroll config
        controller.rotateRoll(controller.roll * -1.0f)

        val handling = vehicle.runtime.resolveDouble(settings.handlingExpr ?: globalSubmarine.handlingExpr!!)
        val effHandling = handling * deltaTime
        //Smooth out mouse input.
        val smoothingSpeed = 2.0
        val mouseXc = (mouseX * 0.1).coerceIn(-effHandling, effHandling)
        val mouseYc = (mouseY * 0.1).coerceIn(-effHandling, effHandling)
        val xInput = mouseXSmoother.getNewDeltaValue(mouseXc , deltaTime * smoothingSpeed);
        val yInput = mouseYSmoother.getNewDeltaValue(mouseYc , deltaTime * smoothingSpeed);

        //Give the ability to yaw with x mouse input when at low speeds.
        val yawForce =  xInput

        //Apply yaw globally as we don't want roll or pitch changes due to local yaw when looking up or down.
        controller.applyGlobalYaw(yawForce.toFloat())

        var pitchRot = yInput

        // Pitch up globally

        val pitchLimit = 45.0
        if (abs(controller.pitch + pitchRot) >= pitchLimit ) {
            pitchRot = 0.0
            mouseYSmoother.reset()
        } else {
            controller.applyGlobalPitch(pitchRot.toFloat())
        }

        //yaw, pitch, roll
        return Vec3.ZERO
    }

    override fun canJump(
        settings: SubmarineSettings,
        state: SubmarineState,
        vehicle: PokemonEntity,
        driver: Player
    ): Boolean {
        return false
    }

    override fun setRideBar(
        settings: SubmarineSettings,
        state: SubmarineState,
        vehicle: PokemonEntity,
        driver: Player
    ): Float {
        return (state.stamina.get() / 1.0f)
    }

    override fun jumpForce(
        settings: SubmarineSettings,
        state: SubmarineState,
        vehicle: PokemonEntity,
        driver: Player,
        jumpStrength: Int
    ): Vec3 {
        return Vec3.ZERO
    }

    override fun gravity(
        settings: SubmarineSettings,
        state: SubmarineState,
        vehicle: PokemonEntity,
        regularGravity: Double
    ): Double {
        return 0.0
    }

    override fun rideFovMultiplier(
        settings: SubmarineSettings,
        state: SubmarineState,
        vehicle: PokemonEntity,
        driver: Player
    ): Float {
        return 1.0f
    }

    override fun useAngVelSmoothing(settings: SubmarineSettings, state: SubmarineState, vehicle: PokemonEntity): Boolean {
        return false
    }

    override fun useRidingAltPose(
        settings: SubmarineSettings,
        state: SubmarineState,
        vehicle: PokemonEntity,
        driver: Player
    ): ResourceLocation {
        return cobblemonResource("no_pose")
    }

    override fun inertia(settings: SubmarineSettings, state: SubmarineState, vehicle: PokemonEntity): Double {
        return if (!vehicle.isInWater && !vehicle.isUnderWater) 1.0 else 0.1
    }

    override fun shouldRoll(settings: SubmarineSettings, state: SubmarineState, vehicle: PokemonEntity): Boolean {
        return true
    }

    override fun turnOffOnGround(settings: SubmarineSettings, state: SubmarineState, vehicle: PokemonEntity): Boolean {
        return false
    }

    override fun dismountOnShift(settings: SubmarineSettings, state: SubmarineState, vehicle: PokemonEntity): Boolean {
        return false
    }

    override fun shouldRotatePokemonHead(
        settings: SubmarineSettings,
        state: SubmarineState,
        vehicle: PokemonEntity
    ): Boolean {
        return false
    }

    override fun shouldRotateRiderHead(
        settings: SubmarineSettings,
        state: SubmarineState,
        vehicle: PokemonEntity
    ): Boolean {
        return false
    }

    override fun getRideSounds(
        settings: SubmarineSettings,
        state: SubmarineState,
        vehicle: PokemonEntity
    ): RideSoundSettingsList {
        return settings.rideSounds
    }

    override fun createDefaultState(settings: SubmarineSettings) = SubmarineState()

}

class SubmarineSettings : RidingBehaviourSettings {
    override val key = SubmarineBehaviour.KEY
    override val stats = mutableMapOf<RidingStat, IntRange>()

    var infiniteStamina: Expression? = null
        private set

    var reverseDriveFactor: Expression? = null
        private set

    var strafeFactor: Expression? = null
        private set

    // Boost influence when boosting
    var jumpExpr: Expression? = null
    // Yaw rate in degrees per second
    var handlingExpr: Expression? = null
    // Top speed in Bl/s
    var speedExpr: Expression? = null
    // Seconds to get to top speed
    var accelerationExpr: Expression? = null
    // Seconds of boost
    var staminaExpr: Expression? = null

    var rideSounds: RideSoundSettingsList = RideSoundSettingsList()

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeRidingStats(stats)
        rideSounds.encode(buffer)
        buffer.writeNullableExpression(infiniteStamina)
        buffer.writeNullableExpression(reverseDriveFactor)
        buffer.writeNullableExpression(strafeFactor)
        buffer.writeNullableExpression(jumpExpr)
        buffer.writeNullableExpression(handlingExpr)
        buffer.writeNullableExpression(speedExpr)
        buffer.writeNullableExpression(accelerationExpr)
        buffer.writeNullableExpression(staminaExpr)
    }

    override fun decode(buffer: RegistryFriendlyByteBuf) {
        stats.putAll(buffer.readRidingStats())
        rideSounds = RideSoundSettingsList.decode(buffer)
        infiniteStamina = buffer.readNullableExpression()
        reverseDriveFactor = buffer.readNullableExpression()
        strafeFactor = buffer.readNullableExpression()
        jumpExpr = buffer.readNullableExpression()
        handlingExpr = buffer.readNullableExpression()
        speedExpr = buffer.readNullableExpression()
        accelerationExpr = buffer.readNullableExpression()
        staminaExpr = buffer.readNullableExpression()
    }
}

class SubmarineState : RidingBehaviourState() {
    var speed = ridingState(0.0, Side.CLIENT)
    var currRollCorrectionForce = ridingState(0.0, Side.CLIENT)
    var currPitchCorrectionForce = ridingState(0.0, Side.CLIENT)
    var noInputTime = ridingState(0.0, Side.CLIENT)

    override fun encode(buffer: FriendlyByteBuf) {
        super.encode(buffer)
    }

    override fun decode(buffer: FriendlyByteBuf) {
        super.decode(buffer)
    }

    override fun reset() {
        super.reset()
        speed.set(0.0, forced = true)
        currRollCorrectionForce.set(0.0, forced = true)
        currPitchCorrectionForce.set(0.0, forced = true)
        noInputTime.set(0.0, forced = true)
    }

    override fun copy() = SubmarineState().also {
        it.stamina.set(stamina.get(), forced = true)
        it.speed.set(speed.get(), forced = true)
        it.rideVelocity.set(rideVelocity.get(), forced = true)
        it.currRollCorrectionForce.set(this.currRollCorrectionForce.get(), forced = true)
        it.currPitchCorrectionForce.set(this.currPitchCorrectionForce.get(), forced = true)
        it.noInputTime.set(this.noInputTime.get(), forced = true) }

    override fun shouldSync(previous: RidingBehaviourState): Boolean {
        if (previous !is SubmarineState) return false
        return super.shouldSync(previous)
    }
}
