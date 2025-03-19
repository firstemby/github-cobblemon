/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.riding.controllers

import com.bedrockk.molang.Expression
import com.bedrockk.molang.runtime.value.DoubleValue
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.Rollable
import com.cobblemon.mod.common.api.riding.controller.RideController
import com.cobblemon.mod.common.api.riding.controller.posing.PoseOption
import com.cobblemon.mod.common.api.riding.controller.posing.PoseProvider
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.riding.states.JetAirState
import com.cobblemon.mod.common.util.*
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.util.SmoothDouble
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import kotlin.math.*

class JetAirController : RideController {
    override val key = KEY
    override val poseProvider = PoseProvider(PoseType.HOVER)
        .with(PoseOption(PoseType.FLY) { it.deltaMovement.length() > 0 })

    override val condition: (PokemonEntity) -> Boolean = { true }

    var gravity: Expression = "0".asExpression()
        private set

    var minSpeed: Expression = "1.2".asExpression()
        private set
    var handlingExpr: Expression = "q.get_ride_stats('SKILL', 'AIR', 140.0, 20.0)".asExpression()
        private set
    var handlingYawExpr: Expression = "q.get_ride_stats('SKILL', 'AIR', 25.0, 8.0)".asExpression()
        private set
    var topSpeedExpr: Expression = "q.get_ride_stats('SPEED', 'AIR', 2.5, 1.0)".asExpression()
        private set
    // Max accel is a whole 1.0 in 1 second. The conversion in the function below is to convert seconds to ticks
    var accelExpr: Expression = "q.get_ride_stats('ACCELERATION', 'AIR', (1.0 / (20.0 * 1.0)), (1.0 / (20.0 * 5.0)))".asExpression()
        private set
    // Between 60 seconds and 10 seconds at the lowest when at full speed.
    var staminaExpr: Expression = "q.get_ride_stats('SKILL', 'AIR', 60.0, 10.0)".asExpression()
        private set
    var jumpExpr: Expression = "q.get_ride_stats('SKILL', 'AIR', 300.0, 128.0)".asExpression()
        private set

    // Make configurable by json
    var infiniteStamina: Expression = "false".asExpression()
        private set
    var infiniteAltitude: Expression = "false".asExpression()
        private set

    override fun speed(entity: PokemonEntity, driver: Player): Float {

        //retrieve stats
        val topSpeed = getRuntime(entity).resolveDouble(topSpeedExpr)
        val staminaStat = getRuntime(entity).resolveDouble(staminaExpr)

        //retrieve state
        val state = getState(entity, ::JetAirState)

        //retrieve minSpeed
        val minSpeed = getRuntime(entity).resolveDouble(minSpeed)

        //Reduce stamina unless stamina is infinite
        if( !getRuntime(entity).resolveBoolean(infiniteStamina)) {
            //Calculate stamina loss due to speed
            //At max speed it will tick down 0.1 a second so the stamina will last ten seconds
            //There has got to be a better way to express this equation. It interpolates between 0.5 and 1.0
            var staminaRate = (normalizeSpeed(state.rideVel.length(), minSpeed, topSpeed))

            //interpolate between 0.25 and 1.0 so that you always have at least a min of 0.25 stam loss
            staminaRate = 0.25 + (0.75 * staminaRate.pow(3))

            //Calculate stamina loss in seconds achievable at top speed
            val staminaLoss = staminaRate * (1.0 / (20.0 * staminaStat))
            state.stamina = max(state.stamina - staminaLoss, 0.0).toFloat()
        }
        else{
            state.stamina = 1.0f
        }

        return state.rideVel.length().toFloat()
    }

    override fun rotation(entity: PokemonEntity, driver: LivingEntity): Vec2 {
        return Vec2(driver.xRot * 0.5f, driver.yRot)
    }

    override fun velocity(entity: PokemonEntity, driver: Player, input: Vec3): Vec3 {

        var upForce = 0.0
        var forwardForce = 0.0

        //Gather state information
        val state = getState(entity, ::JetAirState)

        val rollable = driver as? Rollable

        //Calculate ride space velocity
        calculateRideSpaceVel(entity, driver, state)

        //Translate ride space velocity to world space velocity.
        if( rollable != null ) {

            upForce =  -1.0 * sin(Math.toRadians( rollable.pitch.toDouble() )) * state.rideVel.z

            forwardForce =  cos(Math.toRadians( rollable.pitch.toDouble() )) * state.rideVel.z

        }

        //If stamina has run out then initiate forced glide down.
        upForce = if(state.stamina > 0.0) upForce else -0.7

        val altitudeLimit = getRuntime(entity).resolveDouble(jumpExpr)
        val pushingHeightLimit = if(getRuntime(entity).resolveBoolean(infiniteAltitude)) false
                                 else (entity.y >= 128 && entity.xRot <= 0)


        //Incorporate the altitude limiter.
        //Apply downwards force if limit is reached. Resist upwards force on a buffer of 30 blocks past the limit.
        upForce = if(pushingHeightLimit)  upForce - upForce * ((entity.y - altitudeLimit) / 30) - 0.5
                  else upForce

        val velocity = Vec3(0.0 , upForce, forwardForce )

        return velocity
    }

    override fun useAngVelSmoothing(entity: PokemonEntity): Boolean = true

    override fun angRollVel(entity: PokemonEntity, driver: Player, deltaTime: Double): Vec3 {
        //Retrieve state
        val state = getState(entity, ::JetAirState)

        //Cap at a rate of 5fps so frame skips dont lead to huge jumps
        val cappedDeltaTime = min( deltaTime, 0.2)

        //Get handling in degrees per second
        val yawRotRate = getRuntime(entity).resolveDouble(handlingYawExpr)

        //Base the change off of deltatime.
        var handlingYaw = yawRotRate * (cappedDeltaTime)

        //apply stamina debuff if applicable
        handlingYaw *= if(state.stamina > 0.0) 1.0 else 0.5

        //A+D to yaw
        val yawForce = driver.xxa * handlingYaw * -1

        return Vec3( yawForce, 0.0, 0.0)
    }

    override fun rotationOnMouseXY
                (entity: PokemonEntity,
                 driver: Player,
                 yMouse: Double,
                 xMouse: Double,
                 yMouseSmoother: SmoothDouble,
                 xMouseSmoother: SmoothDouble,
                 sensitivity: Double,
                 deltaTime: Double ): Vec3
    {
        if(driver !is Rollable) return Vec3.ZERO
        //TODO: figure out a cleaner solution to this issue of large jumps when skipping frames or lagging
        //Cap at a rate of 5fps so frame skips dont lead to huge jumps
        val cappedDeltaTime = min( deltaTime, 0.2)

        //Retrieve state
        val state = getState(entity, ::JetAirState)

        val invertRoll = if (Cobblemon.config.invertRoll) -1 else 1
        val invertPitch = if (Cobblemon.config.invertPitch) -1 else 1

        // Accumulate the mouse input
        state.currMouseXForce = (state.currMouseXForce + (0.0015 * xMouse * invertRoll)).coerceIn(-1.0, 1.0)
        state.currMouseYForce = (state.currMouseYForce + (0.0015 * yMouse * invertPitch)).coerceIn(-1.0, 1.0)

        //Get handling in degrees per second
        var handling = getRuntime(entity).resolveDouble(handlingExpr)

        //convert it to delta time
        handling *= (cappedDeltaTime)

        //apply stamina debuff if applicable
        handling *= if(state.stamina > 0.0) 1.0 else 0.5

        val poke = driver.vehicle as? PokemonEntity

        //TODO: reevaluate if deadzones are needed and if they are still causing issues.
        //create deadzones for the constant input values.
        //val xInput = remapWithDeadzone(state.currMouseXForce, 0.025, 1.0)
        //val yInput = remapWithDeadzone(state.currMouseYForce, 0.025, 1.0)

        val pitchRot = handling * state.currMouseYForce

        //Roll is 1.5 times as fast as pitch
        val rollRot =  handling * 1.5 * state.currMouseXForce

        //yaw, pitch, roll
        return Vec3(0.0, pitchRot,  rollRot)
    }

    override fun setRideBar(entity: PokemonEntity, driver: Player): Float {
        //Retrieve stamina from state
        val state = getState(entity, ::JetAirState)

        return (state.stamina / 1.0f)
    }

    override fun canJump(
        entity: PokemonEntity,
        driver: Player
    ): Boolean {
        return false
    }

    override fun jumpForce(
        entity: PokemonEntity,
        driver: Player,
        jumpStrength: Int
    ): Vec3 {
        return Vec3.ZERO
    }

    override fun gravity(entity: PokemonEntity, regularGravity: Double): Double {
        val grav = 0.0
        return grav
    }

    override fun shouldRoll(entity: PokemonEntity) = true

    override fun rideFovMult(entity: PokemonEntity, driver: Player): Float {
        val topSpeed = getRuntime(entity).resolveDouble(topSpeedExpr)
        val minSpeed = getRuntime(entity).resolveDouble(minSpeed)
        val state = getState(entity, ::JetAirState)

        //Must I ensure that topspeed is greater than minimum?
        val normalizedSpeed = normalizeSpeed(state.rideVel.length(), minSpeed, topSpeed)

        //TODO: Determine if this should be based on max possible speed instead of top speed.
        //Only ever want the fov change to be a max of 0.2 and for it to have non linear scaling.
        return 1.0f + normalizedSpeed.pow(2).toFloat() * 0.2f

    }

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        super.encode(buffer)
        buffer.writeString(gravity.toString())
        buffer.writeString(minSpeed.toString())
        buffer.writeString(handlingExpr.toString())
        buffer.writeString(handlingYawExpr.toString())
        buffer.writeString(topSpeedExpr.toString())
        buffer.writeString(accelExpr.toString())
        buffer.writeString(jumpExpr.toString())
        buffer.writeString(staminaExpr.toString())
        buffer.writeString(infiniteStamina.toString())
        buffer.writeString(infiniteAltitude.toString())
    }

    override fun decode(buffer: RegistryFriendlyByteBuf) {
        gravity = buffer.readString().asExpression()
        minSpeed = buffer.readString().asExpression()
        handlingExpr = buffer.readString().asExpression()
        handlingYawExpr = buffer.readString().asExpression()
        topSpeedExpr = buffer.readString().asExpression()
        accelExpr = buffer.readString().asExpression()
        jumpExpr = buffer.readString().asExpression()
        staminaExpr = buffer.readString().asExpression()
        infiniteStamina = buffer.readString().asExpression()
        infiniteAltitude = buffer.readString().asExpression()
    }

    companion object {
        val KEY = cobblemonResource("air/jet")
    }

    /*
    *  Calculates the change in the ride space vector due to player input and ride state
    */
    fun calculateRideSpaceVel( entity: PokemonEntity, driver: Player, state: JetAirState){

        val topSpeed = getRuntime(entity).resolveDouble(topSpeedExpr)
        val accel = getRuntime(entity).resolveDouble(accelExpr)
        val altitudeLimit = getRuntime(entity).resolveDouble(jumpExpr)
        val minSpeed = getRuntime(entity).resolveDouble(minSpeed)
        val speed = state.rideVel.length()

        //Give no altitude limit if at max jump stat.
        val pushingHeightLimit = if(getRuntime(entity).resolveBoolean(infiniteStamina)) false
                                 else (entity.y >= altitudeLimit && entity.xRot <= 0)


        //speed up and slow down based on input
        if( driver.zza > 0.0 && speed < topSpeed && state.stamina > 0.0f && !pushingHeightLimit) {
            //modify acceleration to be slower when at closer speeds to top speed
            val accelMod = max( -(normalizeSpeed(speed, minSpeed, topSpeed)) + 1, 0.0)
            state.rideVel = Vec3(state.rideVel.x, state.rideVel.y, min( state.rideVel.z + (accel * accelMod) , topSpeed))
        }
        else if( driver.zza >= 0.0 && (state.stamina == 0.0f || pushingHeightLimit) ) {
            state.rideVel = Vec3(state.rideVel.x, state.rideVel.y, max( state.rideVel.z - ((accel) / 4) , minSpeed))
        }
        else if ( driver.zza < 0.0 && speed > minSpeed) {
            //modify deccel to be slower when at closer speeds to minimum speed
            val deccelMod = max( (normalizeSpeed(speed, minSpeed, topSpeed) - 1).pow(2), 0.1)

            //Decelerate currently always a constant half of max acceleration.
            state.rideVel = Vec3(state.rideVel.x, state.rideVel.y, max( state.rideVel.z - ((accel * deccelMod) / 2) , minSpeed))
        }
    }

    //TODO: Move these functions to a riding util class.
    /*
    *  Normalizes the current speed between minSpeed and maxSpeed.
    *  The result is clamped between 0.0 and 1.0, where 0.0 represents minSpeed and 1.0 represents maxSpeed.
    */
    fun normalizeSpeed(currSpeed: Double, minSpeed: Double, maxSpeed: Double): Double {
        require(maxSpeed > minSpeed) { "maxSpeed must be greater than minSpeed" }
        return ((currSpeed - minSpeed) / (maxSpeed - minSpeed)).coerceIn(0.0, 1.0)
    }

    /*
    *  Creates a deadzone around zero that extends out to deadzoneLow in both +- and then remaps
    *  the value linearly between -1.0 and 1.0 outside of the deadzone
    */
    fun remapWithDeadzone(value: Double, deadzoneLow: Double = 0.2, deadzoneHigh: Double = 1.0): Double {
        // If the absolute value is below the deadzone, return 0.
        if (kotlin.math.abs(value) < deadzoneLow) return 0.0
        // Otherwise, subtract the deadzone and remap the remaining range to 0-1 (preserving the sign).
        val sign = kotlin.math.sign(value)
        val adjusted = (kotlin.math.abs(value) - deadzoneLow) / (deadzoneHigh - deadzoneLow)
        //Attach the sign back to the vale
        return adjusted * sign
    }


}
