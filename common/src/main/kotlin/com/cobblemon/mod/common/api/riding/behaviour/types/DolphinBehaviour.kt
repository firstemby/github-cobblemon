/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.riding.behaviour.types

import com.bedrockk.molang.runtime.value.DoubleValue
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.OrientationControllable
import com.cobblemon.mod.common.api.riding.RidingStyle
import com.cobblemon.mod.common.api.riding.behaviour.*
import com.cobblemon.mod.common.api.riding.posing.PoseOption
import com.cobblemon.mod.common.api.riding.posing.PoseProvider
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.*
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.SmoothDouble
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.Shapes
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class DolphinBehaviour : RidingBehaviour<DolphinSettings, DolphinState> {
    companion object {
        val KEY = cobblemonResource("swim/dolphin")
    }

    override val key = KEY

    override fun getRidingStyle(settings: DolphinSettings, state: DolphinState): RidingStyle {
        return RidingStyle.LIQUID
    }

    val poseProvider = PoseProvider<DolphinSettings, DolphinState>(PoseType.FLOAT)
        .with(PoseOption(PoseType.SWIM) { _, _, entity -> entity.entityData.get(PokemonEntity.MOVING) })

    override fun isActive(settings: DolphinSettings, state: DolphinState, vehicle: PokemonEntity): Boolean {
        return Shapes.create(vehicle.boundingBox).blockPositionsAsListRounded().any {
            if (it.y.toDouble() == (vehicle.position().y)) {
                val blockState = vehicle.level().getBlockState(it.below())
                return@any (blockState.isAir || !blockState.fluidState.isEmpty ) ||
                        (vehicle.isInWater || vehicle.isUnderWater)
            }
            true
        }
    }

    override fun pose(settings: DolphinSettings, state: DolphinState, vehicle: PokemonEntity): PoseType {
        return poseProvider.select(settings, state, vehicle)
    }

    override fun speed(settings: DolphinSettings, state: DolphinState, vehicle: PokemonEntity, driver: Player): Float {
        return vehicle.runtime.resolveFloat(settings.speed)
    }

    override fun rotation(
        settings: DolphinSettings,
        state: DolphinState,
        vehicle: PokemonEntity,
        driver: LivingEntity
    ): Vec2 {
        return Vec2(driver.xRot * 0.5f, driver.yRot)
    }

    override fun velocity(
        settings: DolphinSettings,
        state: DolphinState,
        vehicle: PokemonEntity,
        driver: Player,
        input: Vec3
    ): Vec3 {
        val runtime = vehicle.runtime

        if (!vehicle.isInWater && !vehicle.isUnderWater)
        {
            state.lastVelocity.set(Vec3(state.lastVelocity.get().x, state.lastVelocity.get().y - 0.035, state.lastVelocity.get().z))
            return state.lastVelocity.get()
        }

        val driveFactor = runtime.resolveFloat(settings.driveFactor)
        val strafeFactor = runtime.resolveFloat(settings.strafeFactor)
        val f = driver.xxa * strafeFactor
        var g = driver.zza * driveFactor
        if (g <= 0.0f) {
            g *= runtime.resolveFloat(settings.reverseDriveFactor)
        }

        var yComp = 0.0

        //Get roll to add a left and right strafe during roll
        //Not sure if this is desired? Will need to mess around with this
        //a bit more
        var zComp = 0.0
        val controller = (driver as? OrientationControllable)?.orientationController
        if (controller != null)
        {
            //Can be used again if I better figure out how to deadzone it near the tops?
            zComp = -1.0 * g.toDouble() * sin(Math.toRadians(controller.roll.toDouble()))
            yComp = -1.0 * g.toDouble() * sin(Math.toRadians(controller.pitch.toDouble()))
        }

        val currVelocity = Vec3(0.0, yComp , g.toDouble())
        state.lastVelocity.set(currVelocity)
        return currVelocity
    }

    override fun angRollVel(
        settings: DolphinSettings,
        state: DolphinState,
        vehicle: PokemonEntity,
        driver: Player,
        deltaTime: Double
    ): Vec3 {
        if (!vehicle.isInWater && !vehicle.isUnderWater)
        {
            return Vec3(0.0, 0.0, 0.0)
        }

        val controller = (driver as? OrientationControllable)?.orientationController

        //this should be changed to be speed maybe?
        val movingForce = driver.zza
        if (controller != null) {
            var yawAngVel = 3 * sin(Math.toRadians(controller.roll.toDouble())).toFloat()
            var pitchAngVel = -1 * Math.abs(sin(Math.toRadians(controller.roll.toDouble())).toFloat())

            //limit rotation modulation when pitched up heavily or pitched down heavily
            yawAngVel *= (abs(cos(Math.toRadians(controller.pitch.toDouble())))).toFloat()
            pitchAngVel *= (abs(cos(Math.toRadians(controller.pitch.toDouble())))).toFloat()
            //if you are not pressing forward then don't turn
            //yawAngVel *= movingForce
            //Ignore x,y,z its angular velocity:
            //yaw, pitch, roll
            return Vec3(yawAngVel.toDouble(), pitchAngVel.toDouble(), 0.0)
        }

        return Vec3(0.0, 0.0, 0.0)
    }

    override fun rotationOnMouseXY(
        settings: DolphinSettings,
        state: DolphinState,
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

        //Might need to add the smoothing here for default.
        val invertRoll = if (Cobblemon.config.invertRoll) -1 else 1
        val invertPitch = if (Cobblemon.config.invertPitch) -1 else 1
        return Vec3(0.0, mouseY * invertPitch, mouseX * invertRoll)
    }

    override fun canJump(
        settings: DolphinSettings,
        state: DolphinState,
        vehicle: PokemonEntity,
        driver: Player
    ): Boolean {
        return vehicle.runtime.resolveBoolean(settings.canJump)
    }

    override fun setRideBar(
        settings: DolphinSettings,
        state: DolphinState,
        vehicle: PokemonEntity,
        driver: Player
    ): Float {
        return 0.0f
    }

    override fun jumpForce(
        settings: DolphinSettings,
        state: DolphinState,
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
        settings: DolphinSettings,
        state: DolphinState,
        vehicle: PokemonEntity,
        regularGravity: Double
    ): Double {
        return 0.0
    }

    override fun rideFovMultiplier(
        settings: DolphinSettings,
        state: DolphinState,
        vehicle: PokemonEntity,
        driver: Player
    ): Float {
        return 1.0f
    }

    override fun useAngVelSmoothing(settings: DolphinSettings, state: DolphinState, vehicle: PokemonEntity): Boolean {
        return false
    }

    override fun useRidingAltPose(
        settings: DolphinSettings,
        state: DolphinState,
        vehicle: PokemonEntity,
        driver: Player
    ): ResourceLocation {
        return cobblemonResource("no_pose")
    }

    override fun inertia(settings: DolphinSettings, state: DolphinState, vehicle: PokemonEntity): Double {
        return 0.05
    }

    override fun shouldRoll(settings: DolphinSettings, state: DolphinState, vehicle: PokemonEntity): Boolean {
        return true
    }

    override fun turnOffOnGround(settings: DolphinSettings, state: DolphinState, vehicle: PokemonEntity): Boolean {
        return true
    }

    override fun dismountOnShift(settings: DolphinSettings, state: DolphinState, vehicle: PokemonEntity): Boolean {
        return false
    }

    override fun shouldRotatePokemonHead(
        settings: DolphinSettings,
        state: DolphinState,
        vehicle: PokemonEntity
    ): Boolean {
        return false
    }

    override fun shouldRotatePlayerHead(
        settings: DolphinSettings,
        state: DolphinState,
        vehicle: PokemonEntity
    ): Boolean {
        return false
    }

    override fun createDefaultState(settings: DolphinSettings) = DolphinState()

}

class DolphinSettings : RidingBehaviourSettings {
    override val key = DolphinBehaviour.KEY

    var canJump = "true".asExpression()
        private set

    var jumpVector = listOf("0".asExpression(), "0.3".asExpression(), "0".asExpression())
        private set

    var speed = "1.0".asExpression()
        private set

    var driveFactor = "1.0".asExpression()
        private set

    var reverseDriveFactor = "0.25".asExpression()
        private set

    var strafeFactor = "0.2".asExpression()
        private set

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeResourceLocation(key)
        buffer.writeExpression(canJump)
        buffer.writeExpression(jumpVector[0])
        buffer.writeExpression(jumpVector[1])
        buffer.writeExpression(jumpVector[2])
        buffer.writeExpression(speed)
        buffer.writeExpression(driveFactor)
        buffer.writeExpression(reverseDriveFactor)
        buffer.writeExpression(strafeFactor)
    }

    override fun decode(buffer: RegistryFriendlyByteBuf) {
        canJump = buffer.readExpression()
        jumpVector = listOf(
            buffer.readExpression(),
            buffer.readExpression(),
            buffer.readExpression()
        )
        speed = buffer.readExpression()
        driveFactor = buffer.readExpression()
        reverseDriveFactor = buffer.readExpression()
        strafeFactor = buffer.readExpression()
    }
}

class DolphinState : RidingBehaviourState() {
    var lastVelocity = ridingState(Vec3.ZERO, Side.BOTH)

    override fun reset() {
        super.reset()
        lastVelocity.set(Vec3.ZERO, forced = true)
    }

    override fun toString(): String {
        return "DolphinState(lastVelocity=${lastVelocity.get()})"
    }

    override fun copy() = DolphinState().also {
        it.stamina.set(stamina.get(), forced = true)
        it.rideVelocity.set(rideVelocity.get(), forced = true)
        it.lastVelocity.set(lastVelocity.get(), forced = true)
    }
}
