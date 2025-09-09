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
import com.cobblemon.mod.common.api.riding.RidingStyle
import com.cobblemon.mod.common.api.riding.behaviour.*
import com.cobblemon.mod.common.api.riding.behaviour.types.land.HorseSettings
import com.cobblemon.mod.common.api.riding.behaviour.types.land.HorseState
import com.cobblemon.mod.common.api.riding.posing.PoseOption
import com.cobblemon.mod.common.api.riding.posing.PoseProvider
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.*
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.RegistryFriendlyByteBuf
import com.cobblemon.mod.common.api.riding.sound.RideSoundSettingsList
import com.cobblemon.mod.common.api.riding.stats.RidingStat
import com.cobblemon.mod.common.config.CobblemonConfig
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.SmoothDouble
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.Shapes
import kotlin.math.*

class JetBehaviour : RidingBehaviour<JetSettings, JetState> {
    companion object {
        val KEY = cobblemonResource("air/jet")
    }

    override val key = KEY

    override fun getRidingStyle(settings: JetSettings, state: JetState): RidingStyle {
        return RidingStyle.AIR
    }

    val poseProvider = PoseProvider<JetSettings, JetState>(PoseType.HOVER)
        .with(PoseOption(PoseType.FLY) { _, state, _ -> state.rideVelocity.get().z > 0.1 })

    override fun isActive(settings: JetSettings, state: JetState, vehicle: PokemonEntity): Boolean {
        return Shapes.create(vehicle.boundingBox).blockPositionsAsListRounded().any {
            //Need to check other fluids
            if (vehicle.isInWater || vehicle.isUnderWater) {
                return@any false
            }
            //This might not actually work, depending on what the yPos actually is. yPos of the middle of the entity? the feet?
            if (it.y.toDouble() == (vehicle.position().y)) {
                val blockState = vehicle.level().getBlockState(it.below())
                return@any !(!blockState.isAir && blockState.fluidState.isEmpty)
            }
            true
        }
    }

    override fun pose(settings: JetSettings, state: JetState, vehicle: PokemonEntity): PoseType {
        return poseProvider.select(settings, state, vehicle)
    }

    override fun speed(settings: JetSettings, state: JetState, vehicle: PokemonEntity, driver: Player): Float {
        return state.rideVelocity.get().length().toFloat()
    }

    override fun tick(settings: JetSettings, state: JetState, vehicle: PokemonEntity, driver: Player, input: Vec3) {
        if(vehicle.level().isClientSide) {
            handleBoosting(state)
            tickStamina(settings, state, vehicle)
        }
    }

    fun handleBoosting(
        state: JetState,
    ) {
        //If the forward key is not held then it cannot be boosting
        val boostKeyPressed = Minecraft.getInstance().options.keySprint.isDown()

        if(state.stamina.get() != 0.0f && boostKeyPressed) {
            if (state.stamina.get() >= 0.25f) {
                //If on the previous tick the boost key was held then don't change if the ride is boosting
                if(state.boostIsToggleable.get()) {
                    //flip the boosting state if boost key is pressed
                    state.boosting.set(!state.boosting.get())
                }
                //If the boost key is not held then next tick boosting is toggleable
                state.boostIsToggleable.set(false)
            }
        } else {
            //Turn off boost and reset boost params
            state.boostIsToggleable.set(true)
            state.boosting.set(false)
            state.canSpeedBurst.set(true)
        }
    }

    fun tickStamina(
        settings: JetSettings,
        state: JetState,
        vehicle: PokemonEntity,
    ) {
        val stam = state.stamina.get()
        var newStam = stam
        val stamDrainRate = (1.0f / vehicle.runtime.resolveDouble(settings.staminaExpr)).toFloat() / 20.0f

        if (state.boosting.get()) {
            newStam = max(0.0f,stam - stamDrainRate)

        } else {
            newStam = min(1.0f,stam + stamDrainRate * 0.25f)
        }

        state.stamina.set(newStam)
    }

    override fun rotation(
        settings: JetSettings,
        state: JetState,
        vehicle: PokemonEntity,
        driver: LivingEntity
    ): Vec2 {
        return Vec2(driver.xRot * 0.5f, driver.yRot)
    }

    override fun velocity(
        settings: JetSettings,
        state: JetState,
        vehicle: PokemonEntity,
        driver: Player,
        input: Vec3
    ): Vec3 {
        var upForce = 0.0
        var forwardForce = 0.0

        val controller = (driver as? OrientationControllable)?.orientationController

        //Calculate ride space velocity
        calculateRideSpaceVel(settings, state, vehicle, driver)

        //Translate ride space velocity to world space velocity.
        if (controller != null) {
            upForce = -1.0 * sin(Math.toRadians(controller.pitch.toDouble())) * state.rideVelocity.get().z
            forwardForce = cos(Math.toRadians(controller.pitch.toDouble())) * state.rideVelocity.get().z
        }

        val velocity = Vec3(0.0, upForce, forwardForce)

        return velocity
    }

    /*
    *  Calculates the change in the ride space vector due to player input and ride state
    */
    private fun calculateRideSpaceVel(
        settings: JetSettings,
        state: JetState,
        vehicle: PokemonEntity,
        driver: Player
    ) {
        val topSpeed = vehicle.runtime.resolveDouble(settings.speedExpr) / 20.0
        val accel = vehicle.runtime.resolveDouble(settings.accelerationExpr) / 400.0
        val minSpeed = vehicle.runtime.resolveDouble(settings.minSpeed) / 20.0
        val speed = state.rideVelocity.get().length()
        val boostMult = vehicle.runtime.resolveDouble(settings.jumpExpr)

        val boostTopSpeed = topSpeed * boostMult
        val boostAccel = accel * boostMult

        //speed up and slow down based on input
        if (state.boosting.get() && speed < boostTopSpeed) {
            state.rideVelocity.set(
                Vec3(
                    state.rideVelocity.get().x,
                    state.rideVelocity.get().y,
                    min(state.rideVelocity.get().z + (boostAccel), boostTopSpeed)
                )
            )
        } else if ((speed < minSpeed) || (driver.zza > 0.0 && speed < topSpeed)) {
            //modify acceleration to be slower when at closer speeds to top speed
            val accelMod = max(-(RidingBehaviour.scaleToRange(speed, minSpeed, topSpeed)) + 1, 0.0)
            state.rideVelocity.set(
                Vec3(
                    state.rideVelocity.get().x,
                    state.rideVelocity.get().y,
                    min(state.rideVelocity.get().z + (accel * accelMod), topSpeed)
                )
            )
        } else if (driver.zza < 0.0 && speed > minSpeed) {
            // Decelerate currently always a constant half of max acceleration.
            state.rideVelocity.set(
                Vec3(
                    state.rideVelocity.get().x,
                    state.rideVelocity.get().y,
                    max(state.rideVelocity.get().z - ((accel) / 2), minSpeed)
                )
            )
        } else if (speed > topSpeed) {
            state.rideVelocity.set(
                state.rideVelocity.get().scale(0.98)
            )
        }
    }

    override fun angRollVel(
        settings: JetSettings,
        state: JetState,
        vehicle: PokemonEntity,
        driver: Player,
        deltaTime: Double
    ): Vec3 {
        //don't yaw this way if
        if (Cobblemon.config.disableRoll) return Vec3.ZERO

        //Cap at a rate of 5fps so frame skips dont lead to huge jumps
        val cappedDeltaTime = min(deltaTime, 0.2)

        //Get handling in degrees per second
        val yawRotRate = vehicle.runtime.resolveDouble(settings.handlingYawExpr)

        //Base the change off of deltatime.
        var handlingYaw = yawRotRate * (cappedDeltaTime)

        //apply stamina debuff if applicable
        handlingYaw *= if (state.stamina.get() > 0.0) 1.0 else 0.5

        //A+D to yaw
        val yawForce = driver.xxa * handlingYaw * -1

        return Vec3(yawForce, 0.0, 0.0)
    }

    override fun rotationOnMouseXY(
        settings: JetSettings,
        state: JetState,
        vehicle: PokemonEntity,
        driver: Player,
        mouseY: Double,
        mouseX: Double,
        mouseYSmoother: SmoothDouble,
        mouseXSmoother: SmoothDouble,
        sensitivity: Double,
        deltaTime: Double
    ): Vec3 {
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
        settings: JetSettings,
        state: JetState,
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

        //Cap at a rate of 5fps so frame skips dont lead to huge jumps
        val cappedDeltaTime = min(deltaTime, 0.2)

        // Accumulate the mouse input
        state.currMouseXForce.set((state.currMouseXForce.get() + (0.0015 * mouseX)).coerceIn(-1.0, 1.0))
        state.currMouseYForce.set((state.currMouseYForce.get() + (0.0015 * mouseY)).coerceIn(-1.0, 1.0))

        //Get handling in degrees per second
        var handling = vehicle.runtime.resolveDouble(settings.handlingExpr)
        //convert it to delta time
        handling *= (cappedDeltaTime)

        var pitchRot = handling * state.currMouseYForce.get()

        // Yaw
        val yawRot = handling * 0.5 * state.currMouseXForce.get()
        controller.applyGlobalYaw(yawRot.toFloat())

        if (abs(controller.pitch + pitchRot) >= 89.5 ) {
            pitchRot = 0.0
            state.currMouseYForce.set(0.0)
            mouseYSmoother.reset()
        } else {
            controller.applyGlobalPitch(pitchRot.toFloat()  * -1.0f)
        }

        // Have accumulated input begin decay when no input detected
        if (abs(mouseX) == 0.0) {
            // Have decay on roll be much stronger.
            state.currMouseXForce.set(lerp(state.currMouseXForce.get(), 0.0, 0.02))
        }
        if (mouseY == 0.0) {
            state.currMouseYForce.set(lerp(state.currMouseYForce.get(), 0.0, 0.005))
        }

        //yaw, pitch, roll
        return Vec3.ZERO
    }

    fun rollRotation(
        settings: JetSettings,
        state: JetState,
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
        //TODO: figure out a cleaner solution to this issue of large jumps when skipping frames or lagging
        //Cap at a rate of 5fps so frame skips dont lead to huge jumps
        val cappedDeltaTime = min(deltaTime, 0.2)

        // Accumulate the mouse input
        state.currMouseXForce.set((state.currMouseXForce.get() + (0.0015 * mouseX)).coerceIn(-1.0, 1.0))
        state.currMouseYForce.set((state.currMouseYForce.get() + (0.0015 * mouseY)).coerceIn(-1.0, 1.0))

        //Get handling in degrees per second
        var handling = vehicle.runtime.resolveDouble(settings.handlingExpr)

        //convert it to delta time
        handling *= (cappedDeltaTime)


        val poke = driver.vehicle as? PokemonEntity

        //TODO: reevaluate if deadzones are needed and if they are still causing issues.
        //create deadzones for the constant input values.
        //val xInput = remapWithDeadzone(state.currMouseXForce, 0.025, 1.0)
        //val yInput = remapWithDeadzone(state.currMouseYForce, 0.025, 1.0)

        val pitchRot = handling * state.currMouseYForce.get()

        // Roll
        val rollRot = handling * 1.5 * state.currMouseXForce.get()

        val mouseRotation = Vec3(0.0, pitchRot, rollRot)

        // Have accumulated input begin decay when no input detected
        if (abs(mouseX) == 0.0) {
            // Have decay on roll be much stronger.
            state.currMouseXForce.set(lerp(state.currMouseXForce.get(), 0.0, 0.02))
        }
        if (mouseY == 0.0) {
            state.currMouseYForce.set(lerp(state.currMouseYForce.get(), 0.0, 0.005))
        }

        //yaw, pitch, roll
        return mouseRotation
    }

    override fun canJump(
        settings: JetSettings,
        state: JetState,
        vehicle: PokemonEntity,
        driver: Player
    ): Boolean {
        return false
    }

    override fun setRideBar(
        settings: JetSettings,
        state: JetState,
        vehicle: PokemonEntity,
        driver: Player
    ): Float {
        return (state.stamina.get() / 1.0f)
    }

    override fun jumpForce(
        settings: JetSettings,
        state: JetState,
        vehicle: PokemonEntity,
        driver: Player,
        jumpStrength: Int
    ): Vec3 {
        return Vec3.ZERO
    }

    override fun gravity(
        settings: JetSettings,
        state: JetState,
        vehicle: PokemonEntity,
        regularGravity: Double
    ): Double {
        return 0.0
    }

    override fun rideFovMultiplier(
        settings: JetSettings,
        state: JetState,
        vehicle: PokemonEntity,
        driver: Player
    ): Float {
        return if (state.boosting.get()) 1.2f else 1.0f
    }

    override fun useAngVelSmoothing(settings: JetSettings, state: JetState, vehicle: PokemonEntity): Boolean {
        return true
    }

    override fun useRidingAltPose(
        settings: JetSettings,
        state: JetState,
        vehicle: PokemonEntity,
        driver: Player
    ): ResourceLocation {
        return cobblemonResource("no_pose")
    }

    override fun inertia(settings: JetSettings, state: JetState, vehicle: PokemonEntity): Double {
        return 1.0
    }

    override fun shouldRoll(settings: JetSettings, state: JetState, vehicle: PokemonEntity): Boolean {
        return true
    }

    override fun turnOffOnGround(settings: JetSettings, state: JetState, vehicle: PokemonEntity): Boolean {
        return false
    }

    override fun dismountOnShift(settings: JetSettings, state: JetState, vehicle: PokemonEntity): Boolean {
        return false
    }

    override fun shouldRotatePokemonHead(
        settings: JetSettings,
        state: JetState,
        vehicle: PokemonEntity
    ): Boolean {
        return false
    }

    override fun shouldRotateRiderHead(
        settings: JetSettings,
        state: JetState,
        vehicle: PokemonEntity
    ): Boolean {
        return false
    }

    override fun getRideSounds(
        settings: JetSettings,
        state: JetState,
        vehicle: PokemonEntity
    ): RideSoundSettingsList {
        return settings.rideSounds
    }

    override fun createDefaultState(settings: JetSettings) = JetState()

    override fun damageOnCollision(
        settings: JetSettings,
        state: JetState,
        vehicle: PokemonEntity,
        impactVec: Vec3
    ): Boolean {
        val impactSpeed = impactVec.horizontalDistance().toFloat() * 10f
        return vehicle.causeFallDamage(impactSpeed, 1f, vehicle.damageSources().flyIntoWall())
    }
}

class JetSettings : RidingBehaviourSettings {
    override val key = JetBehaviour.KEY
    override val stats = mutableMapOf<RidingStat, IntRange>()

    var gravity: Expression = "0".asExpression()
        private set

    var minSpeed: Expression = "12.0".asExpression()
        private set

    var handlingYawExpr: Expression = "q.get_ride_stats('SKILL', 'AIR', 50.0, 25.0)".asExpression()
        private set

    // Make configurable by json
    var infiniteStamina: Expression = "false".asExpression()
        private set

    // Boost power. Mult for top speed and accel while boosting
    var jumpExpr: Expression = "q.get_ride_stats('JUMP', 'AIR', 1.7, 1.2)".asExpression()
        private set

    // Turn rate in degrees per second
    var handlingExpr: Expression = "q.get_ride_stats('SKILL', 'AIR', 120.0, 60.0)".asExpression()
        private set
    // Top Speed in blocks per second
    var speedExpr: Expression = "q.get_ride_stats('SPEED', 'AIR', 36.0, 20.0)".asExpression()
        private set
    // Acceleration in blocks per s^2
    var accelerationExpr: Expression =
        "q.get_ride_stats('ACCELERATION', 'AIR', 5.0, 2.5)".asExpression()
        private set
    // Time in seconds to drain full bar of stamina when boosting
    var staminaExpr: Expression = "q.get_ride_stats('STAMINA', 'AIR', 8.0, 4.0)".asExpression()
        private set

    var rideSounds: RideSoundSettingsList = RideSoundSettingsList()

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeResourceLocation(key)
        buffer.writeRidingStats(stats)
        rideSounds.encode(buffer)
        buffer.writeExpression(gravity)
        buffer.writeExpression(minSpeed)
        buffer.writeExpression(handlingYawExpr)
        buffer.writeExpression(infiniteStamina)
        buffer.writeExpression(jumpExpr)
        buffer.writeExpression(handlingExpr)
        buffer.writeExpression(speedExpr)
        buffer.writeExpression(accelerationExpr)
        buffer.writeExpression(staminaExpr)
    }

    override fun decode(buffer: RegistryFriendlyByteBuf) {
        stats.putAll(buffer.readRidingStats())
        rideSounds = RideSoundSettingsList.decode(buffer)
        gravity = buffer.readExpression()
        minSpeed = buffer.readExpression()
        handlingYawExpr = buffer.readExpression()
        infiniteStamina = buffer.readExpression()
        jumpExpr = buffer.readExpression()
        handlingExpr = buffer.readExpression()
        speedExpr = buffer.readExpression()
        accelerationExpr = buffer.readExpression()
        staminaExpr = buffer.readExpression()
    }
}

class JetState : RidingBehaviourState() {
    var currSpeed = ridingState(0.0, Side.CLIENT)
    var currMouseXForce = ridingState(0.0, Side.CLIENT)
    var currMouseYForce = ridingState(0.0, Side.CLIENT)
    var boosting = ridingState(false, Side.BOTH)
    var boostIsToggleable = ridingState(false, Side.BOTH)
    var canSpeedBurst = ridingState(false, Side.BOTH)

    override fun encode(buffer: FriendlyByteBuf) {
        super.encode(buffer)
        buffer.writeDouble(currSpeed.get())
        buffer.writeFloat(stamina.get())
        buffer.writeBoolean(boosting.get())
        buffer.writeBoolean(boostIsToggleable.get())
        buffer.writeBoolean(canSpeedBurst.get())
    }

    override fun decode(buffer: FriendlyByteBuf) {
        super.decode(buffer)
        currSpeed.set(buffer.readDouble(), forced = true)
        boosting.set(buffer.readBoolean(), forced = true)
        boostIsToggleable.set(buffer.readBoolean(), forced = true)
        canSpeedBurst.set(buffer.readBoolean(), forced = true)
    }

    override fun reset() {
        super.reset()
        currSpeed.set(0.0, forced = true)
        currMouseXForce.set(0.0, forced = true)
        currMouseYForce.set(0.0, forced = true)
        boosting.set(false, forced = true)
        boostIsToggleable.set(false, forced = true)
        canSpeedBurst.set(true, forced = true)
    }

    override fun copy() = JetState().also {
        it.currSpeed.set(currSpeed.get(), forced = true)
        it.stamina.set(stamina.get(), forced = true)
        it.rideVelocity.set(rideVelocity.get(), forced = true)
        it.boosting.set(this.boosting.get(), forced = true)
        it.boostIsToggleable.set(this.boosting.get(), forced = true)
        it.canSpeedBurst.set(this.canSpeedBurst.get(), forced = true)
    }

    override fun shouldSync(previous: RidingBehaviourState): Boolean {
        if (previous !is JetState) return false
        if (previous.currSpeed != currSpeed) return true
        return super.shouldSync(previous)
    }
}
