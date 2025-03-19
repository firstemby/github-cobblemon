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
import com.cobblemon.mod.common.pokemon.riding.states.BirdAirState
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
        .with(PoseOption(PoseType.FLY) {
            val player = it.passengers.firstOrNull() as? Player ?: return@PoseOption false
            it.deltaMovement.length() > 0.3 })
        /*
        .with(PoseOption(PoseType.FLY) {
            val player = it.passengers.firstOrNull() as? Player ?: return@PoseOption false
            player.zza > 0.0f // Checks if the player is actively attempting to move forward
        })
         */

    override val condition: (PokemonEntity) -> Boolean = { true }

    var jumpVector = listOf("0".asExpression(), "0.3".asExpression(), "0".asExpression())
        private set
    var gravity: Expression = "0".asExpression()
        private set
    var horizontalAcceleration: Expression = "0.1".asExpression()
        private set
    var verticalAcceleration: Expression = "0.1".asExpression()
        private set

    // Where 90 is the width of the range and 45 is the offset.
    // So the handling interpolates between 135 and 45
    var handlingExpr: Expression = "(q.get_ride_stats('SKILL', 'AIR') / 100) * 90 + 45".asExpression()
        private set
    var topSpeedExpr: Expression = "(q.get_ride_stats('SPEED', 'AIR') / 100) * (1.0 - 0.35) + 0.35".asExpression()
        private set
    //Reaches max top speed from a standstill at minimum 8 seconds and max 3 seconds.
    //This is a bit of a confusing expression and likely should be cleaned up to be more readable
    var accelExpr: Expression = "(q.get_ride_stats('ACCELERATION', 'AIR') / 100) * (0.01666666666 - 0.00625) + 0.00625".asExpression()
        private set

    override fun speed(entity: PokemonEntity, driver: Player): Float {

        //Retrieve the current bird controller state
        //val state = entity.riding.getState(KEY) { e -> BirdAirControllerState(e) }
        val state = getState(entity, ::BirdAirState)

        //retrieve stats
        val topSpeed = getRuntime(entity).resolveDouble(topSpeedExpr)
        val accel = getRuntime(entity).resolveDouble(accelExpr)

        val maxGlideSpeed = topSpeed * 2
        var glideSpeedChange = 0.00

        //speed up and slow down based on input
        if (driver.zza > 0.0 && state.currSpeed < topSpeed) {
            state.currSpeed = min(state.currSpeed + accel , topSpeed)
        } else if (driver.zza < 0.0 && state.currSpeed > 0.0) {
            //Decelerate is currently always a constant half of max acceleration.
            state.currSpeed = max(state.currSpeed - (0.0166 / 2), 0.0)
        }

        val rollable = driver as? Rollable
        if (rollable != null) {
            glideSpeedChange = sin(Math.toRadians(rollable.pitch.toDouble()))
            glideSpeedChange = glideSpeedChange.pow(3)

            //TODO: Possibly create a deadzone around parallel where speed isn't taken away from?
            if (glideSpeedChange <= 0.0) {
                //Ensures that a propelling force is still able to be applied when
                //climbing in height
                if (driver.zza <= 0) {
                    //speed decrease should be 2x speed increase?
                    state.currSpeed = max(state.currSpeed + (0.0166) * glideSpeedChange, 0.0 )
                }
            } else {
                // only add to the speed if it hasn't exceeded the current
                //glide angles maximum amount of speed that it can give.
                state.currSpeed = min(state.currSpeed + ((0.0166 * 2) * glideSpeedChange), maxGlideSpeed)
            }
        }

        //air resistance
        state.currSpeed = max(state.currSpeed - 0.005, 0.0)

        return state.currSpeed.toFloat()
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
        if (rollable != null) {
            //Need to deadzone this when straight up or down
            // test with and without strafing added. May be wanted while moving
            leftForce = -1.0 * sin(Math.toRadians(rollable.roll.toDouble()))

            upForce = -1.0 * sin(Math.toRadians(rollable.pitch.toDouble()))

            forwardForce = cos(Math.toRadians(rollable.pitch.toDouble()))
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
        //Retrieve the current composite controller state
        val state = getState(entity, ::BirdAirState)

        val velocity = Vec3(0.0 , upForce * state.currSpeed, forwardForce * state.currSpeed)

        return velocity
    }

    override fun angRollVel(entity: PokemonEntity, driver: Player, deltaTime: Double): Vec3 {

        val rollable = driver as? Rollable

        //TODO: Tie in handling
        val handling = getRuntime(entity).resolveDouble(handlingExpr)
        val rotationChangeRate = 10.0

        if (rollable != null) {
            var yawForce =  rotationChangeRate * sin(Math.toRadians(rollable.roll.toDouble()))
            //for a bit of correction on the rolls limit it to a quarter the amount
            var pitchForce = -0.25 * rotationChangeRate * abs(sin(Math.toRadians(rollable.roll.toDouble())))

            //limit rotation modulation when pitched up heavily or pitched down heavily
            yawForce *= abs(cos(Math.toRadians(rollable.pitch.toDouble())))
            pitchForce *= abs(cos(Math.toRadians(rollable.pitch.toDouble()))) * 1.5

            //Create a deadzone for easier leveling out
            if (rollable.roll <= 0.0 && rollable.roll >= -0.0) {
                yawForce = 0.0
                pitchForce = 0.0
            }
            return Vec3(yawForce, pitchForce, 0.0)
        }

        return Vec3(0.0, 0.0, 0.0)
    }

    override fun rotationOnMouseXY(
        entity: PokemonEntity,
        driver: Player,
        yMouse: Double,
        xMouse: Double,
        yMouseSmoother: SmoothDouble,
        xMouseSmoother: SmoothDouble,
        sensitivity: Double,
        deltaTime: Double
    ): Vec3 {
        if (driver !is Rollable) return Vec3.ZERO

        val rollable = driver as Rollable

        val handling = getRuntime(entity).resolveDouble(handlingExpr)

        //Smooth out mouse input.
        val smoothingSpeed = 4
        val invertRoll = if (Cobblemon.config.invertRoll) -1 else 1
        val invertPitch = if (Cobblemon.config.invertPitch) -1 else 1
        var xInput = xMouseSmoother.getNewDeltaValue(xMouse * 0.1 * invertRoll, deltaTime * smoothingSpeed);
        val YInput = yMouseSmoother.getNewDeltaValue(yMouse * 0.1 * invertPitch, deltaTime * smoothingSpeed);

        //limit rolling based on healing
        val rotLimit = handling

        //Limit roll by non linearly decreasing inputs towards
        // a rotation limit based on the current distance from
        // that rotation limit
        if (abs(rollable.roll + xInput) < rotLimit) {
            if (sign(xInput) == sign(rollable.roll).toDouble()) {
                val d = abs(abs(rollable.roll) - rotLimit)
                xInput *= (d.pow(2)) / (rotLimit.pow(2))
            }
        } else if (sign(xInput) == sign(rollable.roll).toDouble()) {
            xInput = 0.0
        }

        //yaw, pitch, roll
        return Vec3(0.0, YInput, xInput)
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
        buffer.writeString(topSpeedExpr.toString())
        buffer.writeString(accelExpr.toString())
        buffer.writeString(handlingExpr.toString())
    }

    override fun decode(buffer: RegistryFriendlyByteBuf) {
        gravity = buffer.readString().asExpression()
        horizontalAcceleration = buffer.readString().asExpression()
        verticalAcceleration = buffer.readString().asExpression()
        topSpeedExpr = buffer.readString().asExpression()
        accelExpr = buffer.readString().asExpression()
        handlingExpr = buffer.readString().asExpression()
    }


    companion object {
        val KEY = cobblemonResource("air/bird")
    }
}