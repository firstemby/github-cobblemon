/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.riding.controllers

import com.bedrockk.molang.Expression
import com.bedrockk.molang.runtime.MoLangMath.lerp
import com.cobblemon.mod.common.Rollable
import com.cobblemon.mod.common.api.riding.controller.RideController
import com.cobblemon.mod.common.api.riding.controller.posing.PoseOption
import com.cobblemon.mod.common.api.riding.controller.posing.PoseProvider
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.riding.states.BirdAirState
import com.cobblemon.mod.common.pokemon.riding.states.JetAirState
import com.cobblemon.mod.common.util.*
import net.minecraft.client.Minecraft
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.util.Mth
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
            it.deltaMovement.length() > 0.5 })
        /*
        .with(PoseOption(PoseType.FLY) {
            val player = it.passengers.firstOrNull() as? Player ?: return@PoseOption false
            player.zza > 0.0f // Checks if the player is actively attempting to move forward
        })
         */
        .with(PoseOption(PoseType.FLY) {
            val player = it.passengers.firstOrNull() as? Player ?: return@PoseOption false
            it.deltaMovement.length() > 0.5 })
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

    var handlingExpr: Expression = "q.get_ride_stats('SKILL', 'AIR', 135.0, 45.0)".asExpression()
        private set
    var topSpeedExpr: Expression = "q.get_ride_stats('SPEED', 'AIR', 1.0, 0.35)".asExpression()
        private set
    // Max accel is a whole 1.0 in 3 seconds. The conversion in the function below is to convert seconds to ticks
    var accelExpr: Expression = "q.get_ride_stats('ACCELERATION', 'AIR', (1.0 / (20.0 * 3.0)), (1.0 / (20.0 * 8.0)))".asExpression()


    override fun speed(entity: PokemonEntity, driver: Player): Float {

        //Retrieve the current bird controller state
        val state = getState(entity, ::BirdAirState)

        return state.rideVel.length().toFloat()
    }

    override fun rotation(entity: PokemonEntity, driver: LivingEntity): Vec2 {
        return Vec2(driver.xRot * 0.5f, driver.yRot)
    }

    override fun velocity(entity: PokemonEntity, driver: Player, input: Vec3): Vec3 {
        var yVel = 0.0
        var leftForce = 0.0
        var upForce = 0.0
        var forwardForce = 0.0
        val state = getState(entity, ::BirdAirState)

        //Perform ride velocity update
        calculateRideSpaceVel(entity, driver, state)

        //Translate ride space velocity to world space velocity.
        val rollable = driver as? Rollable
        if (rollable != null) {
            //Need to deadzone this when straight up or down

            upForce += -1.0 * sin(Math.toRadians(rollable.pitch.toDouble())) * state.rideVel.z
            forwardForce += cos(Math.toRadians(rollable.pitch.toDouble())) * state.rideVel.z

            upForce += cos(Math.toRadians(rollable.pitch.toDouble())) * state.rideVel.y
            forwardForce += sin(Math.toRadians(rollable.pitch.toDouble())) * state.rideVel.y
        }

        //TODO: Add in an upwards and downwards force when pressing space maybe?

        val velocity = Vec3(state.rideVel.x , upForce, forwardForce)
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
        var xInput = xMouseSmoother.getNewDeltaValue(xMouse * 0.1, deltaTime * smoothingSpeed);
        val YInput = yMouseSmoother.getNewDeltaValue(yMouse * 0.1, deltaTime * smoothingSpeed);

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
        return 0.0
    }

    override fun shouldRoll(entity: PokemonEntity) = !Minecraft.getInstance().options.keyShift.isDown()

    /*
    override fun updatePassengerRotation(entity: PokemonEntity, driver: LivingEntity) {
        driver.yRot += (entity.riding.deltaRotation.y)
        driver.setYHeadRot(driver.yHeadRot + (entity.riding.deltaRotation.y))
    }

    override fun clampPassengerRotation(entity: PokemonEntity, driver: LivingEntity) {
        val f = Mth.wrapDegrees(driver.yRot - entity.yRot)
        val runtime = getRuntime(entity)
        val lookYawLimit = 120.0f
        val g = Mth.clamp(f, -lookYawLimit, lookYawLimit)
        driver.yRotO += g - f
        driver.yRot = driver.yRot + g - f
        driver.setYHeadRot(driver.yRot)
    }
     */

    override fun useAngVelSmoothing(entity: PokemonEntity): Boolean = false

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        super.encode(buffer)
        buffer.writeString(gravity.toString())
        buffer.writeString(horizontalAcceleration.toString())
        buffer.writeString(verticalAcceleration.toString())
        buffer.writeString(topSpeedExpr.toString())
        buffer.writeString(accelExpr.toString())
        buffer.writeString(handlingExpr.toString())
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
        topSpeedExpr = buffer.readString().asExpression()
        accelExpr = buffer.readString().asExpression()
        handlingExpr = buffer.readString().asExpression()
    }


    companion object {
        val KEY = cobblemonResource("air/bird")
    }

    /*
    *  Calculates the change in the ride space vector due to player input and ride state
    */
    fun calculateRideSpaceVel( entity: PokemonEntity, driver: Player, state: BirdAirState){


        //retrieve stats
        val topSpeed = getRuntime(entity).resolveDouble(topSpeedExpr)
        val accel = getRuntime(entity).resolveDouble(accelExpr)

        val maxGlideSpeed = topSpeed * 2
        var glideSpeedChange = 0.00

        val currSpeed = state.rideVel.length()

        //speed up and slow down based on input
        if (driver.zza != 0.0f) {
            //state.currSpeed = min(state.currSpeed + accel , topSpeed)
            state.rideVel = Vec3(
                state.rideVel.x,
                state.rideVel.y,
                (state.rideVel.z + (accel * driver.zza)).coerceIn(-topSpeed / 3.0, topSpeed))
        }
        /*
        else if (driver.zza < 0.0 ) {
            //Decelerate is currently always a constant half of max acceleration.
            //state.currSpeed = max(state.currSpeed - (0.0166 / 2), 0.0)
            state.rideVel = Vec3(state.rideVel.x, state.rideVel.y, max(state.rideVel.z - (0.0166 / 2), 0.0))
        }
        */

        val rollable = driver as? Rollable
        if (rollable != null) {
            //Base glide speed change on current pitch of the ride.
            glideSpeedChange = sin(Math.toRadians(rollable.pitch.toDouble()))
            glideSpeedChange = glideSpeedChange.pow(3)

            //TODO: Possibly create a deadzone around parallel where glide doesn't affect speed?
            if (glideSpeedChange <= 0.0) {
                //Ensures that a propelling force is still able to be applied when
                //climbing in height
                if (driver.zza <= 0) {
                    //speed decrease should be 2x speed increase?
                    //state.currSpeed = max(state.currSpeed + (0.0166) * glideSpeedChange, 0.0 )
                    state.rideVel = Vec3(
                        state.rideVel.x,
                        state.rideVel.y,
                        lerp( state.rideVel.z, 0.0,(0.0166) * glideSpeedChange ))
                }
            } else {
                // only add to the speed if it hasn't exceeded the current
                //glide angles maximum amount of speed that it can give.
                //state.currSpeed = min(state.currSpeed + ((0.0166 * 2) * glideSpeedChange), maxGlideSpeed)
                state.rideVel = Vec3(
                    state.rideVel.x,
                    state.rideVel.y,
                    min(state.rideVel.z + ((0.0166 * 2) * glideSpeedChange), maxGlideSpeed))
            }
        }

        //Lateral movement based on driver input.
        val latTopSpeed = topSpeed / 2.0
        if(driver.xxa != 0.0f){
            state.rideVel = Vec3(
                (state.rideVel.x + (accel * driver.xxa)).coerceIn(-latTopSpeed, latTopSpeed),
                state.rideVel.y,
                state.rideVel.z)
        }
        else{
            state.rideVel = Vec3(
                lerp(state.rideVel.x, 0.0, latTopSpeed / 20.0),
                state.rideVel.y,
                state.rideVel.z)
        }

        //Vertical movement based on driver input.
        val vertTopSpeed = topSpeed / 2.0
        val vertInput = when {
            Minecraft.getInstance().options.keyJump.isDown() -> 1.0
            Minecraft.getInstance().options.keyShift.isDown() -> -1.0
            else -> 0.0
        }
        if(vertInput != 0.0) {
            state.rideVel = Vec3(
                state.rideVel.x,
                (state.rideVel.y + (accel * vertInput)).coerceIn(-vertTopSpeed, vertTopSpeed),
                state.rideVel.z)
        }
        else {
            state.rideVel = Vec3(
                state.rideVel.x,
                lerp(state.rideVel.y, 0.0, vertTopSpeed / 20.0),
                state.rideVel.z)
        }

        //air resistance
        //TODO: This should not affect the ride if the player is inputting a force?
        //state.currSpeed = max(state.currSpeed - 0.005, 0.0)
        //Ensure it would take ten whole seconds to come to a complete stop through air resistance.
        state.rideVel = Vec3(
            state.rideVel.x,
            state.rideVel.y,
            lerp( state.rideVel.z,0.0, topSpeed / ( 20.0 * 10.0)))

    }
}