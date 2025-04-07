/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.riding.behaviour.types

import com.bedrockk.molang.Expression
import com.cobblemon.mod.common.api.riding.behaviour.*
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.entity.pokemon.PokemonBehaviourFlag
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.asExpression
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.readExpression
import com.cobblemon.mod.common.util.writeExpression
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.SmoothDouble
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3

class RunToJetCompositeBehaviour : RidingBehaviour<RunToJetCompositeSettings, RunToJetCompositeState> {
    companion object {
        val KEY = cobblemonResource("composite/run_to_jet")
    }

    override val key = KEY

    val landBehaviour: GenericLandBehaviour = GenericLandBehaviour()
    val jetBehaviour: JetAirBehaviour = JetAirBehaviour()

    override fun tick(
        settings: RunToJetCompositeSettings,
        state: RunToJetCompositeState,
        vehicle: PokemonEntity,
        driver: Player,
        input: Vec3
    ) {
        val flightState = state.flightState
        val groundState = state.landState
        if (shouldTransitionToGround(state, vehicle, driver)) {
            groundState.currSpeed = flightState.currSpeed
            groundState.stamina = flightState.stamina
            groundState.rideVel = flightState.rideVel

            state.activeController.set(GenericLandBehaviour.KEY)
            vehicle.setBehaviourFlag(PokemonBehaviourFlag.FLYING, false)
            state.timeTransitioned.set(vehicle.level().gameTime)
        } else if (shouldTransitionToAir(state, vehicle, driver, input)) {
            flightState.currSpeed = groundState.currSpeed
            flightState.stamina = groundState.stamina
            flightState.rideVel = groundState.rideVel

            state.activeController.set(JetAirBehaviour.KEY)
            vehicle.setBehaviourFlag(PokemonBehaviourFlag.FLYING, true)
            state.timeTransitioned.set(vehicle.level().gameTime)
        }
    }

    private fun shouldTransitionToGround(
        state: RunToJetCompositeState,
        entity: PokemonEntity,
        driver: Player
    ): Boolean {
        if (state.activeController.get() != JetAirBehaviour.KEY) return false
        if (state.timeTransitioned.get() + 20 >= entity.level().gameTime) return false
        return driver.isSprinting || entity.onGround()
    }

    private fun shouldTransitionToAir(
        state: RunToJetCompositeState,
        entity: PokemonEntity,
        driver: Player,
        input: Vec3
    ): Boolean {
        if (state.activeController.get() != GenericLandBehaviour.KEY) return false
        if (state.timeTransitioned.get() + 20 >= entity.level().gameTime) return false
        return driver.isSprinting && input.z > 0.5
    }

    override fun isActive(
        settings: RunToJetCompositeSettings,
        state: RunToJetCompositeState,
        vehicle: PokemonEntity
    ): Boolean {
        return true
    }

    override fun pose(
        settings: RunToJetCompositeSettings,
        state: RunToJetCompositeState,
        vehicle: PokemonEntity
    ): PoseType {
        return when (state.activeController.get()) {
            GenericLandBehaviour.KEY -> landBehaviour.pose(settings.land, state.landState, vehicle)
            JetAirBehaviour.KEY -> jetBehaviour.pose(settings.jet, state.flightState, vehicle)
            else -> error("Invalid controller: ${state.activeController.get()}")
        }
    }

    override fun speed(
        settings: RunToJetCompositeSettings,
        state: RunToJetCompositeState,
        vehicle: PokemonEntity,
        driver: Player
    ): Float {
        return when (state.activeController.get()) {
            GenericLandBehaviour.KEY -> landBehaviour.speed(settings.land, state.landState, vehicle, driver)
            JetAirBehaviour.KEY -> jetBehaviour.speed(settings.jet, state.flightState, vehicle, driver)
            else -> error("Invalid controller: ${state.activeController.get()}")
        }
    }

    override fun rotation(
        settings: RunToJetCompositeSettings,
        state: RunToJetCompositeState,
        vehicle: PokemonEntity,
        driver: LivingEntity
    ): Vec2 {
        return when (state.activeController.get()) {
            GenericLandBehaviour.KEY -> landBehaviour.rotation(settings.land, state.landState, vehicle, driver)
            JetAirBehaviour.KEY -> jetBehaviour.rotation(settings.jet, state.flightState, vehicle, driver)
            else -> error("Invalid controller: ${state.activeController.get()}")
        }
    }

    override fun velocity(
        settings: RunToJetCompositeSettings,
        state: RunToJetCompositeState,
        vehicle: PokemonEntity,
        driver: Player,
        input: Vec3
    ): Vec3 {
        return when (state.activeController.get()) {
            GenericLandBehaviour.KEY -> landBehaviour.velocity(settings.land, state.landState, vehicle, driver, input)
            JetAirBehaviour.KEY -> jetBehaviour.velocity(settings.jet, state.flightState, vehicle, driver, input)
            else -> error("Invalid controller: ${state.activeController.get()}")
        }
    }

    override fun angRollVel(
        settings: RunToJetCompositeSettings,
        state: RunToJetCompositeState,
        vehicle: PokemonEntity,
        driver: Player,
        deltaTime: Double
    ): Vec3 {
        return when (state.activeController.get()) {
            GenericLandBehaviour.KEY -> landBehaviour.angRollVel(
                settings.land,
                state.landState,
                vehicle,
                driver,
                deltaTime
            )

            JetAirBehaviour.KEY -> jetBehaviour.angRollVel(settings.jet, state.flightState, vehicle, driver, deltaTime)
            else -> error("Invalid controller: ${state.activeController.get()}")
        }
    }

    override fun rotationOnMouseXY(
        settings: RunToJetCompositeSettings,
        state: RunToJetCompositeState,
        vehicle: PokemonEntity,
        driver: Player,
        mouseY: Double,
        mouseX: Double,
        mouseYSmoother: SmoothDouble,
        mouseXSmoother: SmoothDouble,
        sensitivity: Double,
        deltaTime: Double
    ): Vec3 {
        return when (state.activeController.get()) {
            GenericLandBehaviour.KEY -> landBehaviour.rotationOnMouseXY(
                settings.land,
                state.landState,
                vehicle,
                driver,
                mouseY,
                mouseX,
                mouseYSmoother,
                mouseXSmoother,
                sensitivity,
                deltaTime
            )
            JetAirBehaviour.KEY -> jetBehaviour.rotationOnMouseXY(
                settings.jet,
                state.flightState,
                vehicle,
                driver,
                mouseY,
                mouseX,
                mouseYSmoother,
                mouseXSmoother,
                sensitivity,
                deltaTime
            )

            else -> error("Invalid controller: ${state.activeController.get()}")
        }
    }

    override fun canJump(
        settings: RunToJetCompositeSettings,
        state: RunToJetCompositeState,
        vehicle: PokemonEntity,
        driver: Player
    ): Boolean {
        return when (state.activeController.get()) {
            GenericLandBehaviour.KEY -> landBehaviour.canJump(settings.land, state.landState, vehicle, driver)
            JetAirBehaviour.KEY -> jetBehaviour.canJump(settings.jet, state.flightState, vehicle, driver)
            else -> error("Invalid controller: ${state.activeController.get()}")
        }
    }

    override fun setRideBar(
        settings: RunToJetCompositeSettings,
        state: RunToJetCompositeState,
        vehicle: PokemonEntity,
        driver: Player
    ): Float {
        return when (state.activeController.get()) {
            GenericLandBehaviour.KEY -> landBehaviour.setRideBar(settings.land, state.landState, vehicle, driver)
            JetAirBehaviour.KEY -> jetBehaviour.setRideBar(settings.jet, state.flightState, vehicle, driver)
            else -> error("Invalid controller: ${state.activeController.get()}")
        }
    }

    override fun jumpForce(
        settings: RunToJetCompositeSettings,
        state: RunToJetCompositeState,
        vehicle: PokemonEntity,
        driver: Player,
        jumpStrength: Int
    ): Vec3 {
        return when (state.activeController.get()) {
            GenericLandBehaviour.KEY -> landBehaviour.jumpForce(settings.land, state.landState, vehicle, driver, jumpStrength)
            JetAirBehaviour.KEY -> jetBehaviour.jumpForce(settings.jet, state.flightState, vehicle, driver, jumpStrength)
            else -> error("Invalid controller: ${state.activeController.get()}")
        }
    }

    override fun gravity(
        settings: RunToJetCompositeSettings,
        state: RunToJetCompositeState,
        vehicle: PokemonEntity,
        regularGravity: Double
    ): Double {
        return when (state.activeController.get()) {
            GenericLandBehaviour.KEY -> landBehaviour.gravity(settings.land, state.landState, vehicle, regularGravity)
            JetAirBehaviour.KEY -> jetBehaviour.gravity(settings.jet, state.flightState, vehicle, regularGravity)
            else -> error("Invalid controller: ${state.activeController.get()}")
        }
    }

    override fun rideFovMultiplier(
        settings: RunToJetCompositeSettings,
        state: RunToJetCompositeState,
        vehicle: PokemonEntity,
        driver: Player
    ): Float {
        return when (state.activeController.get()) {
            GenericLandBehaviour.KEY -> landBehaviour.rideFovMultiplier(settings.land, state.landState, vehicle, driver)
            JetAirBehaviour.KEY -> jetBehaviour.rideFovMultiplier(settings.jet, state.flightState, vehicle, driver)
            else -> error("Invalid controller: ${state.activeController.get()}")
        }
    }

    override fun useAngVelSmoothing(
        settings: RunToJetCompositeSettings,
        state: RunToJetCompositeState,
        vehicle: PokemonEntity
    ): Boolean {
        return when (state.activeController.get()) {
            GenericLandBehaviour.KEY -> landBehaviour.useAngVelSmoothing(settings.land, state.landState, vehicle)
            JetAirBehaviour.KEY -> jetBehaviour.useAngVelSmoothing(settings.jet, state.flightState, vehicle)
            else -> error("Invalid controller: ${state.activeController.get()}")
        }
    }

    override fun useRidingAltPose(
        settings: RunToJetCompositeSettings,
        state: RunToJetCompositeState,
        vehicle: PokemonEntity,
        driver: Player
    ): Boolean {
        return when (state.activeController.get()) {
            GenericLandBehaviour.KEY -> landBehaviour.useRidingAltPose(settings.land, state.landState, vehicle, driver)
            JetAirBehaviour.KEY -> jetBehaviour.useRidingAltPose(settings.jet, state.flightState, vehicle, driver)
            else -> error("Invalid controller: ${state.activeController.get()}")
        }
    }

    override fun inertia(
        settings: RunToJetCompositeSettings,
        state: RunToJetCompositeState,
        vehicle: PokemonEntity
    ): Double {
        return when (state.activeController.get()) {
            GenericLandBehaviour.KEY -> landBehaviour.inertia(settings.land, state.landState, vehicle)
            JetAirBehaviour.KEY -> jetBehaviour.inertia(settings.jet, state.flightState, vehicle)
            else -> error("Invalid controller: ${state.activeController.get()}")
        }
    }

    override fun shouldRoll(
        settings: RunToJetCompositeSettings,
        state: RunToJetCompositeState,
        vehicle: PokemonEntity
    ): Boolean {
        return when (state.activeController.get()) {
            GenericLandBehaviour.KEY -> landBehaviour.shouldRoll(settings.land, state.landState, vehicle)
            JetAirBehaviour.KEY -> jetBehaviour.shouldRoll(settings.jet, state.flightState, vehicle)
            else -> error("Invalid controller: ${state.activeController.get()}")
        }
    }

    override fun turnOffOnGround(
        settings: RunToJetCompositeSettings,
        state: RunToJetCompositeState,
        vehicle: PokemonEntity
    ): Boolean {
        return when (state.activeController.get()) {
            GenericLandBehaviour.KEY -> landBehaviour.turnOffOnGround(settings.land, state.landState, vehicle)
            JetAirBehaviour.KEY -> jetBehaviour.turnOffOnGround(settings.jet, state.flightState, vehicle)
            else -> error("Invalid controller: ${state.activeController.get()}")
        }
    }

    override fun dismountOnShift(
        settings: RunToJetCompositeSettings,
        state: RunToJetCompositeState,
        vehicle: PokemonEntity
    ): Boolean {
        return when (state.activeController.get()) {
            GenericLandBehaviour.KEY -> landBehaviour.dismountOnShift(settings.land, state.landState, vehicle)
            JetAirBehaviour.KEY -> jetBehaviour.dismountOnShift(settings.jet, state.flightState, vehicle)
            else -> error("Invalid controller: ${state.activeController.get()}")
        }
    }

    override fun shouldRotatePokemonHead(
        settings: RunToJetCompositeSettings,
        state: RunToJetCompositeState,
        vehicle: PokemonEntity
    ): Boolean {
        return when (state.activeController.get()) {
            GenericLandBehaviour.KEY -> landBehaviour.shouldRotatePokemonHead(settings.land, state.landState, vehicle)
            JetAirBehaviour.KEY -> jetBehaviour.shouldRotatePokemonHead(settings.jet, state.flightState, vehicle)
            else -> error("Invalid controller: ${state.activeController.get()}")
        }
    }

    override fun shouldRotatePlayerHead(
        settings: RunToJetCompositeSettings,
        state: RunToJetCompositeState,
        vehicle: PokemonEntity
    ): Boolean {
        return when (state.activeController.get()) {
            GenericLandBehaviour.KEY -> landBehaviour.shouldRotatePlayerHead(settings.land, state.landState, vehicle)
            JetAirBehaviour.KEY -> jetBehaviour.shouldRotatePlayerHead(settings.jet, state.flightState, vehicle)
            else -> error("Invalid controller: ${state.activeController.get()}")
        }
    }

    override fun createDefaultState() = RunToJetCompositeState()
}

class RunToJetCompositeSettings : RidingBehaviourSettings {
    override val key = RunToJetCompositeBehaviour.KEY

    var jet: JetAirSettings = JetAirSettings()
        private set

    var land: GenericLandSettings = GenericLandSettings()
        private set

    var minimumSpeed: Expression = "0.5".asExpression()
        private set

    var minimumJump: Expression = "0.5".asExpression()
        private set

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        jet.encode(buffer)
        land.encode(buffer)
        buffer.writeExpression(minimumSpeed)
        buffer.writeExpression(minimumJump)
    }

    override fun decode(buffer: RegistryFriendlyByteBuf) {
        jet.decode(buffer)
        land.decode(buffer)
        minimumSpeed = buffer.readExpression()
        minimumJump = buffer.readExpression()
    }
}

class RunToJetCompositeState : RidingBehaviourState {
    var activeController = ridingState(GenericLandBehaviour.KEY, Side.CLIENT)
    var landState: GenericLandState = GenericLandState()
    var flightState: JetAirState = JetAirState()
    var currSpeed = ridingState(0.0, Side.BOTH)
    var timeTransitioned = ridingState(-100L, Side.BOTH)

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeResourceLocation(activeController.get())
        landState.encode(buffer)
        flightState.encode(buffer)
    }

    override fun decode(buffer: RegistryFriendlyByteBuf) {
        activeController.set(buffer.readResourceLocation(), forced = true)
        landState.decode(buffer)
        flightState.decode(buffer)
    }

    override fun reset() {
        activeController.set(GenericLandBehaviour.KEY, forced = true)
        timeTransitioned.set(-100L, forced = true)
        currSpeed.set(0.0, forced = true)
    }

    override fun toString(): String {
        return "RunToJetCompositeState(activeController=${activeController.get()}, landState=$landState, flightState=$flightState, currSpeed=${currSpeed.get()}, timeTransitioned=${timeTransitioned.get()})"
    }

    override fun copy() = RunToJetCompositeState().also {
        it.activeController.set(activeController.get(), forced = true)
        it.landState = landState.copy()
        it.flightState = flightState.copy()
        it.currSpeed.set(currSpeed.get(), forced = true)
        it.timeTransitioned.set(timeTransitioned.get(), forced = true)
    }

    override fun shouldSync(previous: RidingBehaviourState): Boolean {
        if (previous !is RunToJetCompositeState) return false
        if (activeController != previous.activeController) return true
        if (landState.shouldSync(previous.landState)) return true
        if (flightState.shouldSync(previous.flightState)) return true
        return false
    }
}
