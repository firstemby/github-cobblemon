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
import com.cobblemon.mod.common.api.riding.controller.RideController
import com.cobblemon.mod.common.api.riding.controller.posing.PoseOption
import com.cobblemon.mod.common.api.riding.controller.posing.PoseProvider
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.riding.controllers.HelicopterAirController.Companion.ROTATION_LIMIT
import com.cobblemon.mod.common.util.*
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import kotlin.math.*

class BirdAirController : RideController {
    override val key = KEY
    override val poseProvider = PoseProvider(PoseType.HOVER)
        .with(PoseOption(PoseType.GLIDE) { it.deltaMovement.length() > 0.51 })
        .with(PoseOption(PoseType.FLY) { it.deltaMovement.length() > 0.1 && it.deltaMovement.length() <= 0.51 })

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
    var maxSpeed = 0.2
        private set

    var lastPitch = 0.0f
        private set

    //This function might just be good for syncing the ridecontroller speed
    //with the entity speed?
    override fun speed(entity: PokemonEntity, driver: Player): Float {
        //Set the speed since this will need to be done to transfer speed between rides?
        //maxSpeed = entity.speed.toDouble()

        var drivingForce = driver.zza
        val maxPropelSpeed = 0.5
        var maxGlideSpeed = 1.5
        var glideSpeedChange = 0.01
        //speed up and slow down based on input
        if( drivingForce > 0 && maxSpeed < maxPropelSpeed)
        {
            if( maxSpeed <= 0.1 ){ maxSpeed = 0.1}
            maxSpeed += 0.01
        }
        else if ( drivingForce <  0)
        {
            maxSpeed = max( maxSpeed -0.01 , 0.0)
        }

        val rollable = driver as? Rollable
        if ( rollable != null )
        {
            //maxGlideSpeed *= abs(sin(Math.toRadians( rollable.pitch.toDouble() )))
            //Larger speed change the larger the pitch is.
            glideSpeedChange = sin(Math.toRadians( rollable.pitch.toDouble() ))
            glideSpeedChange *= glideSpeedChange * glideSpeedChange

            //TODO: Possibly create a deadzone around parallel where speed isn't taken away from?
            if( glideSpeedChange <= 0.0 )
            {
                //Ensures that a propelling force is still able to be applied when
                //climbing in height
                if( drivingForce <= 0 )
                {
                    //speed decrease should be 2x speed increase?
                    maxSpeed = max( maxSpeed  + 0.04* glideSpeedChange , 0.0)
                }
            }
            else
            {
                // only add to the speed if it hasn't exceeded the current
                //glide angles maximum amount of speed that it can give.
                if( maxSpeed < maxGlideSpeed )
                {
                    if( maxSpeed < 0.1 ){ maxSpeed = 0.2}
                    maxSpeed = min( maxSpeed  + 0.02 * glideSpeedChange , maxGlideSpeed)
                }
            }
        }

        //air resistance
        maxSpeed -= 0.002

        println(maxSpeed)
        return 0.0f
    }

    override fun rotation(entity: PokemonEntity, driver: LivingEntity): Vec2 {
        return Vec2(driver.xRot * 0.5f, driver.yRot)
    }

    override fun velocity(entity: PokemonEntity, driver: Player, input: Vec3): Vec3 {
        //var yVel = if (driver.jumping) 1.0
        //else if (driver.isShiftKeyDown) -1.0
        // (0.0)




        var drivingForce = driver.zza
        //if (drivingForce < 0.0f) {
        //    drivingForce = -0.25f
        //}
        drivingForce = 1.0f
        {
            //Always push forward
            drivingForce = 1.0f
        }

        /*
        if (!entity.isInWater && !entity.isUnderWater)
        {
            lastVelocity = Vec3(lastVelocity.x, -1.0, lastVelocity.z)
            return lastVelocity
        }
         */
        var yVel = 0.0
        var leftForce = 0.0
        var upForce = 0.0
        var forwardForce = 0.0
        val rollable = driver as? Rollable
        if( rollable != null ) {


            //yVel = sin(Math.toRadians(rollable.pitch.toDouble() - lastPitch ))*-10



            lastPitch = rollable.pitch

            //Need to deadzone this when straight up or down
            leftForce = -1.0 * drivingForce * sin(Math.toRadians( rollable.roll.toDouble() ) )

            upForce = drivingForce * -1.0 * sin(Math.toRadians( rollable.pitch.toDouble() ))
            //limit how fast you can descend?
            upForce *= 1.0
            forwardForce = drivingForce * cos(Math.toRadians( rollable.pitch.toDouble() ))
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
        val velocity = Vec3(0.0 , (upForce) * maxSpeed, forwardForce * maxSpeed)

        //entity.setDeltaMovement(velocity)

        return velocity
    }

    override fun angRollVel(entity: PokemonEntity, driver: Player): Vec3 {

        val rollable = driver as? Rollable

        //In degrees per second? It was supposed to be I think but
        //I have messed something up

        val rotationChangeRate = 6.0
        if( rollable != null ) {

            var yawForce =  rotationChangeRate * sin( Math.toRadians( rollable.roll.toDouble() ))
            //for a bit of correction on the rolls limit it to a quarter the amount
            var pitchForce = -0.25 * rotationChangeRate * abs(sin( Math.toRadians( rollable.roll.toDouble() )))

            //limit rotation modulation when pitched up heavily or pitched down heavily
            yawForce *= abs(cos( Math.toRadians( rollable.pitch.toDouble() )))
            pitchForce *= abs(cos( Math.toRadians( rollable.pitch.toDouble() )))

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

    override fun rotationOnMouseXY( entity: PokemonEntity, driver: Player, yMouse: Double, xMouse: Double ){
        if(driver !is Rollable) return

        val rollable = driver as Rollable

        val rotLimit = 45.0
        val nextRollRot = rollable.roll + xMouse.toFloat()
        if( xMouse > 0 )
        {
            if( rollable.roll > rotLimit  )
            {
                val adjustedX =  0.5* xMouse * ( (rotLimit - rollable.roll) / ( rotLimit) )
                rollable.rotate(0.0f, yMouse.toFloat(), adjustedX.toFloat() )
                return
            }

        }else{
            if( rollable.roll < -1.0 * rotLimit  )
            {
                val adjustedX = 0.5* xMouse * ( (rotLimit + rollable.roll) / ( rotLimit) )
                rollable.rotate(0.0f, yMouse.toFloat(), adjustedX.toFloat() )
                return
            }

        }
        //Ignore x,y,z its angular velocity:
        //yaw, pitch, roll
        rollable.rotate(0.0f, yMouse.toFloat(),  0.5f * xMouse.toFloat() )
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
        val KEY = cobblemonResource("air/bird")
    }
}