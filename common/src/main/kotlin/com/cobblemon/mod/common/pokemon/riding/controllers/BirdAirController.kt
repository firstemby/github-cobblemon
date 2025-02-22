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
import com.cobblemon.mod.common.pokemon.riding.controllers.JetAirController.Companion.MAXTOPSPEED
import com.cobblemon.mod.common.pokemon.riding.controllers.JetAirController.Companion.MINSPEED
import com.cobblemon.mod.common.util.*
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.util.SmoothDouble
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import kotlin.math.*

class BirdAirController : RideController {
    override val key = KEY
    override val poseProvider = PoseProvider(PoseType.HOVER)
        .with(PoseOption(PoseType.GLIDE) {
            val player = it.passengers.firstOrNull() as? Player ?: return@PoseOption false
            player.zza == 0.0f && it.deltaMovement.length() > 0.3 })
        .with(PoseOption(PoseType.FLY) {
            val player = it.passengers.firstOrNull() as? Player ?: return@PoseOption false
            player.zza > 0.0f // Checks if the player is actively controlling movement // Checks if the player is actively controlling movement
        })

        //.with(PoseOption(PoseType.FLY) { entity ->
        //    val player = entity.passengers.firstOrNull() as? Player ?: false
        //    player.// This is the `zza` value })
        //.with(PoseOption(PoseType.GLIDE) { it.deltaMovement.length() > 0.51 })
        //.with(PoseOption(PoseType.FLY) { it.deltaMovement.length() > 0.1 && it.deltaMovement.length() <= 0.51 })

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

    var currSpeed = 0.0
        private set

    var lastPitch = 0.0f
        private set

    //This function might just be good for syncing the ridecontroller speed
    //with the entity speed?
    override fun speed(entity: PokemonEntity, driver: Player): Float {
        //Set the speed since this will need to be done to transfer speed between rides?
        //maxSpeed = entity.speed.toDouble()


        //retrieve stats
        val topSpeed = entity.getRideStat( RidingStat.SPEED, RidingStyle.AIR, MINTOPSPEED, MAXTOPSPEED )
        val accel = entity.getRideStat( RidingStat.ACCELERATION, RidingStyle.AIR, MINACCEL, MAXACCEL )

        val maxGlideSpeed = topSpeed * 2
        var glideSpeedChange = 0.00

        //speed up and slow down based on input
        if( driver.zza > 0.0 && currSpeed < topSpeed)
        {
            currSpeed = min( currSpeed + accel , topSpeed)
        }
        else if ( driver.zza < 0.0 && currSpeed > 0.0)
        {
            //Decelerate is now always a constant half of max acceleration.
            currSpeed = max( currSpeed - (MAXACCEL / 2) , 0.0)
        }

        val rollable = driver as? Rollable
        if ( rollable != null )
        {
            glideSpeedChange = sin(Math.toRadians( rollable.pitch.toDouble() ))
            glideSpeedChange = glideSpeedChange.pow(3)

            //TODO: Possibly create a deadzone around parallel where speed isn't taken away from?
            if( glideSpeedChange <= 0.0 )
            {
                //Ensures that a propelling force is still able to be applied when
                //climbing in height
                if( driver.zza <= 0 )
                {
                    //speed decrease should be 2x speed increase?
                    currSpeed = max( currSpeed  + (MAXACCEL) * glideSpeedChange , 0.0 )
                }
            }
            else
            {
                // only add to the speed if it hasn't exceeded the current
                //glide angles maximum amount of speed that it can give.
                currSpeed = min( currSpeed  + ((MAXACCEL * 2) * glideSpeedChange) , topSpeed * 2)
            }
        }

        //air resistance
        currSpeed = max( currSpeed - 0.005, 0.0)

        println(currSpeed)
        return 0.0f
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


            //yVel = sin(Math.toRadians(rollable.pitch.toDouble() - lastPitch ))*-10



            lastPitch = rollable.pitch

            //Need to deadzone this when straight up or down
            leftForce = -1.0 * sin(Math.toRadians( rollable.roll.toDouble() ) )

            upForce = -1.0 * sin(Math.toRadians( rollable.pitch.toDouble() ))

            forwardForce = cos(Math.toRadians( rollable.pitch.toDouble() ))
        }

        //TODO: Add in an upwards and downwards force when pressing space maybe?

        //Leave out tap to flap right now
        /*
        var jumpVector = Vec3.ZERO
        if (entity.jumpInputStrength > 0) {
            val runtime = getRuntime(entity)
            runtime.environment.query.addFunction("jump_strength") { DoubleValue(entity.jumpInputStrength.toDouble()) }
            val jumpForces = this.jumpVector.map { runtime.resolveFloat(it) }
            jumpVector = Vec3(jumpForces[0].toDouble(), jumpForces[1].toDouble(), jumpForces[2].toDouble())
            jumpVector = jumpVector.toVector3f().mul((driver as Rollable).orientation).toVec3d()
            entity.jumpInputStrength = 0
            entity.addDeltaMovement(jumpVector)
        }
        */

        val velocity = Vec3(0.0 , upForce * currSpeed, forwardForce * currSpeed)

        return velocity
    }

    override fun angRollVel(entity: PokemonEntity, driver: Player, deltaTime: Double): Vec3 {

        val rollable = driver as? Rollable


        val handling = entity.getRideStat( RidingStat.SKILL, RidingStyle.AIR, MINHANDLING, MAXHANDLING)
        val rotationChangeRate = 10.0

        if( rollable != null )
        {
            var yawForce =  rotationChangeRate * sin( Math.toRadians( rollable.roll.toDouble() ))
            //for a bit of correction on the rolls limit it to a quarter the amount
            var pitchForce = -0.25 * rotationChangeRate * abs(sin( Math.toRadians( rollable.roll.toDouble() )))

            //limit rotation modulation when pitched up heavily or pitched down heavily
            yawForce *= abs(cos( Math.toRadians( rollable.pitch.toDouble() )))
            pitchForce *= abs(cos( Math.toRadians( rollable.pitch.toDouble() ))) * 1.5

            //Create a deadzone for easier leveling out
            if( rollable.roll <= 0.0 && rollable.roll >= -0.0)
            {
                yawForce = 0.0
                pitchForce = 0.0
            }
            return Vec3( yawForce, pitchForce, 0.0)
        }

        /*
        val rotLimit = 15.0

        var rollForce = -1.0 * driver.xxa * rotationChangeRate

        //TODO: Fix accumulated movement causing pitch and roll out of set bounds
        if( rollable != null ) {
            //If the roll or pitch have exceeded the limit then do not modulate that
            //rotation in that direction further

            val rotLim = 30.0

            rollForce *= 1.0.pow(-1.0 * (abs(rollable.roll.toDouble()) / rotLimit))


            if( rollable.roll >= rotLimit && rollForce > 0.0)
            {
                rollForce = 0.0;
            }
            if( rollable.roll <= -rotLimit && rollForce < 0.0)
            {
                rollForce = 0.0;
            }

            //Ignore x,y,z its angular velocity:
            //yaw, pitch, roll
            return Vec3( 0.0, 0.0, rollForce)
        }


         */

        return Vec3(0.0, 0.0, 0.0)
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

        val rollable = driver as Rollable

        val handling = entity.getRideStat( RidingStat.SKILL, RidingStyle.AIR, MINHANDLING, MAXHANDLING)

        //var yMouse = yMouseSmoother.getNewDeltaValue(angVecMouse.y * 0.15f, d);
        //var xMouse = xMouseSmoother.getNewDeltaValue(cursorDeltaX * 0.15f, d);

        var xInput = xMouseSmoother.getNewDeltaValue(xMouse * 0.1, deltaTime * 4);
        val YInput = yMouseSmoother.getNewDeltaValue(yMouse * 0.1, deltaTime * 4);

        //limit rolling based on healing
        val rotLimit = handling * 1.5

        if( abs( rollable.roll + xInput ) < rotLimit )
        {
            if( sign(xInput) == sign( rollable.roll ).toDouble() ) {
                val s = sign(rollable.roll)
                val d = abs(abs(rollable.roll) - rotLimit)
                xInput *= (d.pow(2)) / (rotLimit.pow(2))
            }
        }

        //if( abs(rollable.roll) > rotLimit  )
        else
        {
            val s = sign(rollable.roll)
            val d = abs(rollable.roll) - rotLimit
            if( sign(xInput) == sign( rollable.roll ).toDouble() ) {
                xInput = 0.0
            }
        }

        /*
        if( xMouse > 0 )
        {
            if( rollable.roll > rotLimit  )
            {
                //val adjustedX =  0.5* xMouse * ( (rotLimit - rollable.roll) / ( rotLimit) )
                return Vec3(0.0, YInput, 0.0 )

            }
            else
            {
                return Vec3(0.0, YInput, xInput * -0.1 )
            }

        }else{
            if( rollable.roll < -1.0 * rotLimit  )
            {
                //val adjustedX = 0.5* xMouse * ( (rotLimit + rollable.roll) / ( rotLimit) )
                return Vec3(0.0, YInput, 0.0 )

            }
            else
            {
                return Vec3(0.0, YInput, xInput * -0.1 )
            }

        }
         */
        //Ignore x,y,z its angular velocity:
        //yaw, pitch, roll
        return Vec3(0.0, YInput,   xInput )
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
        return 0.0
    }

    override fun shouldRoll(entity: PokemonEntity) = true

    override fun useAngVelSmoothing(entity: PokemonEntity): Boolean = false

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        super.encode(buffer)
        buffer.writeString(gravity.toString())
        buffer.writeString(horizontalAcceleration.toString())
        buffer.writeString(verticalAcceleration.toString())
        buffer.writeString(speed.toString())
        buffer.writeDouble(this.lastVelocity.x)
        buffer.writeDouble(this.lastVelocity.y)
        buffer.writeDouble(this.lastVelocity.z)
    }

    override fun decode(buffer: RegistryFriendlyByteBuf) {
        gravity = buffer.readString().asExpression()
        horizontalAcceleration = buffer.readString().asExpression()
        verticalAcceleration = buffer.readString().asExpression()
        speed = buffer.readString().asExpression()
        val x = buffer.readDouble()
        val y = buffer.readDouble()
        val z = buffer.readDouble()
        this.lastVelocity = Vec3(x, y, z)
    }


    companion object {
        val KEY = cobblemonResource("air/bird")

        val MAXTOPSPEED = 1.0 // 20 bl/s
        val MINTOPSPEED = 0.35 // 7 bl/s
        //val MINSPEED = 0.25 // 5 bl/s

        //Accel will lie between 1.0 second and 5.0 seconds
        val MAXACCEL = (MAXTOPSPEED ) / (20*3) //3.0 second to max speed
        val MINACCEL = (MAXTOPSPEED ) / (20*8) // 8 seconds to max speed

        //Can rotate 90 degrees
        val MAXHANDLING = 90.0
        val MINHANDLING = 30.0


        val MAXYAWHANDLING = 16.0
        val MINYAWHANDLING = 8.0
    }
}