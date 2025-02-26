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
import com.cobblemon.mod.common.Rollable
import com.cobblemon.mod.common.api.riding.RidingStyle
import com.cobblemon.mod.common.api.riding.controller.RideController
import com.cobblemon.mod.common.api.riding.controller.posing.PoseOption
import com.cobblemon.mod.common.api.riding.controller.posing.PoseProvider
import com.cobblemon.mod.common.api.riding.stats.RidingStat
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

    var jumpVector = listOf("0".asExpression(), "0.3".asExpression(), "0".asExpression())
        private set
    var gravity: Expression = "0".asExpression()
        private set
    var horizontalAcceleration: Expression = "0.1".asExpression()
        private set
    var verticalAcceleration: Expression = "0.1".asExpression()
        private set

    //5 bl/s. Minimum speed so long as jets have no gravity.
    var minSpeed: Expression = "0.8".asExpression()
        private set
    var handlingExpr: Expression = "(q.get_ride_stats('SKILL', 'AIR') / 100) * (140.0 - 20.0) + 20.0".asExpression()
        private set
    var handlingYawExpr: Expression = "(q.get_ride_stats('SKILL', 'AIR') / 100) * (16.0 - 8.0) + 20.0".asExpression()
        private set
    var topSpeedExpr: Expression = "(q.get_ride_stats('SPEED', 'AIR') / 100) * (2.5 - 1.0) + 1.0".asExpression()
        private set
    //Same equation as above except the minimum accel is 8 seconds from the minimum accel to 1 second.
    //This likely needs simplified to probably just the amount of seconds to reach max from zero?
    var accelExpr: Expression = ("(q.get_ride_stats('ACCELERATION', 'AIR') / 100.0) * " +
            "( ((2.5 - 0.35) / 20)  - ((2.5 - 0.35) / (20 * 5))) + ((2.5 - 0.35) / (20 * 5))").asExpression()
        private set

    override fun speed(entity: PokemonEntity, driver: Player): Float {

        //retrieve stats
        val topSpeed = getRuntime(entity).resolveDouble(topSpeedExpr)
        val accel = getRuntime(entity).resolveDouble(accelExpr)

        //retrieve state
        val state = getState(entity, ::JetAirState)

        //retrieve minSpeed
        val mSpeed = getRuntime(entity).resolveDouble(minSpeed)

        //speed up and slow down based on input
        if( driver.zza > 0.0 && state.currSpeed < topSpeed)
        {
            state.currSpeed = min( state.currSpeed + accel , topSpeed)
        }
        else if ( driver.zza < 0.0 && state.currSpeed > getRuntime(entity).resolveDouble(minSpeed))
        {
            //Decelerate currently always a constant half of max acceleration.
            state.currSpeed = max( state.currSpeed - (((2.5 - 0.35) / 20) / 2) , 1.2)
        }

        //Returning is redundant as the logic is not connected currently
        return state.currSpeed.toFloat()
    }

    override fun rotation(entity: PokemonEntity, driver: LivingEntity): Vec2 {
        return Vec2(driver.xRot * 0.5f, driver.yRot)
    }

    override fun velocity(entity: PokemonEntity, driver: Player, input: Vec3): Vec3 {

        var upForce = 0.0
        var forwardForce = 0.0
        val rollable = driver as? Rollable

        if( rollable != null ) {

            upForce =  -1.0 * sin(Math.toRadians( rollable.pitch.toDouble() ))

            forwardForce =  cos(Math.toRadians( rollable.pitch.toDouble() ))

        }

        //Gather state information
        val state = getState(entity, ::JetAirState)

        val velocity = Vec3(0.0 , (upForce)* state.currSpeed, forwardForce * state.currSpeed)

        return velocity
    }

    override fun useAngVelSmoothing(entity: PokemonEntity): Boolean = true

    override fun angRollVel(entity: PokemonEntity, driver: Player, deltaTime: Double): Vec3 {

        //Get handling in degrees per second
        val yawRotRate = getRuntime(entity).resolveDouble(handlingYawExpr)

        //Base the change off of deltatime.
        val handlingYaw = yawRotRate * (deltaTime)

        //A+D to yaw
        //TODO: Currently doubled, check if this makes the most sense to have it this high.
        val yawForce = -2.0 * driver.xxa * handlingYaw

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

        // Accumulate the mouse input
        state.currMouseXForce = (state.currMouseXForce + ( 0.0015 * xMouse )).coerceIn(-1.0, 1.0)
        state.currMouseYForce = (state.currMouseYForce + ( 0.0015 * yMouse )).coerceIn(-1.0, 1.0)

        //Get handling in degrees per second
        var handling = getRuntime(entity).resolveDouble(handlingExpr)

        //convert it to partial ticks
        handling *= (deltaTime)

        val poke = driver.vehicle as? PokemonEntity

        //TODO: reevaluate if deadzones are needed and if they are still causing issues.
        //create deadzones for the constant input values.
        val xInput = remapWithDeadzone(state.currMouseXForce, 0.025, 1.0)
        val yInput = remapWithDeadzone(state.currMouseYForce, 0.025, 1.0)

        val pitchRot = handling * state.currMouseYForce
        //Roll is 1.5 times as fast as pitch
        val rollRot =  handling * 1.5 * state.currMouseXForce

        //yaw, pitch, roll
        return Vec3(0.0, pitchRot,  rollRot)
    }

    //TODO: bring in stamina stats
    override fun setRideBar(entity: PokemonEntity, driver: Player): Float {
        val topSpeed = getRuntime(entity).resolveDouble(topSpeedExpr)
        val mSpeed = getRuntime(entity).resolveDouble(minSpeed)

        //Retrieve stamina from state and tick down based on current speed vs top speed.
        val state = getState(entity, ::JetAirState)
        //For now 1.0 is a temp min speed.
        //At max speed it will tick down 0.1 a second so the stamina will last ten seconds
        //There has got to be a better way to express this equation. It interpolates between 0.5 and 1.0
        val staminaLoss = ((( state.currSpeed - 1.2 ) / ( topSpeed - 1.2 )) / 2 + 0.5) * ( ( 1.0 / 20.0 ) * 0.1)
        state.stamina = max(state.stamina - staminaLoss,0.0).toFloat()
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

        var grav = 0.0

        //TODO: create a better gravity equation.
        //if( maxSpeed <= 0.5 ) grav = 0.5 - maxSpeed

        return grav
    }

    override fun shouldRoll(entity: PokemonEntity) = true

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        super.encode(buffer)
        buffer.writeString(gravity.toString())
        buffer.writeString(horizontalAcceleration.toString())
        buffer.writeString(verticalAcceleration.toString())
        buffer.writeString(minSpeed.toString())
        buffer.writeString(handlingExpr.toString())
        buffer.writeString(handlingYawExpr.toString())
        buffer.writeString(topSpeedExpr.toString())
        buffer.writeString(accelExpr.toString())
    }

    override fun decode(buffer: RegistryFriendlyByteBuf) {
        gravity = buffer.readString().asExpression()
        horizontalAcceleration = buffer.readString().asExpression()
        verticalAcceleration = buffer.readString().asExpression()
        minSpeed = buffer.readString().asExpression()
        handlingExpr = buffer.readString().asExpression()
        handlingYawExpr = buffer.readString().asExpression()
        topSpeedExpr = buffer.readString().asExpression()
        accelExpr = buffer.readString().asExpression()
    }

    companion object {
        val KEY = cobblemonResource("air/jet")
    }

    //This likely needs moved to somewhere that all ride contollers can share it. Maybe a ride utilities file?
    //Creates a deadzone around zero that extends out to deadzoneLow in both +- and then remaps
    //the value linearly between -1.0 and 1.0 outside of the deadzone
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