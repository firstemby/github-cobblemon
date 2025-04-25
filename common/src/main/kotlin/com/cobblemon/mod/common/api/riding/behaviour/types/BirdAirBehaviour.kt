/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.riding.behaviour.types

import com.bedrockk.molang.Expression
import com.bedrockk.molang.runtime.MoLangMath.lerp
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.OrientationControllable
import com.cobblemon.mod.common.api.riding.RidingStyle
import com.cobblemon.mod.common.api.riding.behaviour.*
import com.cobblemon.mod.common.api.riding.posing.PoseOption
import com.cobblemon.mod.common.api.riding.posing.PoseProvider
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.*
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.SmoothDouble
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import org.joml.Matrix3f
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.*

class BirdAirBehaviour : RidingBehaviour<BirdAirSettings, BirdAirState> {
    companion object {
        val KEY = cobblemonResource("air/bird")
    }

    override val key: ResourceLocation = KEY

    override fun getRidingStyle(settings: BirdAirSettings, state: BirdAirState): RidingStyle {
        return RidingStyle.AIR
    }

    val poseProvider = PoseProvider<BirdAirSettings, BirdAirState>(PoseType.HOVER)
        .with(PoseOption(PoseType.FLY) { _, state, _ -> state.rideVelocity.get().z > 0.2 })

    override fun isActive(settings: BirdAirSettings, state: BirdAirState, vehicle: PokemonEntity): Boolean {
        return true
    }

    override fun pose(settings: BirdAirSettings, state: BirdAirState, vehicle: PokemonEntity): PoseType {
        return poseProvider.select(settings, state, vehicle)
    }

    override fun speed(
        settings: BirdAirSettings,
        state: BirdAirState,
        vehicle: PokemonEntity,
        driver: Player
    ): Float {
        return state.rideVelocity.get().length().toFloat()
    }

    override fun rotation(
        settings: BirdAirSettings,
        state: BirdAirState,
        vehicle: PokemonEntity,
        driver: LivingEntity
    ): Vec2 {
        return Vec2(driver.xRot * 0.5f, driver.yRot)
    }

    override fun velocity(
        settings: BirdAirSettings,
        state: BirdAirState,
        vehicle: PokemonEntity,
        driver: Player,
        input: Vec3
    ): Vec3 {
        var yVel = 0.0
        var leftForce = 0.0
        var upForce = 0.0
        var forwardForce = 0.0

        //Perform ride velocity update
        calculateRideSpaceVel(settings, state, vehicle, driver)

        //Translate ride space velocity to world space velocity.
        val controller = (driver as? OrientationControllable)?.orientationController
        if (controller != null) {
            //Need to deadzone this when straight up or down

            upForce += -1.0 * sin(Math.toRadians(controller.pitch.toDouble())) * state.rideVelocity.get().z
            forwardForce += cos(Math.toRadians(controller.pitch.toDouble())) * state.rideVelocity.get().z

            upForce += cos(Math.toRadians(controller.pitch.toDouble())) * state.rideVelocity.get().y
            forwardForce += sin(Math.toRadians(controller.pitch.toDouble())) * state.rideVelocity.get().y
        }


        //Bring the ride out of the sky when stamina is depleted.
        if (state.stamina.get() <= 0.0) {
            upForce -= 0.3
        }

        val altitudeLimit = vehicle.runtime.resolveDouble(settings.jumpExpr)

        //Only limit altitude if altitude is not infinite
        if (!vehicle.runtime.resolveBoolean(settings.infiniteAltitude)) {
            //Provide a hard limit on altitude
            upForce = if (vehicle.y >= altitudeLimit && upForce > 0) 0.0 else upForce
        }

        val velocity = Vec3(state.rideVelocity.get().x , upForce, forwardForce)
        return velocity
    }

    /*
    *  Calculates the change in the ride space vector due to player input and ride state
    */
    fun calculateRideSpaceVel(settings: BirdAirSettings, state: BirdAirState, vehicle: PokemonEntity, driver: Player) {
        //retrieve stats
        val topSpeed = vehicle.runtime.resolveDouble(settings.speedExpr)
        val glideTopSpeed = vehicle.runtime.resolveDouble(settings.glidespeedExpr)
        val accel = vehicle.runtime.resolveDouble(settings.accelerationExpr)
        val staminaStat = vehicle.runtime.resolveDouble(settings.staminaExpr)

        var glideSpeedChange = 0.0

        val currSpeed = state.rideVelocity.get().length()

        //Flag for determining if player is actively inputting
        var activeInput = false

        var newVelocity = Vec3(state.rideVelocity.get().x, state.rideVelocity.get().y, state.rideVelocity.get().z)

        //speed up and slow down based on input
        if (driver.zza != 0.0f && state.stamina.get() > 0.0) {
            //make sure it can't exceed top speed
            val forwardInput = when {
                driver.zza > 0 && newVelocity.z > topSpeed -> 0.0
                driver.zza < 0 && newVelocity.z < (-topSpeed / 3.0) -> 0.0
                else -> driver.zza.sign
            }

            newVelocity = Vec3(
                newVelocity.x,
                newVelocity.y,
                (newVelocity.z + (accel * forwardInput.toDouble())))

            activeInput = true
        }

        val controller = (driver as? OrientationControllable)?.orientationController
        if (controller != null) {
            //Base glide speed change on current pitch of the ride.
            glideSpeedChange = sin(Math.toRadians(controller.pitch.toDouble()))
            glideSpeedChange = glideSpeedChange.pow(3) * 0.5

            //TODO: Possibly create a deadzone around parallel where glide doesn't affect speed?
            if (glideSpeedChange <= 0.0) {
                //Ensures that a propelling force is still able to be applied when
                //climbing in height
                if (driver.zza <= 0) {
                    //speed decrease should be 2x speed increase?
                    //state.currSpeed = max(state.currSpeed + (0.0166) * glideSpeedChange, 0.0 )
                    newVelocity = Vec3(
                        newVelocity.x,
                        newVelocity.y,
                        lerp( newVelocity.z, 0.0,glideSpeedChange * -0.0166 * 2 )
                    )
                }
            } else {
                // only add to the speed if it hasn't exceeded the current
                //glide angles maximum amount of speed that it can give.
                //state.currSpeed = min(state.currSpeed + ((0.0166 * 2) * glideSpeedChange), maxGlideSpeed)
                newVelocity = Vec3(
                    newVelocity.x,
                    newVelocity.y,
                    min(newVelocity.z + ((0.0166 * 2) * glideSpeedChange), glideTopSpeed)
                )
            }
        }

        //Lateral movement based on driver input.
//        val latTopSpeed = topSpeed / 2.0
//        if (driver.xxa != 0.0f && state.stamina.get() > 0.0) {
//            newVelocity = Vec3(
//                (newVelocity.x + (accel * driver.xxa)).coerceIn(-latTopSpeed, latTopSpeed),
//                newVelocity.y,
//                newVelocity.z)
//            activeInput = true
//        }
//        else {
//            newVelocity = Vec3(
//                lerp(newVelocity.x, 0.0, latTopSpeed / 20.0),
//                newVelocity.y,
//                newVelocity.z)
//        }

        //Vertical movement based on driver input.
        val vertTopSpeed = topSpeed / 2.0
        val vertInput = when {
            driver.jumping && state.rideVelocity.get().length() < 0.3 -> 1.0
            driver.isShiftKeyDown && state.rideVelocity.get().length() < 0.3  -> -1.0
            else -> 0.0
        }

        if (vertInput != 0.0 && state.stamina.get() > 0.0) {
            newVelocity = Vec3(
                newVelocity.x,
                (newVelocity.y + (accel * vertInput)).coerceIn(-vertTopSpeed, vertTopSpeed),
                newVelocity.z)
            activeInput = true
        }
        else {
            newVelocity = Vec3(
                newVelocity.x,
                lerp(newVelocity.y, 0.0, vertTopSpeed / 20.0),
                newVelocity.z)
        }

        //Check if the ride should be gliding
        if (driver.isLocalPlayer) {
            if (activeInput && state.stamina.get() > 0.0) {
                state.gliding.set(false)
            } else {
                state.gliding.set(true)
            }
        }

        //TODO: Reintroduce stamina drain once stats start to be polished and tweaked
//        //Only perform stamina logic if the ride does not have infinite stamina
//        if (!vehicle.runtime.resolveBoolean(settings.infiniteStamina)) {
//            if (activeInput) {
//                state.stamina.set(state.stamina.get() - (0.05 / staminaStat).toFloat())
//            }
//
//            //Lose a base amount of stamina just for being airborne
//            state.stamina.set(state.stamina.get() - (0.01 / staminaStat).toFloat())
//        }
//        else
//        {
//            state.stamina.set(1.0f)
//        }

        //air resistance
        if( abs(newVelocity.z) > 0) {
            newVelocity = newVelocity.subtract(0.0, 0.0, min(0.0015 * newVelocity.z.sign, newVelocity.z))
        }
        state.rideVelocity.set(newVelocity)
    }

    override fun angRollVel(
        settings: BirdAirSettings,
        state: BirdAirState,
        vehicle: PokemonEntity,
        driver: Player,
        deltaTime: Double
    ): Vec3 {
        if (driver !is OrientationControllable) return Vec3.ZERO
        val controller = (driver as OrientationControllable).orientationController

        //limit rolling based on handling and current speed.
        //modulated by speed so that when flapping idle in air you are ont wobbling around to look around
        //TODO: Tie handling into yaw rate
        val handling = vehicle.runtime.resolveDouble(settings.handlingExpr)
        val topSpeed = vehicle.runtime.resolveDouble(settings.speedExpr)
        //This rotation limit will be used to know if the ride has exceeded it and its needs correcting.
        val rotMin = 15.0
        val rotLimit = max(handling * sqrt(normalizeVal(state.rideVelocity.get().length(), 0.0, topSpeed)), rotMin)


        val rotationChangeRate = 100.0

        var yawForce =  rotationChangeRate * sin(Math.toRadians(controller.roll.toDouble()))
        //for a bit of correction on the rolls limit it to a quarter the amount
        var pitchForce = -0.35 * rotationChangeRate * abs(sin(Math.toRadians(controller.roll.toDouble())))

        //limit rotation modulation when pitched up heavily or pitched down heavily
        yawForce *= abs(cos(Math.toRadians(controller.pitch.toDouble())))
        pitchForce *= abs(cos(Math.toRadians(controller.pitch.toDouble()))) * 1.5

        var yawDeltaDeg =  deltaTime * rotationChangeRate * sin(Math.toRadians(controller.roll.toDouble())) //sin(Math.toRadians(controller.roll.toDouble())) * abs(cos(Math.toRadians(controller.pitch.toDouble())))
        val trueYawDelt = yawDeltaDeg * abs(cos(Math.toRadians(controller.pitch.toDouble())))
        //val pitchedYawDelt = yawDeltaDeg - trueYawDelt

        controller.applyGlobalYaw(trueYawDelt.toFloat())

        val correctionRate = 5.0
        //Calculate correcting roll force.
        if(abs(controller.roll) > rotLimit) {
            state.currRollCorrectionForce.set(
                state.currRollCorrectionForce.get() + ((abs(controller.roll) - rotLimit) / 180 - rotLimit) * controller.roll.sign * correctionRate * deltaTime * 0.01
            )
        } else {
            state.currRollCorrectionForce.set(
                //reduce rollCorrection gradually and make sure to
                //state.currRollCorrectionForce.get() - max(correctionRate * deltaTime * 0.04, abs(state.currRollCorrectionForce.get())) * state.currRollCorrectionForce.get().sign
                lerp(state.currRollCorrectionForce.get(), 0.0, deltaTime * 0.98)
            )
        }

        //yaw, pitch, roll
        return Vec3(0.0, 0.0, state.currRollCorrectionForce.get())
    }

    override fun rotationOnMouseXY(
        settings: BirdAirSettings,
        state: BirdAirState,
        vehicle: PokemonEntity,
        driver: Player,
        mouseY: Double,
        mouseX: Double,
        mouseYSmoother: SmoothDouble,
        mouseXSmoother: SmoothDouble,
        sensitivity: Double,
        deltaTime: Double
    ): Vec3 {
        if (driver !is OrientationControllable) return Vec3.ZERO
        val controller = (driver as OrientationControllable).orientationController

        val handling = vehicle.runtime.resolveDouble(settings.handlingExpr)
        val topSpeed = vehicle.runtime.resolveDouble(settings.speedExpr)

        //Smooth out mouse input.
        val smoothingSpeed = 4.0
        val invertRoll = if (Cobblemon.config.invertRoll) -1 else 1
        val invertPitch = if (Cobblemon.config.invertPitch) -1 else 1
        val mouseXc = (mouseX).coerceIn(-60.0, 60.0)
        val mouseYc = (mouseY).coerceIn(-60.0, 60.0)
        val xInput = mouseXSmoother.getNewDeltaValue(mouseXc * 0.1 * invertRoll, deltaTime * smoothingSpeed);
        val yInput = mouseYSmoother.getNewDeltaValue(mouseYc * 0.1 * invertPitch, deltaTime * smoothingSpeed);

        //limit rolling based on handling and current speed.
        //modulated by speed so that when flapping idle in air you are ont wobbling around to look around
        val rotMin = 15.0
        var rollForce = xInput
        val rotLimit = max(handling * sqrt(normalizeVal(state.rideVelocity.get().length(), 0.0, topSpeed)), rotMin)

        //Limit roll by non linearly decreasing inputs towards
        // a rotation limit based on the current distance from
        // that rotation limit
        if (abs(controller.roll + rollForce) < rotLimit) {
            if (sign(rollForce) == sign(controller.roll).toDouble()) {
                //Grab how far the current roll is away from the roll limit
                val d = abs(abs(controller.roll) - rotLimit)
                rollForce *= (d.pow(2)) / (rotLimit.pow(2))
            }
        } else if (sign(rollForce) == sign(controller.roll).toDouble()) {
            rollForce = 0.0
        }

        //Give the ability to yaw with x mouse input when at low speeds.
        val yawForce = xInput * ( 1.0 - sqrt(normalizeVal(state.rideVelocity.get().length(), 0.0, topSpeed)))

        //Yaw locally a bit when up or down so that its more intuitive to make it out of a dive or a straight vertical
        //climb
        val yawForcePitched = xInput * sin(Math.toRadians(abs(controller.pitch.toDouble()))) * 0.25


        //Apply yaw globally as we don't want roll or pitch changes due to local yaw when looking up or down.
        controller.applyGlobalYaw(yawForce.toFloat())

        //yaw, pitch, roll
        return Vec3(yawForcePitched, yInput, rollForce)
    }

    /*
    *  Normalizes the given value between a min and a max.
    *  The result is clamped between 0.0 and 1.0, where 0.0 represents x is at or below min
    *  and 1.0 represents x is at or above it.
    */
    private fun normalizeVal(x: Double, min: Double, max: Double): Double {
        require(max > min) { "max must be greater than min" }
        return ((x - min) / (max - min)).coerceIn(0.0, 1.0)
    }

    override fun canJump(
        settings: BirdAirSettings,
        state: BirdAirState,
        vehicle: PokemonEntity,
        driver: Player
    ): Boolean {
        return false
    }

    override fun setRideBar(
        settings: BirdAirSettings,
        state: BirdAirState,
        vehicle: PokemonEntity,
        driver: Player
    ): Float {
        return (state.stamina.get() / 1.0f)
    }

    override fun jumpForce(
        settings: BirdAirSettings,
        state: BirdAirState,
        vehicle: PokemonEntity,
        driver: Player,
        jumpStrength: Int
    ): Vec3 {
        return Vec3.ZERO
    }

    override fun gravity(
        settings: BirdAirSettings,
        state: BirdAirState,
        vehicle: PokemonEntity,
        regularGravity: Double
    ): Double {
        return 0.0
    }

    override fun rideFovMultiplier(
        settings: BirdAirSettings,
        state: BirdAirState,
        vehicle: PokemonEntity,
        driver: Player
    ): Float {
        val topSpeed = vehicle.runtime.resolveDouble(settings.speedExpr)
        val glideTopSpeed = vehicle.runtime.resolveDouble(settings.glidespeedExpr)

        //Must I ensure that topspeed is greater than minimum?
        val normalizedGlideSpeed = normalizeVal(state.rideVelocity.get().length(), topSpeed, glideTopSpeed)

        //Only ever want the fov change to be a max of 0.2 and for it to have non linear scaling.
        return 1.0f + normalizedGlideSpeed.pow(2).toFloat() * 0.2f
    }

    override fun useAngVelSmoothing(
        settings: BirdAirSettings,
        state: BirdAirState,
        vehicle: PokemonEntity
    ): Boolean {
        return false
    }

    override fun useRidingAltPose(
        settings: BirdAirSettings,
        state: BirdAirState,
        vehicle: PokemonEntity,
        driver: Player
    ): Boolean {
        return state.gliding.get()
    }

    override fun inertia(settings: BirdAirSettings, state: BirdAirState, vehicle: PokemonEntity): Double {
        return 0.5
    }

    override fun shouldRoll(settings: BirdAirSettings, state: BirdAirState, vehicle: PokemonEntity): Boolean {
        return true
    }

    override fun turnOffOnGround(settings: BirdAirSettings, state: BirdAirState, vehicle: PokemonEntity): Boolean {
        return false
    }

    override fun dismountOnShift(settings: BirdAirSettings, state: BirdAirState, vehicle: PokemonEntity): Boolean {
        return false
    }

    override fun shouldRotatePokemonHead(
        settings: BirdAirSettings,
        state: BirdAirState,
        vehicle: PokemonEntity
    ): Boolean {
        return false
    }

    override fun shouldRotatePlayerHead(
        settings: BirdAirSettings,
        state: BirdAirState,
        vehicle: PokemonEntity
    ): Boolean {
        return false
    }

    override fun createDefaultState(settings: BirdAirSettings) = BirdAirState()

}

class BirdAirSettings : RidingBehaviourSettings {
    override val key = BirdAirBehaviour.KEY

    var infiniteAltitude: Expression = "false".asExpression()
        private set

    var infiniteStamina: Expression = "false".asExpression()
        private set

    //max y level for the ride
    var jumpExpr: Expression = "q.get_ride_stats('JUMP', 'AIR', 200.0, 128.0)".asExpression()
    var handlingExpr: Expression = "q.get_ride_stats('SKILL', 'AIR', 135.0, 45.0)".asExpression()
    var speedExpr: Expression = "q.get_ride_stats('SPEED', 'AIR', 1.0, 0.35)".asExpression()
    var accelerationExpr: Expression = "q.get_ride_stats('ACCELERATION', 'AIR', (1.0 / (20.0 * 3.0)), (1.0 / (20.0 * 8.0)))".asExpression()
    var staminaExpr: Expression = "q.get_ride_stats('STAMINA', 'AIR', 120.0, 20.0)".asExpression()

    var glidespeedExpr: Expression = "q.get_ride_stats('SPEED', 'AIR', 2.0, 1.0)".asExpression()
        private set

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeResourceLocation(key)
        buffer.writeExpression(infiniteAltitude)
        buffer.writeExpression(infiniteStamina)
        buffer.writeExpression(glidespeedExpr)
        buffer.writeExpression(jumpExpr)
        buffer.writeExpression(handlingExpr)
        buffer.writeExpression(speedExpr)
        buffer.writeExpression(accelerationExpr)
        buffer.writeExpression(staminaExpr)
    }

    override fun decode(buffer: RegistryFriendlyByteBuf) {
        infiniteAltitude = buffer.readExpression()
        infiniteStamina = buffer.readExpression()
        glidespeedExpr = buffer.readExpression()
        jumpExpr = buffer.readExpression()
        handlingExpr = buffer.readExpression()
        speedExpr = buffer.readExpression()
        accelerationExpr = buffer.readExpression()
        staminaExpr = buffer.readExpression()
    }
}

class BirdAirState : RidingBehaviourState() {
    var gliding = ridingState(false, Side.CLIENT)
    var lastGlide = ridingState(-100L, Side.CLIENT)
    var currRollCorrectionForce = ridingState(0.0, Side.CLIENT)

    override fun encode(buffer: FriendlyByteBuf) {
        super.encode(buffer)
        buffer.writeFloat(stamina.get())
        buffer.writeBoolean(gliding.get())
    }

    override fun decode(buffer: FriendlyByteBuf) {
        super.decode(buffer)
        gliding.set(buffer.readBoolean(), forced = true)
    }

    override fun reset() {
        super.reset()
        gliding.set(false, forced = true)
        lastGlide.set(-100L, forced = true)
        currRollCorrectionForce.set(0.0, forced = true)
    }

    override fun toString(): String {
        return "BirdAirState(rideVelocity=${rideVelocity.get()}, stamina=${stamina.get()}, gliding=${gliding.get()})"
    }

    override fun copy() = BirdAirState().also {
        it.rideVelocity.set(this.rideVelocity.get(), forced = true)
        it.stamina.set(this.stamina.get(), forced = true)
        it.gliding.set(this.gliding.get(), forced = true)
        it.lastGlide.set(this.lastGlide.get(), forced = true)
        it.currRollCorrectionForce.set(this.currRollCorrectionForce.get(), forced = true)
    }

    override fun shouldSync(previous: RidingBehaviourState): Boolean {
        if (previous !is BirdAirState) return false
        if (previous.gliding.get() != gliding.get()) return true
        return super.shouldSync(previous)
    }
}
