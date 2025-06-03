/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.riding.behaviour.types.liquid

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.OrientationControllable
import com.cobblemon.mod.common.api.riding.RidingStyle
import com.cobblemon.mod.common.api.riding.behaviour.*
import com.cobblemon.mod.common.api.riding.posing.PoseOption
import com.cobblemon.mod.common.api.riding.posing.PoseProvider
import com.cobblemon.mod.common.api.riding.stats.RidingStat
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.*
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import net.minecraft.util.SmoothDouble
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.Shapes
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class BoatBehaviour : RidingBehaviour<BoatSettings, BoatState> {
    companion object {
        val KEY = cobblemonResource("liquid/boat")
        val TOP_SPEED = 2.0
    }

    override val key = KEY

    override fun getRidingStyle(settings: BoatSettings, state: BoatState): RidingStyle {
        return RidingStyle.LIQUID
    }

    val poseProvider = PoseProvider<BoatSettings, BoatState>(PoseType.FLOAT)
        .with(PoseOption(PoseType.SWIM) { _, _, entity -> entity.isSwimming && entity.entityData.get(PokemonEntity.MOVING) })

    override fun isActive(settings: BoatSettings, state: BoatState, vehicle: PokemonEntity): Boolean {
        if (state.jumpBuffer.get() != -1) {
            return true
        }
        return Shapes.create(vehicle.boundingBox).blockPositionsAsListRounded().any {
            if (vehicle.isInWater || vehicle.isUnderWater) {
                return@any true
            }
            val blockState = vehicle.level().getBlockState(it)
            return@any !blockState.fluidState.isEmpty
        }
    }

    override fun tick(
        settings: BoatSettings,
        state: BoatState,
        vehicle: PokemonEntity,
        driver: Player,
        input: Vec3
    ) {
        if (state.jumpBuffer.get() > 5) {
            if ((vehicle.isInWater || vehicle.isUnderWater || vehicle.onGround())) {
                state.jumpBuffer.set(-1)
            }
        }
        else if (state.jumpBuffer.get() > -1) {
            state.jumpBuffer.set(state.jumpBuffer.get() + 1)
        }

        if (driver.zza == 0f && (vehicle.isInWater || vehicle.isUnderWater)) {
            if (state.staminaBuffer.get() >= 20) {
                val staminaIncrease = vehicle.getRideStat(RidingStat.STAMINA, RidingStyle.LIQUID, 1.0, 2.0)
                state.stamina.set(min(1.0f, state.stamina.get() + 0.01f * staminaIncrease.toFloat()))
            }
        }
        else if (abs(state.rideVelocity.get().z) > 0.1) {
            consumeStamina(vehicle, state, 0.01f)
        }
        state.staminaBuffer.set(state.staminaBuffer.get() + 1)
        super.tick(settings, state, vehicle, driver, input)
    }

    override fun pose(settings: BoatSettings, state: BoatState, vehicle: PokemonEntity): PoseType {
        return poseProvider.select(settings, state, vehicle)
    }

    override fun speed(settings: BoatSettings, state: BoatState, vehicle: PokemonEntity, driver: Player): Float {
        return state.rideVelocity.get().length().toFloat()
    }

    override fun rotation(
        settings: BoatSettings,
        state: BoatState,
        vehicle: PokemonEntity,
        driver: LivingEntity
    ): Vec2 {
        state.deltaRotation.set(state.deltaRotation.get() * 0.8)
        return Vec2(vehicle.xRot, vehicle.yRot + state.deltaRotation.get().toFloat())
    }

    override fun velocity(
        settings: BoatSettings,
        state: BoatState,
        vehicle: PokemonEntity,
        driver: Player,
        input: Vec3
    ): Vec3 {
        applyStrafeRotation(vehicle, driver, state)

        var velocity = state.rideVelocity.get()
        velocity = applyVelocityFromInput(velocity, vehicle, driver, state)
        velocity = applyGravity(velocity, vehicle, settings, state)
        velocity = applyJump(velocity, vehicle, driver, state)
        state.rideVelocity.set(velocity)
        return state.rideVelocity.get()
    }

    private fun applyVelocityFromInput(velocity: Vec3, vehicle: PokemonEntity, driver: Player, state: BoatState): Vec3 {
        val speed = vehicle.getRideStat(RidingStat.SPEED, RidingStyle.LIQUID, 0.8, TOP_SPEED)
        val acceleration = vehicle.getRideStat(RidingStat.ACCELERATION, RidingStyle.LIQUID, 0.05, 0.5)

        if (state.jumpBuffer.get() != -1 || !(vehicle.isInWater || vehicle.isUnderWater)) {
            return velocity
        }

        val forwardInput = driver.zza.toDouble()
        val delta = when {
            !hasStamina(state) || forwardInput == 0.0 -> -velocity.z * 0.04
            forwardInput < 0.0 -> when {
                velocity.z < 0.0 -> forwardInput * acceleration * 0.5
                else -> min(forwardInput * acceleration * 0.5, -velocity.z * 0.04) // We never want it to be slower to reverse than no input
            }
            else -> forwardInput * acceleration
        }

        return Vec3(
            velocity.x,
            velocity.y,
            Mth.clamp(velocity.z + delta, -speed * 0.25, speed)
        )
    }

    private fun applyJump(velocity: Vec3, vehicle: PokemonEntity, driver: Player, state: BoatState): Vec3 {
        if (!driver.jumping) return velocity // Not jumping
        if (state.jumpBuffer.get() != -1) return velocity // Already jumped very recently
        if (!hasStamina(state)) return velocity // Has no stamina

        val jumpStrength = vehicle.getRideStat(RidingStat.JUMP, RidingStyle.LIQUID, 1.0, 2.5)
        state.jumpBuffer.set(0)
        consumeStamina(vehicle, state, 0.05f)
        return Vec3(velocity.x, velocity.y + jumpStrength, velocity.z)
    }

    private fun applyGravity(velocity: Vec3, vehicle: PokemonEntity, settings: BoatSettings, state: BoatState): Vec3 {
        if (state.jumpBuffer.get() == -1 && vehicle.isInWater || vehicle.isUnderWater) {
            return Vec3(velocity.x, -0.05, velocity.z)
        }
        val terminalVelocity = vehicle.runtime.resolveDouble(settings.terminalVelocity)
        val gravity = (9.8 / ( 20.0)) * 0.2
        return Vec3(velocity.x, max(velocity.y - gravity, terminalVelocity), velocity.z)
    }

    private fun applyStrafeRotation(vehicle: PokemonEntity, driver: Player, state: BoatState) {
        val skill = vehicle.getRideStat(RidingStat.SKILL, RidingStyle.LIQUID, 0.5, 1.5)
        val strafe = driver.xxa
        if (abs(strafe) > 0) {
            val increase = if (strafe > 0) -1 else 1
            state.deltaRotation.set(state.deltaRotation.get() + increase * skill)
        }
    }

    private fun hasStamina(state: BoatState): Boolean {
        return state.stamina.get() != 0f
    }

    private fun consumeStamina(vehicle: PokemonEntity, state: BoatState, drain: Float) {
        val stamina = vehicle.getRideStat(RidingStat.STAMINA, RidingStyle.LIQUID, 1.0, 5.0)
        state.stamina.set(max(0f, state.stamina.get() - drain / stamina.toFloat()))
        state.staminaBuffer.set(0)
    }

    override fun updatePassengerRotation(
        settings: BoatSettings,
        state: BoatState,
        vehicle: PokemonEntity,
        driver: LivingEntity
    ) {
        driver.yRot += state.deltaRotation.get().toFloat() * 0.887f
        driver.yHeadRot += state.deltaRotation.get().toFloat() * 0.887f
        clampPassengerRotation(settings, state, vehicle, driver)
    }

    override fun clampPassengerRotation(
        settings: BoatSettings,
        state: BoatState,
        vehicle: PokemonEntity,
        driver: LivingEntity
    ) {
        driver.setYBodyRot(vehicle.yRot)
        val f: Float = Mth.wrapDegrees(driver.yRot - vehicle.yRot)
        val g = Mth.clamp(f, -105.0f, 105.0f)
        driver.yRotO += g - f
        driver.yRot = driver.yRot + g - f
        driver.setYHeadRot(driver.yRot)
        vehicle.setYHeadRot(driver.yRot)
    }

    override fun angRollVel(
        settings: BoatSettings,
        state: BoatState,
        vehicle: PokemonEntity,
        driver: Player,
        deltaTime: Double
    ): Vec3 {
        return Vec3.ZERO
    }

    override fun rotationOnMouseXY(
        settings: BoatSettings,
        state: BoatState,
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
        settings: BoatSettings,
        state: BoatState,
        vehicle: PokemonEntity,
        driver: Player
    ): Boolean {
        return false
    }

    override fun setRideBar(
        settings: BoatSettings,
        state: BoatState,
        vehicle: PokemonEntity,
        driver: Player
    ): Float {
        return (state.stamina.get() / 1.0f)
    }

    override fun jumpForce(
        settings: BoatSettings,
        state: BoatState,
        vehicle: PokemonEntity,
        driver: Player,
        jumpStrength: Int
    ): Vec3 {
        return Vec3.ZERO
    }

    override fun gravity(
        settings: BoatSettings,
        state: BoatState,
        vehicle: PokemonEntity,
        regularGravity: Double
    ): Double {
        return 0.0
    }

    override fun rideFovMultiplier(
        settings: BoatSettings,
        state: BoatState,
        vehicle: PokemonEntity,
        driver: Player
    ): Float {
        if (state.rideVelocity.get().z > TOP_SPEED * 0.8) {
            return Mth.lerp((state.rideVelocity.get().z - (TOP_SPEED * 0.8)) / (TOP_SPEED * 0.2), 1.0, 1.2).toFloat()
        }
        return 1.0f
    }

    override fun useAngVelSmoothing(settings: BoatSettings, state: BoatState, vehicle: PokemonEntity): Boolean {
        return false
    }

    override fun useRidingAltPose(
        settings: BoatSettings,
        state: BoatState,
        vehicle: PokemonEntity,
        driver: Player
    ): ResourceLocation {
        return cobblemonResource("no_pose")
    }

    override fun inertia(settings: BoatSettings, state: BoatState, vehicle: PokemonEntity): Double {
        return 0.5
    }

    override fun shouldRoll(settings: BoatSettings, state: BoatState, vehicle: PokemonEntity): Boolean {
        return false
    }

    override fun turnOffOnGround(settings: BoatSettings, state: BoatState, vehicle: PokemonEntity): Boolean {
        return false
    }

    override fun dismountOnShift(settings: BoatSettings, state: BoatState, vehicle: PokemonEntity): Boolean {
        return false
    }

    override fun shouldRotatePokemonHead(
        settings: BoatSettings,
        state: BoatState,
        vehicle: PokemonEntity
    ): Boolean {
        return vehicle.runtime.resolveBoolean(settings.rotatePokemonHead)
    }

    override fun shouldRotatePlayerHead(
        settings: BoatSettings,
        state: BoatState,
        vehicle: PokemonEntity
    ): Boolean {
        return true
    }

    override fun maxUpStep(settings: BoatSettings, state: BoatState, vehicle: PokemonEntity) = 0f

    override fun createDefaultState(settings: BoatSettings) = BoatState()

}

class BoatSettings : RidingBehaviourSettings {
    override val key = BoatBehaviour.KEY

    var terminalVelocity = "-2.0".asExpression()
        private set

    var rotatePokemonHead = "true".asExpression()
        private set

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeResourceLocation(key)
        buffer.writeExpression(terminalVelocity)
    }

    override fun decode(buffer: RegistryFriendlyByteBuf) {
        terminalVelocity = buffer.readExpression()
    }
}

class BoatState : RidingBehaviourState() {
    val deltaRotation = ridingState(0.0, Side.CLIENT)
    val jumpBuffer = ridingState(-1, Side.CLIENT)
    val staminaBuffer = ridingState(0, Side.CLIENT)

    override fun copy() = BoatState().also {
        it.deltaRotation.set(deltaRotation.get(), true)
        it.rideVelocity.set(rideVelocity.get(), true)
        it.stamina.set(stamina.get(), true)
        it.jumpBuffer.set(jumpBuffer.get(), true)
        it.staminaBuffer.set(staminaBuffer.get(), true)
    }

    override fun shouldSync(previous: RidingBehaviourState): Boolean {
        if (previous !is BoatState) return false
        return super.shouldSync(previous)
    }

}
