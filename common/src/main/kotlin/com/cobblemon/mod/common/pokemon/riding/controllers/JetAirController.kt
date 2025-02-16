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
    var maxSpeed = 0.0
        private set

    var lastPitch = 0.0f
        private set

    override fun speed(entity: PokemonEntity, driver: Player): Float {
        val drivingForce = if (driver.jumping) 1.0
        else if (driver.isShiftKeyDown) -1.0
        else(0.0)
        val maxPropelSpeed = 1.5
        //speed up and slow down based on input
        if( drivingForce > 0 && maxSpeed < maxPropelSpeed)
        {
                 if( maxSpeed <= 0.1 ){ maxSpeed = 0.1}
            maxSpeed *= 1.05
        }
        else if ( drivingForce <  0)
        {
            maxSpeed = max( maxSpeed * 0.90 , 0.0)
        }

        return 1.0f
        //return maxSpeed.toFloat()
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
        val velocity = Vec3(0.0 , (upForce), forwardForce)

        //entity.setDeltaMovement(velocity)

        return velocity
    }

    override fun angRollVel(entity: PokemonEntity, driver: Player): Vec3 {

        //In degrees per second?
        val rotationChangeRate = 6.0

        var pitchForce = driver.zza.toDouble()
        pitchForce *= rotationChangeRate/3

        val yawForce = -1.0 * driver.xxa * rotationChangeRate

        return Vec3( 0.25*yawForce, -1.5*pitchForce, 0.0)
    }

    override fun rotationOnMouseXY( entity: PokemonEntity, driver: Player, yMouse: Double, xMouse: Double ){
        if(driver !is Rollable) return

        val rollable = driver as Rollable

        //Ignore x,y,z its angular velocity:
        //yaw, pitch, roll
        rollable.rotate(0.0f, 0.0f,  xMouse.toFloat() )
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

    override fun useMouseInputRoll(entity: PokemonEntity): Boolean = false

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
    }
}