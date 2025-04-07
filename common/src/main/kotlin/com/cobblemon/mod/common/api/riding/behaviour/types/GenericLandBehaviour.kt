/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.riding.behaviour.types

import com.bedrockk.molang.Expression
import com.bedrockk.molang.runtime.value.DoubleValue
import com.cobblemon.mod.common.api.riding.behaviour.*
import com.cobblemon.mod.common.api.riding.posing.PoseOption
import com.cobblemon.mod.common.api.riding.posing.PoseProvider
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.*
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.util.SmoothDouble
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.Shapes
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class GenericLandBehaviour : RidingBehaviour<GenericLandSettings, GenericLandState> {
    companion object {
        val KEY = cobblemonResource("land/generic")
    }

    override val key = KEY

    val poseProvider = PoseProvider<GenericLandSettings, GenericLandState>(PoseType.STAND)
        .with(PoseOption(PoseType.WALK) { _, state, _ ->
            return@PoseOption state.rideVel.get().z > 0.1
        })

    override fun isActive(
        settings: GenericLandSettings,
        state: GenericLandState,
        vehicle: PokemonEntity
    ): Boolean {
        return Shapes.create(vehicle.boundingBox).blockPositionsAsListRounded().any {
            //Need to check other fluids
            if (vehicle.isInWater || vehicle.isUnderWater) {
                return@any false
            }
            //This might not actually work, depending on what the yPos actually is. yPos of the middle of the entity? the feet?
            if (it.y.toDouble() == (vehicle.position().y)) {
                val blockState = vehicle.level().getBlockState(it.below())
                return@any !blockState.isAir && blockState.fluidState.isEmpty
            }
            true
        }
    }

    override fun pose(
        settings: GenericLandSettings,
        state: GenericLandState,
        vehicle: PokemonEntity
    ): PoseType {
        return this.poseProvider.select(settings, state, vehicle)
    }

    override fun speed(
        settings: GenericLandSettings,
        state: GenericLandState,
        vehicle: PokemonEntity,
        driver: Player
    ): Float {
        return state.rideVel.get().length().toFloat()
    }

    override fun rotation(
        settings: GenericLandSettings,
        state: GenericLandState,
        vehicle: PokemonEntity,
        driver: LivingEntity
    ): Vec2 {
        return Vec2(driver.xRot * 0.5f, driver.yRot)
    }

    override fun velocity(
        settings: GenericLandSettings,
        state: GenericLandState,
        vehicle: PokemonEntity,
        driver: Player,
        input: Vec3
    ): Vec3 {
        val runtime = vehicle.runtime
        val driveFactor = runtime.resolveFloat(settings.driveFactor)
        val strafeFactor = runtime.resolveFloat(settings.strafeFactor)
        val f = driver.xxa * strafeFactor
        var g = driver.zza * driveFactor
        if (g <= 0.0f) {
            g *= runtime.resolveFloat(settings.reverseDriveFactor)
        }
        val gravity = -1.0

        val newVelocity = calculateRideSpaceVel(settings, state, vehicle, driver)

        //Jump the thang!
        if (driver.jumping && vehicle.onGround()) {
            state.rideVel.set(Vec3(newVelocity.x, 2.0, newVelocity.z))
        } else {
            state.rideVel.set(newVelocity)
        }

        //This is cheap.
        //Also make it stop quicker the slower it is.
        val maxSpeed = 1.0
        //state.currVel = state.currVel.lerp( Vec3(0.0, state.currVel.y, 0.0), 1.0/20.0 )


        //entity.deltaMovement = entity.deltaMovement.lerp(velocity, 1.0)
        //state.currVel = state.currVel.add( f.toDouble() / 20.0, gravity / 20.0 , g.toDouble() / 20.0)
        return state.rideVel.get()
    }

    private fun calculateRideSpaceVel(
        settings: GenericLandSettings,
        state: GenericLandState,
        vehicle: PokemonEntity,
        driver: Player
    ): Vec3 {
        val topSpeed = vehicle.runtime.resolveDouble(settings.topSpeedExpr)
        val accel = vehicle.runtime.resolveDouble(settings.accelExpr)
        val speed = state.rideVel.get().length()

        val minSpeed = 0.0

        var newVelocity = Vec3(state.rideVel.get().x, state.rideVel.get().y, state.rideVel.get().z)

        //speed up and slow down based on input
        if (driver.zza > 0.0 && speed <= topSpeed && state.stamina.get() > 0.0f) {
            //modify acceleration to be slower when at closer speeds to top speed
            val accelMod = max((normalizeSpeed(speed, minSpeed, topSpeed) - 1).pow(2), 0.1)
            newVelocity = Vec3(newVelocity.x, newVelocity.y, min(newVelocity.z + (accel * accelMod), topSpeed))
        } else if (driver.zza >= 0.0 && state.stamina.get() == 0.0f || speed > topSpeed) {
            newVelocity = Vec3(newVelocity.x, newVelocity.y, max(newVelocity.z - ((accel) / 4), minSpeed))
        } else if (driver.zza < 0.0 && speed > minSpeed) {
            //modify deccel to be slower when at closer speeds to minimum speed
            val deccelMod = max(-(normalizeSpeed(speed, minSpeed, topSpeed)) + 1, 0.0)

            //Decelerate currently always a constant half of max acceleration.
            newVelocity =
                Vec3(newVelocity.x, newVelocity.y, max(newVelocity.z - ((accel * deccelMod) / 2), minSpeed))
        }

        if (vehicle.onGround()) {
            newVelocity = Vec3(newVelocity.x, 0.0, newVelocity.z)
        } else {
            newVelocity = Vec3(newVelocity.x, max(newVelocity.y - 0.2, -1.0), newVelocity.z)
        }
        return newVelocity
    }

    /*
    *  Normalizes the current speed between minSpeed and maxSpeed.
    *  The result is clamped between 0.0 and 1.0, where 0.0 represents minSpeed and 1.0 represents maxSpeed.
    */
    private fun normalizeSpeed(currSpeed: Double, minSpeed: Double, maxSpeed: Double): Double {
        require(maxSpeed > minSpeed) { "maxSpeed must be greater than minSpeed" }
        return ((currSpeed - minSpeed) / (maxSpeed - minSpeed)).coerceIn(0.0, 1.0)
    }

    override fun angRollVel(
        settings: GenericLandSettings,
        state: GenericLandState,
        vehicle: PokemonEntity,
        driver: Player,
        deltaTime: Double
    ): Vec3 {
        return Vec3.ZERO
    }

    override fun rotationOnMouseXY(
        settings: GenericLandSettings,
        state: GenericLandState,
        vehicle: PokemonEntity,
        driver: Player,
        mouseY: Double,
        mouseX: Double,
        mouseYSmoother: SmoothDouble,
        mouseXSmoother: SmoothDouble,
        sensitivity: Double,
        deltaTime: Double
    ): Vec3 {
        //Smooth out mouse input.
        val smoothingSpeed = 4
        val xInput = mouseXSmoother.getNewDeltaValue(mouseX * 0.1, deltaTime * smoothingSpeed);
        val yInput = mouseYSmoother.getNewDeltaValue(mouseY * 0.1, deltaTime * smoothingSpeed);

        //yaw, pitch, roll
        return Vec3(0.0, yInput, xInput)
    }

    override fun canJump(
        settings: GenericLandSettings,
        state: GenericLandState,
        vehicle: PokemonEntity,
        driver: Player
    ): Boolean {
        return false
    }

    override fun setRideBar(
        settings: GenericLandSettings,
        state: GenericLandState,
        vehicle: PokemonEntity,
        driver: Player
    ): Float {
        //Retrieve stamina from state and tick up at a rate of 0.1 a second
        val staminaGain = (1.0 / 20.0) * 0.1
        state.stamina.set(min(state.stamina.get() + staminaGain, 1.0).toFloat())
        return (state.stamina.get() / 1.0f)
    }

    override fun jumpForce(
        settings: GenericLandSettings,
        state: GenericLandState,
        vehicle: PokemonEntity,
        driver: Player,
        jumpStrength: Int
    ): Vec3 {
        val runtime = vehicle.runtime
        runtime.environment.query.addFunction("jump_strength") { DoubleValue(jumpStrength.toDouble()) }
        val jumpVector = settings.jumpVector.map { runtime.resolveFloat(it) }
        return Vec3(jumpVector[0].toDouble(), jumpVector[1].toDouble(), jumpVector[2].toDouble())
    }

    override fun gravity(
        settings: GenericLandSettings,
        state: GenericLandState,
        vehicle: PokemonEntity,
        regularGravity: Double
    ): Double {
        return 0.0
    }

    override fun rideFovMultiplier(
        settings: GenericLandSettings,
        state: GenericLandState,
        vehicle: PokemonEntity,
        driver: Player
    ): Float {
        return 1.0f
    }

    override fun useAngVelSmoothing(
        settings: GenericLandSettings,
        state: GenericLandState,
        vehicle: PokemonEntity
    ): Boolean {
        return false
    }

    override fun useRidingAltPose(
        settings: GenericLandSettings,
        state: GenericLandState,
        vehicle: PokemonEntity,
        driver: Player
    ): Boolean {
        return false
    }

    override fun inertia(
        settings: GenericLandSettings,
        state: GenericLandState,
        vehicle: PokemonEntity
    ): Double {
        return 0.5
    }

    override fun shouldRoll(
        settings: GenericLandSettings,
        state: GenericLandState,
        vehicle: PokemonEntity
    ): Boolean {
        return false
    }

    override fun turnOffOnGround(
        settings: GenericLandSettings,
        state: GenericLandState,
        vehicle: PokemonEntity
    ): Boolean {
        return false
    }

    override fun dismountOnShift(
        settings: GenericLandSettings,
        state: GenericLandState,
        vehicle: PokemonEntity
    ): Boolean {
        return false
    }

    override fun shouldRotatePokemonHead(
        settings: GenericLandSettings,
        state: GenericLandState,
        vehicle: PokemonEntity
    ): Boolean {
        return false
    }

    override fun shouldRotatePlayerHead(
        settings: GenericLandSettings,
        state: GenericLandState,
        vehicle: PokemonEntity
    ): Boolean {
        return false
    }

    override fun createDefaultState() = GenericLandState()
}

class GenericLandSettings : RidingBehaviourSettings {
    override val key = GenericLandBehaviour.KEY

    var driveFactor = "1.0".asExpression()
        private set

    var strafeFactor = "0.2".asExpression()
        private set

    var reverseDriveFactor = "0.25".asExpression()
        private set

    var topSpeedExpr: Expression = "q.get_ride_stats('SPEED', 'LAND', 1.0, 0.3)".asExpression()
        private set

    // Max accel is a whole 1.0 in 1 second. The conversion in the function below is to convert seconds to ticks
    var accelExpr: Expression =
        "q.get_ride_stats('ACCELERATION', 'LAND', (1.0 / (20.0 * 0.5)), (1.0 / (20.0 * 5.0)))".asExpression()
        private set

    var jumpVector = listOf("0".asExpression(), "0.3".asExpression(), "0".asExpression())
        private set

    // Between 30 seconds and 10 seconds at the lowest when at full speed.
    var staminaExpr: Expression = "q.get_ride_stats('STAMINA', 'LAND', 30.0, 10.0)".asExpression()
        private set

    //Between a one block jump and a ten block jump
    var jumpExpr: Expression = "q.get_ride_stats('JUMP', 'LAND', 10.0, 1.0)".asExpression()
        private set

    var handlingExpr: Expression = "q.get_ride_stats('SKILL', 'LAND', 140.0, 20.0)".asExpression()
        private set

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeExpression(driveFactor)
        buffer.writeExpression(strafeFactor)
        buffer.writeExpression(reverseDriveFactor)
        buffer.writeExpression(topSpeedExpr)
        buffer.writeExpression(accelExpr)
        buffer.writeExpression(jumpVector[0])
        buffer.writeExpression(jumpVector[1])
        buffer.writeExpression(jumpVector[2])
        buffer.writeExpression(staminaExpr)
        buffer.writeExpression(jumpExpr)
        buffer.writeExpression(handlingExpr)
    }

    override fun decode(buffer: RegistryFriendlyByteBuf) {
        driveFactor = buffer.readExpression()
        strafeFactor = buffer.readExpression()
        reverseDriveFactor = buffer.readExpression()
        topSpeedExpr = buffer.readExpression()
        accelExpr = buffer.readExpression()
        jumpVector = listOf(
            buffer.readExpression(),
            buffer.readExpression(),
            buffer.readExpression()
        )
        staminaExpr = buffer.readExpression()
        jumpExpr = buffer.readExpression()
        handlingExpr = buffer.readExpression()
    }

}

class GenericLandState : RidingBehaviourState {
    var rideVel = ridingState(Vec3.ZERO, Side.CLIENT)
    var stamina = ridingState(1.0f, Side.CLIENT)
    var currSpeed = ridingState(0.0, Side.BOTH)

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeVec3(rideVel.get())
        buffer.writeFloat(stamina.get())
    }

    override fun decode(buffer: RegistryFriendlyByteBuf) {
        rideVel.set(buffer.readVec3(), forced = true)
        stamina.set(buffer.readFloat(), forced = true)
    }

    override fun reset() {
        rideVel.set(Vec3.ZERO, forced = true)
        stamina.set(1.0f, forced = true)
        currSpeed.set(0.0, forced = true)
    }

    override fun toString(): String {
        return "GenericLandState(rideVel=${rideVel.get()}, stamina=${stamina.get()}, currSpeed=${currSpeed.get()})"
    }

    override fun copy() = GenericLandState().also {
        it.rideVel.set(this.rideVel.get(), forced = true)
        it.stamina.set(this.stamina.get(), forced = true)
        it.currSpeed.set(this.currSpeed.get(), forced = true)
    }

    override fun shouldSync(previous: RidingBehaviourState): Boolean {
        if (previous !is GenericLandState) return false
        if (rideVel != previous.rideVel) return true
        if (stamina != previous.stamina) return true
        return false
    }
}
