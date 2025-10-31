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
import com.cobblemon.mod.common.Cobblemon
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
import com.cobblemon.mod.common.util.math.geometry.toDegrees
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
import net.minecraft.util.Mth
import net.minecraft.util.SmoothDouble
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3

class DolphinBehaviour : RidingBehaviour<DolphinSettings, DolphinState> {
    companion object {
        val KEY = cobblemonResource("liquid/dolphin")
    }

    override val key = KEY
    val globalDolphin: DolphinSettings
        get() = CobblemonRideSettings.dolphin

    override fun getRidingStyle(settings: DolphinSettings, state: DolphinState): RidingStyle {
        return RidingStyle.LIQUID
    }

    val poseProvider = PoseProvider<DolphinSettings, DolphinState>(PoseType.FLOAT)
        .with(PoseOption(PoseType.SWIM) { _, _, entity -> entity.deltaMovement.length() > 0.1 })

    override fun isActive(settings: DolphinSettings, state: DolphinState, vehicle: PokemonEntity): Boolean {
        return vehicle.isInWater || !vehicle.onGround()
    }

    override fun pose(settings: DolphinSettings, state: DolphinState, vehicle: PokemonEntity): PoseType {
        return poseProvider.select(settings, state, vehicle)
    }

    override fun speed(settings: DolphinSettings, state: DolphinState, vehicle: PokemonEntity, driver: Player): Float {
        return state.speed.get().toFloat()
    }

    override fun tick(
        settings: DolphinSettings,
        state: DolphinState,
        vehicle: PokemonEntity,
        driver: Player,
        input: Vec3
    ) {
        if(vehicle.level().isClientSide) {
            handleBoosting(state, vehicle)
            tickStamina(settings, state, vehicle)
        }
    }

    fun handleBoosting(
        state: DolphinState,
        vehicle: PokemonEntity
    ) {
        //If the forward key is not held then it cannot be boosting
        if(Minecraft.getInstance().options.keyUp.isDown() && state.stamina.get() != 0.0f && (vehicle.isInWater || vehicle.isUnderWater)) {
            val boostKeyPressed = Minecraft.getInstance().options.keySprint.isDown()
            if (state.stamina.get() >= 0.25f) {
                //If on the previous tick the boost key was held then don't change if the ride is boosting
                if(state.boostIsToggleable.get() && boostKeyPressed) {
                    //flip the boosting state if boost key is pressed
                    state.boosting.set(!state.boosting.get())
                }
                //If the boost key is not held then next tick boosting is toggleable
                state.boostIsToggleable.set(!boostKeyPressed)
            }
        } else {
            //Turn off boost and reset boost params
            state.boostIsToggleable.set(true)
            state.boosting.set(false)
        }
    }

    fun tickStamina(
        settings: DolphinSettings,
        state: DolphinState,
        vehicle: PokemonEntity
    ) {
        val stam = state.stamina.get()

        if (vehicle.runtime.resolveBoolean(settings.infiniteStamina ?: globalDolphin.infiniteStamina!!)) {
            return
        }

        // Grab the boost time in seconds and convert to ticks. Then calculate the drain rate as inversely
        // proportional to the number of ticks of boost thus making a full boost take x ticks
        // in short: "Stamina drains slower at higher values and also replenishes slower"
        val boostTime = vehicle.runtime.resolveDouble(settings.staminaExpr ?: globalDolphin.staminaExpr!!) * 20.0f
        val stamDrainRate = (1.0f / boostTime).toFloat()

        val newStam = if (state.boosting.get()) max(0.0f,stam - stamDrainRate)
            else min(1.0f,stam + stamDrainRate)

        state.stamina.set(newStam)
    }

    override fun rotation(
        settings: DolphinSettings,
        state: DolphinState,
        vehicle: PokemonEntity,
        driver: LivingEntity
    ): Vec2 {
        return Vec2(driver.xRot * 0.5f, driver.yRot)
    }

    override fun velocity(
        settings: DolphinSettings,
        state: DolphinState,
        vehicle: PokemonEntity,
        driver: Player,
        input: Vec3
    ): Vec3 {
        if (driver !is OrientationControllable) return Vec3.ZERO
        val controller = (driver as OrientationControllable).orientationController

        val runtime = vehicle.runtime
        val boostMod = if (state.boosting.get()) runtime.resolveDouble(settings.jumpExpr ?: globalDolphin.jumpExpr!!) else 1.0
        val topSpeed = (vehicle.runtime.resolveDouble(settings.speedExpr ?: globalDolphin.speedExpr!!) / 20.0) * boostMod
        val accel = (topSpeed / (vehicle.runtime.resolveDouble(settings.accelerationExpr ?: globalDolphin.accelerationExpr!!) * 20.0)) * boostMod

        /*********************************************
         * Breaching logic
         *********************************************/
        if (!vehicle.isInWater && !vehicle.isUnderWater)
        {
            val currVel = state.lastVelocity.get()
            val breachBoost = 1.5
            if (!state.hasBreached.get()) {
                // Apply an upwards breaching force if boosting. This will enhance jumping out of water to feel more
                // fun
                if (state.boosting.get()) {
                    state.lastVelocity.set(Vec3(currVel.x, currVel.y + breachBoost*sin(-controller.pitch.toRadians()).coerceIn(0.0f,0.5f), currVel.z))
                }
                state.hasBreached.set(true)
            } else {
                val gravity = (9.8 / ( 20.0)) * 0.2 * 0.4
                val terminalVel = 2.0
                state.lastVelocity.set(Vec3(currVel.x, max(-terminalVel, currVel.y - gravity), currVel.z)) //.yRot(vehicle.yRot.toRadians()))
            }
            return state.lastVelocity.get()
        } else {
            state.lastVelocity.set(vehicle.deltaMovement.yRot(vehicle.yRot.toRadians()))
            state.hasBreached.set(false)
        }

        /*********************************************
         * Handle collisions
         *********************************************/
        if (vehicle.horizontalCollision || vehicle.verticalCollision) {
            // not actually friction
            val frictionLimit = 0.1
            val postColSpeed = min(max(vehicle.deltaMovement.length(), frictionLimit), state.speed.get())
            state.speed.set(postColSpeed)
        }

        val currSpeed = state.speed.get()
        val strafeFactor = runtime.resolveDouble(settings.strafeFactor ?: globalDolphin.strafeFactor!!)
        val reverseDriveFactor = runtime.resolveDouble(settings.reverseDriveFactor ?: globalDolphin.reverseDriveFactor!!)
        val f = -driver.xxa * strafeFactor
        when {
            driver.zza > 0.0 -> state.speed.set(min(topSpeed, currSpeed + accel))
            driver.zza < 0.0 -> state.speed.set(max( -topSpeed * reverseDriveFactor, currSpeed - accel))
            else -> state.speed.set( lerp(currSpeed, 0.0, 0.05))
        }
        val vertInput = when {
            driver.jumping && state.stamina.get() != 0.0f -> 1.0
            driver.isShiftKeyDown -> -1.0
            else -> 0.0
        } * strafeFactor

        var newVelocity = Vec3(f, vertInput,-state.speed.get())

        // Align to the ride
        newVelocity = controller.orientation?.transform(newVelocity.toVector3f())?.toVec3d() ?: Vec3.ZERO
        newVelocity = newVelocity.yRot(vehicle.yRot.toRadians())
        val desiredVec = newVelocity.subtract(vehicle.deltaMovement)
        return vehicle.deltaMovement.add(desiredVec)
    }

    override fun angRollVel(
        settings: DolphinSettings,
        state: DolphinState,
        vehicle: PokemonEntity,
        driver: Player,
        deltaTime: Double
    ): Vec3 {
        if (Cobblemon.config.disableRoll) return Vec3.ZERO
        if (driver !is OrientationControllable) return Vec3.ZERO
        val controller = (driver as OrientationControllable).orientationController

        val currSpeed = vehicle.deltaMovement.length()

        val handling = vehicle.runtime.resolveDouble(settings.handlingExpr ?: globalDolphin.handlingExpr!!)
        val topSpeed = vehicle.runtime.resolveDouble(settings.speedExpr ?: globalDolphin.speedExpr!!) / 20.0

        val yawDeltaDeg =  deltaTime * handling * sin(Math.toRadians(controller.roll.toDouble())).pow(2) * sin(Math.toRadians(controller.roll.toDouble())).sign
        val trueYawDelt = yawDeltaDeg * abs(cos(Math.toRadians(controller.pitch.toDouble()))) * sqrt(RidingBehaviour.scaleToRange(currSpeed, 0.0, topSpeed))

        // Dampen yaw when upside down
        val yawDampen = (1 - abs(min(cos(controller.roll.toRadians()),0.0f)))
        controller.applyGlobalYaw(trueYawDelt.toFloat() * yawDampen)

        // Correct orientation when at low speeds
        correctOrientation(settings, state, vehicle, controller, driver, deltaTime)

        //yaw, pitch, roll
        return Vec3(0.0, state.currPitchCorrectionForce.get(), state.currRollCorrectionForce.get())
    }

    private fun correctOrientation(
        settings: DolphinSettings,
        state: DolphinState,
        vehicle: PokemonEntity,
        controller: OrientationController,
        driver: Player,
        deltaTime: Double
    ) {
        // Calculate correcting roll force.
        // Mult by cos(pitch) to ensure that it doesn't roll correct at when diving or climbing
        val currSpeed = vehicle.deltaMovement.length()
        val rollCorrectionTimer = vehicle.runtime.resolveDouble(settings.timeToRollCorrect ?: globalDolphin.timeToRollCorrect!!)
        val maxRollCorrectionRate = if (!(vehicle.isInWater || vehicle.isUnderWater)) 15.0f
            else 10.0f * (cos(controller.pitch.toRadians())).pow(2) * ((state.noInputTime.get() - rollCorrectionTimer) * 2).coerceIn(0.0, 1.0).toFloat()
        val maxRollForce = maxRollCorrectionRate / 40.0
        val rollArrivalDeg = 90.0

        if((state.speed.get() < 0.1 && driver.zza.toDouble() == 0.0) || state.noInputTime.get() > rollCorrectionTimer || !(vehicle.isInWater || vehicle.isUnderWater)) {
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
        val maxPitchCorrectionRate = if (!(vehicle.isInWater || vehicle.isUnderWater)) 15.0 else 5.0f * ((state.noInputTime.get() - rollCorrectionTimer) * 2).coerceIn(0.0, 1.0).toFloat() * (1 - (currSpeed * 0.5).coerceIn(0.0, 1.0))
        val maxPitchForce = maxPitchCorrectionRate / 40.0
        val pitchArrivalDeg = 45.0
        val targetPitch = if ((vehicle.isInWater || vehicle.isUnderWater)) 0.0f else (-asin(vehicle.deltaMovement.y / vehicle.deltaMovement.length()).toDegrees()).coerceIn(-45.0f, 45.0f)

        if((state.noInputTime.get() > rollCorrectionTimer && driver.zza.toDouble() == 0.0) || !(vehicle.isInWater || vehicle.isUnderWater)) {
            val arrivalInfluence = min(rollArrivalDeg, abs(Mth.wrapDegrees(targetPitch - controller.pitch.toDouble()))) / pitchArrivalDeg
            val desiredPitchForce = (targetPitch - controller.pitch).sign * maxPitchCorrectionRate * arrivalInfluence
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
        settings: DolphinSettings,
        state: DolphinState,
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
        if (abs(mouseX) < 1 && abs(mouseY) < 1 && (vehicle.isInWater || vehicle.isUnderWater)) {
            state.noInputTime.set(state.noInputTime.get() + deltaTime)
        } else {
            state.noInputTime.set(0.0)
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
        settings: DolphinSettings,
        state: DolphinState,
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

        val boostingHandleDebuff = if (state.boosting.get()) 0.2 else 1.0
        val handling = vehicle.runtime.resolveDouble(settings.handlingExpr ?: globalDolphin.handlingExpr!!) * boostingHandleDebuff
        val mouseInputMult = vehicle.runtime.resolveDouble(settings.mouseInputMult ?: globalDolphin.mouseInputMult!!)

        //Smooth out mouse input.
        val smoothingSpeed = 4.0
        val mouseXc = ((mouseX).coerceIn(-60.0, 60.0) / 60.0) * mouseInputMult
        val mouseYc = ((mouseY).coerceIn(-60.0, 60.0) / 60.0) * mouseInputMult
        val xInput = mouseXSmoother.getNewDeltaValue(mouseXc * 0.1, deltaTime * smoothingSpeed);
        val yInput = mouseYSmoother.getNewDeltaValue(mouseYc * 0.1, deltaTime * smoothingSpeed);

        //Give the ability to yaw with x mouse input when at low speeds.
        val yawForce =  xInput  * handling

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
        settings: DolphinSettings,
        state: DolphinState,
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
        val topSpeed = vehicle.runtime.resolveDouble(settings.speedExpr ?: globalDolphin.speedExpr!!) / 20.0
        val boostingHandleDebuff = if (state.boosting.get()) 0.2 else 1.0
        val mouseInputMult = vehicle.runtime.resolveDouble(settings.mouseInputMult ?: globalDolphin.mouseInputMult!!) * boostingHandleDebuff

        //Smooth out mouse input.
        val smoothingSpeed = 4.0
        val mouseXc = (mouseX).coerceIn(-45.0, 45.0) * mouseInputMult
        val mouseYc = (mouseY).coerceIn(-45.0, 45.0) * mouseInputMult
        val xInput = mouseXSmoother.getNewDeltaValue(mouseXc * 0.1, deltaTime * smoothingSpeed);
        val yInput = mouseYSmoother.getNewDeltaValue(mouseYc * 0.1, deltaTime * smoothingSpeed);
        val currSpeed = vehicle.deltaMovement.length()

        // Yaw locally
        // Dampen yaw when rolled or pitched substantially
        val yawDampen = abs(cos(controller.pitch.toRadians())).pow(2) * abs(cos(controller.roll.toRadians())).pow(2)
        val localYaw = xInput * (1 - (RidingBehaviour.scaleToRange(currSpeed, 0.0, topSpeed))).coerceIn(0.4, 1.0) * yawDampen

        // Pitch locally up or down depending upon a number of factors:
        // - Reduce pitch substantially when rolled and in a steep yaw. This prevents the player from ignoring a slow handling stat
        // - Do not pitch at all when at or below hover speed
        // - pitch faster at higher speeds
        val p = 1.0 - vehicle.runtime.resolveDouble(settings.horizontalPitchExpr ?: globalDolphin.horizontalPitchExpr!!)
        val localPitch = yInput * (1 - abs(sin(controller.roll.toRadians())) * p * hypot(controller.forwardVector.x.toDouble(), controller.forwardVector.z.toDouble()).toFloat())
//        localPitch *= if (currSpeed > hoverSpeed) RidingBehaviour.scaleToRange(currSpeed, hoverSpeed, topSpeed)
//        else 0.0

        // Roll less when slow and not at all when at hoverSpeed or lower.
        // Apply a roll righting force when trying to pitch hard while rolled sideways. This nudges the player towards
        // pitching globally
        val rollForce =  xInput * ((RidingBehaviour.scaleToRange(currSpeed, 0.0, topSpeed))).coerceIn(0.2, 1.0) //+ pitchInfluencedRollCorrection

        //yaw, pitch, roll
        return Vec3(localYaw, localPitch, rollForce)
    }

    override fun canJump(
        settings: DolphinSettings,
        state: DolphinState,
        vehicle: PokemonEntity,
        driver: Player
    ): Boolean {
        return false
    }

    override fun setRideBar(
        settings: DolphinSettings,
        state: DolphinState,
        vehicle: PokemonEntity,
        driver: Player
    ): Float {
        return (state.stamina.get() / 1.0f)
    }

    override fun jumpForce(
        settings: DolphinSettings,
        state: DolphinState,
        vehicle: PokemonEntity,
        driver: Player,
        jumpStrength: Int
    ): Vec3 {
        return Vec3.ZERO
    }

    override fun gravity(
        settings: DolphinSettings,
        state: DolphinState,
        vehicle: PokemonEntity,
        regularGravity: Double
    ): Double {
        return 0.0 //if (!vehicle.isInWater && !vehicle.isUnderWater) 1.0 else 0.0
    }

    override fun rideFovMultiplier(
        settings: DolphinSettings,
        state: DolphinState,
        vehicle: PokemonEntity,
        driver: Player
    ): Float {
        return if (state.boosting.get()) 1.2f else 1.0f
    }

    override fun useAngVelSmoothing(settings: DolphinSettings, state: DolphinState, vehicle: PokemonEntity): Boolean {
        return false
    }

    override fun useRidingAltPose(
        settings: DolphinSettings,
        state: DolphinState,
        vehicle: PokemonEntity,
        driver: Player
    ): ResourceLocation {
        return cobblemonResource("no_pose")
    }

    override fun inertia(settings: DolphinSettings, state: DolphinState, vehicle: PokemonEntity): Double {
        return if (!vehicle.isInWater && !vehicle.isUnderWater) 1.0 else 0.1
    }

    override fun shouldRoll(settings: DolphinSettings, state: DolphinState, vehicle: PokemonEntity): Boolean {
        return true
    }

    override fun turnOffOnGround(settings: DolphinSettings, state: DolphinState, vehicle: PokemonEntity): Boolean {
        return false
    }

    override fun dismountOnShift(settings: DolphinSettings, state: DolphinState, vehicle: PokemonEntity): Boolean {
        return false
    }

    override fun shouldRotatePokemonHead(
        settings: DolphinSettings,
        state: DolphinState,
        vehicle: PokemonEntity
    ): Boolean {
        return false
    }

    override fun shouldRotateRiderHead(
        settings: DolphinSettings,
        state: DolphinState,
        vehicle: PokemonEntity
    ): Boolean {
        return false
    }

    override fun getRideSounds(
        settings: DolphinSettings,
        state: DolphinState,
        vehicle: PokemonEntity
    ): RideSoundSettingsList {
        return settings.rideSounds
    }

    override fun createDefaultState(settings: DolphinSettings) = DolphinState()

}

class DolphinSettings : RidingBehaviourSettings {
    override val key = DolphinBehaviour.KEY
    override val stats = mutableMapOf<RidingStat, IntRange>()

    var infiniteStamina: Expression? = null
        private set

    var canJump: Expression? = null
        private set

    var reverseDriveFactor: Expression? = null
        private set

    var strafeFactor: Expression? = null
        private set

    var timeToRollCorrect: Expression? = null
        private set

    // Boost influence when boosting
    var jumpExpr: Expression? = null
    // Yaw rate in degrees per second
    var handlingExpr: Expression? = null
    var horizontalPitchExpr: Expression? = null
    var mouseInputMult: Expression? = null
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
        buffer.writeNullableExpression(canJump)
        buffer.writeNullableExpression(reverseDriveFactor)
        buffer.writeNullableExpression(strafeFactor)
        buffer.writeNullableExpression(timeToRollCorrect)
        buffer.writeNullableExpression(jumpExpr)
        buffer.writeNullableExpression(handlingExpr)
        buffer.writeNullableExpression(horizontalPitchExpr)
        buffer.writeNullableExpression(mouseInputMult)
        buffer.writeNullableExpression(speedExpr)
        buffer.writeNullableExpression(accelerationExpr)
        buffer.writeNullableExpression(staminaExpr)
    }

    override fun decode(buffer: RegistryFriendlyByteBuf) {
        stats.putAll(buffer.readRidingStats())
        rideSounds = RideSoundSettingsList.decode(buffer)
        infiniteStamina = buffer.readNullableExpression()
        canJump = buffer.readNullableExpression()
        reverseDriveFactor = buffer.readNullableExpression()
        strafeFactor = buffer.readNullableExpression()
        timeToRollCorrect = buffer.readNullableExpression()
        jumpExpr = buffer.readNullableExpression()
        handlingExpr = buffer.readNullableExpression()
        horizontalPitchExpr = buffer.readNullableExpression()
        mouseInputMult = buffer.readNullableExpression()
        speedExpr = buffer.readNullableExpression()
        accelerationExpr = buffer.readNullableExpression()
        staminaExpr = buffer.readNullableExpression()
    }
}

class DolphinState : RidingBehaviourState() {
    var lastVelocity = ridingState(Vec3.ZERO, Side.BOTH)
    var currRollCorrectionForce = ridingState(0.0, Side.CLIENT)
    var currPitchCorrectionForce = ridingState(0.0, Side.CLIENT)
    var noInputTime = ridingState(0.0, Side.CLIENT)
    var boosting = ridingState(false, Side.BOTH)
    var boostIsToggleable = ridingState(false, Side.CLIENT)
    var speed = ridingState(0.0, Side.CLIENT)
    var hasBreached = ridingState(false, Side.CLIENT)

    override fun encode(buffer: FriendlyByteBuf) {
        super.encode(buffer)
        buffer.writeBoolean(boosting.get())
    }

    override fun decode(buffer: FriendlyByteBuf) {
        super.decode(buffer)
        boosting.set(buffer.readBoolean(), forced = true)
    }

    override fun reset() {
        super.reset()
        lastVelocity.set(Vec3.ZERO, forced = true)
        currRollCorrectionForce.set(0.0, forced = true)
        currPitchCorrectionForce.set(0.0, forced = true)
        noInputTime.set(0.0, forced = true)
        boosting.set(false, forced = true)
        boostIsToggleable.set(true, forced = true)
        speed.set(0.0, forced = true)
        hasBreached.set(false, forced = true)
    }

    override fun toString(): String {
        return "DolphinState(lastVelocity=${lastVelocity.get()})"
    }

    override fun copy() = DolphinState().also {
        it.stamina.set(stamina.get(), forced = true)
        it.rideVelocity.set(rideVelocity.get(), forced = true)
        it.lastVelocity.set(lastVelocity.get(), forced = true)
        it.currRollCorrectionForce.set(this.currRollCorrectionForce.get(), forced = true)
        it.currPitchCorrectionForce.set(this.currPitchCorrectionForce.get(), forced = true)
        it.noInputTime.set(this.noInputTime.get(), forced = true)
        it.boosting.set(this.boosting.get(), forced = true)
        it.boostIsToggleable.set(this.boosting.get(), forced = true)
        it.speed.set(this.speed.get(), forced = true)
        it.hasBreached.set(this.hasBreached.get(), forced = true)
    }

    override fun shouldSync(previous: RidingBehaviourState): Boolean {
        if (previous !is DolphinState) return false
        if (previous.boosting.get() != boosting.get()) return true
        return super.shouldSync(previous)
    }
}
