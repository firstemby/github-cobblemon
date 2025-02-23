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
    var speed: Expression = "1.0".asExpression()
        private set
    var lastVelocity = Vec3(0.0,0.0,0.0)
        private set
    var maxSpeed = 0.4
        private set

    var currSpeed = MINSPEED
        private set

    var currMouseXForce = 0.0
        private set

    var currMouseYForce = 0.0
        private set

    override fun speed(entity: PokemonEntity, driver: Player): Float {

        //Gather needed stats
        val topSpeed = entity.getRideStat( RidingStat.SPEED, RidingStyle.AIR, MINTOPSPEED, MAXTOPSPEED )
        val accel = entity.getRideStat( RidingStat.ACCELERATION, RidingStyle.AIR, MINACCEL, MAXACCEL )


        //speed up and slow down based on input
        if( driver.zza > 0.0 && currSpeed < topSpeed)
        {
            currSpeed = min( currSpeed + accel , topSpeed)
        }
        else if ( driver.zza < 0.0 && currSpeed > MINSPEED)
        {
            //Decelerate currently always a constant half of max acceleration.
            currSpeed = max( currSpeed - (MAXACCEL / 2) , MINSPEED)
        }

        //Returning is redundant as the logic is not connected currently
        return currSpeed.toFloat()
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
        val velocity = Vec3(0.0 , (upForce)* currSpeed, forwardForce * currSpeed)

        return velocity
    }

    override fun useAngVelSmoothing(entity: PokemonEntity): Boolean = true

    override fun angRollVel(entity: PokemonEntity, driver: Player, deltaTime: Double): Vec3 {

        //Get handling in degrees per second
        val yawRotRate = entity.getRideStat( RidingStat.SKILL, RidingStyle.AIR, MINYAWHANDLING, MAXYAWHANDLING)

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

        //TODO: figure out a cleaner solution to this issue

        //Cap at a rate of 5fps so frame skips dont lead to huge jumps
        val cappedDeltaTime = min( deltaTime, 0.2)


        // Accumulate the mouse input
        currMouseXForce = (currMouseXForce + ( 0.0015 * xMouse )).coerceIn(-1.0, 1.0)
        currMouseYForce = (currMouseYForce + ( 0.0015 * yMouse )).coerceIn(-1.0, 1.0)

        //Get handling in degrees per second
        val handlingStat = entity.rideProp.calculate(RidingStat.SKILL, RidingStyle.AIR, 0 )
        var handling = (((MAXHANDLING - MINHANDLING) / 100) * handlingStat) + MINHANDLING

        //convert it to partial ticks
        handling *= (deltaTime)

        val poke = driver.vehicle as? PokemonEntity

        //TODO: reevaluate if deadzones are needed and if they are still causing issues.
        //create deadzones for the constant input values.
        val xInput = remapWithDeadzone(currMouseXForce, 0.025, 1.0)
        val yInput = remapWithDeadzone(currMouseYForce, 0.025, 1.0)

        val pitchRot = handling * currMouseYForce
        val rollRot =  handling * 1.5 * currMouseXForce

        //yaw, pitch, roll
        return Vec3(0.0, pitchRot,  rollRot)
    }

    override fun fuelBar(entity: PokemonEntity, driver: Player): Float {
        return 0.5f
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

        if( maxSpeed <= 0.5 ) grav = 0.5 - maxSpeed

        return grav
    }

    override fun shouldRoll(entity: PokemonEntity) = true

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        super.encode(buffer)
        buffer.writeString(gravity.toString())
        buffer.writeString(horizontalAcceleration.toString())
        buffer.writeString(verticalAcceleration.toString())
        buffer.writeString(speed.toString())
        buffer.writeDouble(maxSpeed)
        buffer.writeDouble(this.lastVelocity.x)
        buffer.writeDouble(this.lastVelocity.y)
        buffer.writeDouble(this.lastVelocity.z)
    }

    override fun decode(buffer: RegistryFriendlyByteBuf) {
        gravity = buffer.readString().asExpression()
        horizontalAcceleration = buffer.readString().asExpression()
        verticalAcceleration = buffer.readString().asExpression()
        speed = buffer.readString().asExpression()
        this.maxSpeed = buffer.readDouble()
        val x = buffer.readDouble()
        val y = buffer.readDouble()
        val z = buffer.readDouble()
        this.lastVelocity = Vec3(x, y, z)
    }


    companion object {
        val KEY = cobblemonResource("air/jet")

        //TODO: ensure there is consensus on these values

        val MAXTOPSPEED = 2.5 // 50 bl/s
        val MINTOPSPEED = 0.35 // 7 bl/s
        val MINSPEED = 0.25 // 5 bl/s

        //Accel currently lies between 1.0 second and 5.0 seconds
        val MAXACCEL = (MAXTOPSPEED - MINSPEED) / 20 //1.0 second to max speed
        val MINACCEL = (MAXTOPSPEED - MINSPEED) / (20*5) // 5 seconds to max speed

        //Degrees of rotation per second
        val MAXHANDLING = 140.0
        val MINHANDLING = 20.0

        //Degrees of rotation per second of yaw
        val MAXYAWHANDLING = 16.0
        val MINYAWHANDLING = 8.0

        //TODO: add feul/stamina

        //Fuel measured by seconds at full throttle.
        //minimum speed will consume a quarter of the fuel.
        //val MAXSTAMINA =
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