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
import com.cobblemon.mod.common.pokemon.riding.controllers.JetAirController.Companion
import com.cobblemon.mod.common.util.*
import net.minecraft.client.Minecraft
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import kotlin.math.*

class JetAltAirController : RideController {
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
            //should slowdown be faster or slower than speed up?
            currSpeed = max( currSpeed - (accel / 2) , MINSPEED)
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

    override fun angRollVel(entity: PokemonEntity, driver: Player, deltaTime: Double): Vec3 {

        //Get handling in degrees per second
        val handlingStat = entity.rideProp.calculate(RidingStat.SKILL, RidingStyle.AIR, 0 )
        var handling = (((MAXHANDLING - MINHANDLING) / 100) * handlingStat) + MINHANDLING

        //convert it to partial ticks
        handling *= (deltaTime)

        //Convert to a third of base handling since yaw should be slower
        handling *= 2

        //A+D to yaw
        val rollForce = -2.0 * driver.xxa * handling

        return Vec3( 0.0, 0.0, rollForce)
    }

    override fun  rotationOnMouseXY( entity: PokemonEntity, driver: Player, yMouse: Double, xMouse: Double, deltaTime: Double ): Vec3 {
        if(driver !is Rollable) return Vec3.ZERO

        val rollable = driver as Rollable


        //Get handling in degrees per second
        val handlingStat = entity.rideProp.calculate(RidingStat.SKILL, RidingStyle.AIR, 0 )
        var handling = (((JetAirController.MAXHANDLING - JetAirController.MINHANDLING) / 100) * handlingStat) + JetAirController.MINHANDLING

        //convert it to partial ticks
        handling *= (deltaTime)


        val pitchRot = min( handling, max(-handling,  yMouse ) )

        //Divide by three so you can only yaw a third as fast as you can pitch
        val yawRot = min( handling/2, max(-handling/2,  xMouse ) )

        //Ignore x,y,z its angular velocity:
        //yaw, pitch, roll
        return Vec3(yawRot, pitchRot,  0.0)
    }

    override fun canJump(
        entity: PokemonEntity,
        driver: Player
    ): Boolean {
        return true
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

    override fun useAngVelSmoothing(entity: PokemonEntity): Boolean = true

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
        val KEY = cobblemonResource("air/jetalt")
        //I need a top and a bottom for the speed stat
        // I need a minmum speed stat for this ride specifically so that it caps at this or slows below it and falls
        // 32 bl/s and 8 bl/s
        //min is 4 bl/s
        val MAXTOPSPEED = 1.6
        val MINTOPSPEED = 0.4
        val MINSPEED = 0.2

        //Accel will lie between 1.0 second and 5.0 seconds
        val MAXACCEL = (MAXTOPSPEED - MINTOPSPEED) / 20
        val MINACCEL = (MAXTOPSPEED - MINTOPSPEED) / (20*5)

        //Max to hopefully be something like a full 180 in 1 second.
        val MAXHANDLING = 180.0
        val MINHANDLING = 30.0
    }
}