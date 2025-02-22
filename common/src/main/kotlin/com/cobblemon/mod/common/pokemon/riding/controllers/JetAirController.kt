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
import com.cobblemon.mod.common.pokemon.riding.controllers.HelicopterAirController.Companion.ROTATION_LIMIT
import com.cobblemon.mod.common.util.*
import net.minecraft.client.Minecraft
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

    var lastPitch = 0.0f
        private set

    var currSpeed = MINSPEED
        private set

    var currMouseXForce = 0.0
        private set

    var currMouseYForce = 0.0
        private set

    override fun speed(entity: PokemonEntity, driver: Player): Float {
        //0 boost so just the base value.
        //This should be a function in pokemonEntity.kt so it can easily be called.
        val speedStat = entity.rideProp.calculate(RidingStat.SPEED, RidingStyle.AIR, 0 )
        //now calculate the true top  speed value using the speed stat
        val topSpeed = (((MAXTOPSPEED - MINTOPSPEED) / 100) * speedStat) + MINTOPSPEED

        val accelStat = entity.rideProp.calculate(RidingStat.ACCELERATION, RidingStyle.AIR, 0 )
        val accel = (((MAXACCEL - MINACCEL) / 100) * accelStat) + MINACCEL

        val drivingForce = if ( Minecraft.getInstance().options.keySprint.isDown() ) 1.0
        else if (driver.isShiftKeyDown) -1.0
        else(0.0)

        //speed up and slow down based on input
        if( driver.zza > 0.0 && currSpeed < topSpeed)
        {
            currSpeed = min( currSpeed + accel , topSpeed)
        }
        else if ( driver.zza < 0.0 && currSpeed > MINSPEED)
        {
            //Decelerate is now always a constant half of max acceleration.
            currSpeed = max( currSpeed - (MAXACCEL / 2) , MINSPEED)
        }

        return currSpeed.toFloat()
    }

    override fun rotation(entity: PokemonEntity, driver: LivingEntity): Vec2 {
        return Vec2(driver.xRot * 0.5f, driver.yRot)
    }

    override fun velocity(entity: PokemonEntity, driver: Player, input: Vec3): Vec3 {

        var yVel = 0.0
        var leftForce = 0.0
        var upForce = 0.0
        var forwardForce = 0.0
        val rollable = driver as? Rollable
        if( rollable != null ) {


            yVel = sin(Math.toRadians(rollable.pitch.toDouble() - lastPitch ))*-10

            lastPitch = rollable.pitch

            leftForce = -1.0  * sin(Math.toRadians( rollable.roll.toDouble() ) )

            upForce =  -1.0 * sin(Math.toRadians( rollable.pitch.toDouble() ))
            //limit how fast you can descend?
            upForce *= 1.0
            forwardForce =  cos(Math.toRadians( rollable.pitch.toDouble() ))
        }
        val velocity = Vec3(0.0 , (upForce)* currSpeed, forwardForce * currSpeed)

        //entity.setDeltaMovement(velocity)

        return velocity
    }

    override fun useAngVelSmoothing(entity: PokemonEntity): Boolean = true

    override fun angRollVel(entity: PokemonEntity, driver: Player, deltaTime: Double): Vec3 {

        //Get handling in degrees per second
        val handlingStat = entity.rideProp.calculate(RidingStat.SKILL, RidingStyle.AIR, 0 )
        //var handling = (((MAXHANDLING - MINHANDLING) / 100) * handlingStat) + MINHANDLING
        var handlingYaw = (((MAXYAWHANDLING - MINYAWHANDLING) / 100) * handlingStat) + MINYAWHANDLING

        //convert it to partial ticks
        handlingYaw *= (deltaTime)

        //A+D to yaw
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



        //val pitchRot = min( handling, max(-handling,  yMouse ) )

        //increase on an amount.

        //if its below 0.2 it should be zero force.
        // And then between 0.2-1.0 it should map to a value between 0.0 and 1.0
        //val yInput = if(currMouseYForce < 0.2)  0.0 else ( ( currMouseYForce - 0.2 ) / 0.8)
        //val xInput = if(currMouseXForce < 0.2)  0.0 else ( ( currMouseXForce - 0.2 ) / 0.8)

        //create deadzones for the constant input values.
        val xInput = remapWithDeadzone(currMouseXForce, 0.025, 1.0)
        val yInput = remapWithDeadzone(currMouseYForce, 0.025, 1.0)

        val pitchRot = handling * currMouseYForce
        //double roll
        val rollRot =  handling * 1.5 * currMouseXForce

        //mult by 2 so you can roll twice as fast as you can pitch.
        //val rollRot = min( handling*2, max(-handling*2,  xMouse ) )

        //Ignore x,y,z its angular velocity:
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

    //Going to use my own gravity calculations for now.
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
        //I need a top and a bottom for the speed stat
        // I need a minimum speed stat for this ride specifically so that it caps
        // at this or slows below it and falls
        // 32 bl/s and 8 bl/s
        // min is 4 bl/s
        val MAXTOPSPEED = 2.5 // 50 bl/s
        val MINTOPSPEED = 0.35 // 7 bl/s
        val MINSPEED = 0.25 // 5 bl/s

        //Accel will lie between 1.0 second and 5.0 seconds
        val MAXACCEL = (MAXTOPSPEED - MINSPEED) / 20 //1.0 second to max speed
        val MINACCEL = (MAXTOPSPEED - MINSPEED) / (20*5) // 5 seconds to max speed

        //full 180 in 1 second.
        //30 degrees in 1 second.
        val MAXHANDLING = 140.0
        val MINHANDLING = 20.0


        val MAXYAWHANDLING = 16.0
        val MINYAWHANDLING = 8.0

        //Fuel measured by seconds at full throttle.
        //minimum speed will consume a quarter of the fuel.
        //val MAXSTAMINA =
    }

    //helper functions

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