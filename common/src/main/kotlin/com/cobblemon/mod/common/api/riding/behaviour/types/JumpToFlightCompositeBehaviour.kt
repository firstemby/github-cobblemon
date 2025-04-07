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

class JumpToFlightCompositeBehaviour : RidingBehaviour<JumpToFlightCompositeSettings, JumpToFlightCompositeState> {
    companion object {
        val KEY = cobblemonResource("composite/jump_to_flight")
    }

    override val key = KEY

    val landBehaviour: GenericLandBehaviour = GenericLandBehaviour()
    val birdBehaviour: BirdAirBehaviour = BirdAirBehaviour()

    override fun tick(
        settings: JumpToFlightCompositeSettings,
        state: JumpToFlightCompositeState,
        vehicle: PokemonEntity,
        driver: Player,
        input: Vec3
    ) {
        val flightState = state.flightState
        val groundState = state.landState
        if (shouldTransitionToGround(state, vehicle)) {
            //Pass the speed to the next state
//            groundState.currSpeed = flightState.currSpeed
            groundState.rideVel.set(flightState.rideVel.get())
            groundState.stamina.set(flightState.stamina.get())

            state.activeController.set(GenericLandBehaviour.KEY)
            vehicle.setBehaviourFlag(PokemonBehaviourFlag.FLYING, false)
            state.timeTransitioned.set(vehicle.level().gameTime)
        } else if (shouldTransitionToAir(state, vehicle, driver)) {
            //Pass the speed to the next state
//            flightState.currSpeed = groundState.currSpeed
            flightState.rideVel.set(groundState.rideVel.get())
            flightState.stamina.set(groundState.stamina.get())

            state.activeController.set(BirdAirBehaviour.KEY)
            vehicle.setBehaviourFlag(PokemonBehaviourFlag.FLYING, true)
            state.timeTransitioned.set(vehicle.level().gameTime)
        }
    }

    override fun isActive(
        settings: JumpToFlightCompositeSettings,
        state: JumpToFlightCompositeState,
        vehicle: PokemonEntity
    ): Boolean {
        return true
    }

    private fun shouldTransitionToGround(state: JumpToFlightCompositeState, entity: PokemonEntity): Boolean {
        return state.activeController.get() == BirdAirBehaviour.KEY
                && entity.onGround()
                && state.timeTransitioned.get() + 20 < entity.level().gameTime
    }

    private fun shouldTransitionToAir(
        state: JumpToFlightCompositeState,
        entity: PokemonEntity,
        driver: Player
    ): Boolean {
        return state.activeController.get() == GenericLandBehaviour.KEY
                && driver.jumping
                && state.timeTransitioned.get() + 20 < entity.level().gameTime
    }

    override fun pose(
        settings: JumpToFlightCompositeSettings,
        state: JumpToFlightCompositeState,
        vehicle: PokemonEntity
    ): PoseType {
        return when (state.activeController.get()) {
            GenericLandBehaviour.KEY -> landBehaviour.pose(settings.land, state.landState, vehicle)
            BirdAirBehaviour.KEY -> birdBehaviour.pose(settings.bird, state.flightState, vehicle)
            else -> error("Invalid controller: ${state.activeController.get()}")
        }
    }

    override fun speed(
        settings: JumpToFlightCompositeSettings,
        state: JumpToFlightCompositeState,
        vehicle: PokemonEntity,
        driver: Player
    ): Float {
        return when (state.activeController.get()) {
            GenericLandBehaviour.KEY -> landBehaviour.speed(settings.land, state.landState, vehicle, driver)
            BirdAirBehaviour.KEY -> birdBehaviour.speed(settings.bird, state.flightState, vehicle, driver)
            else -> error("Invalid controller: ${state.activeController.get()}")
        }
    }

    override fun rotation(
        settings: JumpToFlightCompositeSettings,
        state: JumpToFlightCompositeState,
        vehicle: PokemonEntity,
        driver: LivingEntity
    ): Vec2 {
        return when (state.activeController.get()) {
            GenericLandBehaviour.KEY -> landBehaviour.rotation(settings.land, state.landState, vehicle, driver)
            BirdAirBehaviour.KEY -> birdBehaviour.rotation(settings.bird, state.flightState, vehicle, driver)
            else -> error("Invalid controller: ${state.activeController.get()}")
        }
    }

    override fun velocity(
        settings: JumpToFlightCompositeSettings,
        state: JumpToFlightCompositeState,
        vehicle: PokemonEntity,
        driver: Player,
        input: Vec3
    ): Vec3 {
        return when (state.activeController.get()) {
            GenericLandBehaviour.KEY -> landBehaviour.velocity(settings.land, state.landState, vehicle, driver, input)
            BirdAirBehaviour.KEY -> birdBehaviour.velocity(settings.bird, state.flightState, vehicle, driver, input)
            else -> error("Invalid controller: ${state.activeController.get()}")
        }
    }

    override fun angRollVel(
        settings: JumpToFlightCompositeSettings,
        state: JumpToFlightCompositeState,
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

            BirdAirBehaviour.KEY -> birdBehaviour.angRollVel(
                settings.bird,
                state.flightState,
                vehicle,
                driver,
                deltaTime
            )

            else -> error("Invalid controller: ${state.activeController.get()}")
        }
    }

    override fun rotationOnMouseXY(
        settings: JumpToFlightCompositeSettings,
        state: JumpToFlightCompositeState,
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

            BirdAirBehaviour.KEY -> birdBehaviour.rotationOnMouseXY(
                settings.bird,
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
        settings: JumpToFlightCompositeSettings,
        state: JumpToFlightCompositeState,
        vehicle: PokemonEntity,
        driver: Player
    ): Boolean {
        return when (state.activeController.get()) {
            GenericLandBehaviour.KEY -> landBehaviour.canJump(settings.land, state.landState, vehicle, driver)
            BirdAirBehaviour.KEY -> birdBehaviour.canJump(settings.bird, state.flightState, vehicle, driver)
            else -> error("Invalid controller: ${state.activeController.get()}")
        }
    }

    override fun setRideBar(
        settings: JumpToFlightCompositeSettings,
        state: JumpToFlightCompositeState,
        vehicle: PokemonEntity,
        driver: Player
    ): Float {
        return when (state.activeController.get()) {
            GenericLandBehaviour.KEY -> landBehaviour.setRideBar(settings.land, state.landState, vehicle, driver)
            BirdAirBehaviour.KEY -> birdBehaviour.setRideBar(settings.bird, state.flightState, vehicle, driver)
            else -> error("Invalid controller: ${state.activeController.get()}")
        }
    }

    override fun jumpForce(
        settings: JumpToFlightCompositeSettings,
        state: JumpToFlightCompositeState,
        vehicle: PokemonEntity,
        driver: Player,
        jumpStrength: Int
    ): Vec3 {
        return when (state.activeController.get()) {
            GenericLandBehaviour.KEY -> landBehaviour.jumpForce(
                settings.land,
                state.landState,
                vehicle,
                driver,
                jumpStrength
            )

            BirdAirBehaviour.KEY -> birdBehaviour.jumpForce(
                settings.bird,
                state.flightState,
                vehicle,
                driver,
                jumpStrength
            )

            else -> error("Invalid controller: ${state.activeController.get()}")
        }
    }

    override fun gravity(
        settings: JumpToFlightCompositeSettings,
        state: JumpToFlightCompositeState,
        vehicle: PokemonEntity,
        regularGravity: Double
    ): Double {
        return when (state.activeController.get()) {
            GenericLandBehaviour.KEY -> landBehaviour.gravity(settings.land, state.landState, vehicle, regularGravity)
            BirdAirBehaviour.KEY -> birdBehaviour.gravity(settings.bird, state.flightState, vehicle, regularGravity)
            else -> error("Invalid controller: ${state.activeController.get()}")
        }
    }

    override fun rideFovMultiplier(
        settings: JumpToFlightCompositeSettings,
        state: JumpToFlightCompositeState,
        vehicle: PokemonEntity,
        driver: Player
    ): Float {
        return when (state.activeController.get()) {
            GenericLandBehaviour.KEY -> landBehaviour.rideFovMultiplier(settings.land, state.landState, vehicle, driver)
            BirdAirBehaviour.KEY -> birdBehaviour.rideFovMultiplier(settings.bird, state.flightState, vehicle, driver)
            else -> error("Invalid controller: ${state.activeController.get()}")
        }
    }

    override fun useAngVelSmoothing(
        settings: JumpToFlightCompositeSettings,
        state: JumpToFlightCompositeState,
        vehicle: PokemonEntity
    ): Boolean {
        return when (state.activeController.get()) {
            GenericLandBehaviour.KEY -> landBehaviour.useAngVelSmoothing(settings.land, state.landState, vehicle)
            BirdAirBehaviour.KEY -> birdBehaviour.useAngVelSmoothing(settings.bird, state.flightState, vehicle)
            else -> error("Invalid controller: ${state.activeController.get()}")
        }
    }

    override fun useRidingAltPose(
        settings: JumpToFlightCompositeSettings,
        state: JumpToFlightCompositeState,
        vehicle: PokemonEntity,
        driver: Player
    ): Boolean {
        return when (state.activeController.get()) {
            GenericLandBehaviour.KEY -> landBehaviour.useRidingAltPose(settings.land, state.landState, vehicle, driver)
            BirdAirBehaviour.KEY -> birdBehaviour.useRidingAltPose(settings.bird, state.flightState, vehicle, driver)
            else -> error("Invalid controller: ${state.activeController.get()}")
        }
    }

    override fun inertia(
        settings: JumpToFlightCompositeSettings,
        state: JumpToFlightCompositeState,
        vehicle: PokemonEntity
    ): Double {
        return when (state.activeController.get()) {
            GenericLandBehaviour.KEY -> landBehaviour.inertia(settings.land, state.landState, vehicle)
            BirdAirBehaviour.KEY -> birdBehaviour.inertia(settings.bird, state.flightState, vehicle)
            else -> error("Invalid controller: ${state.activeController.get()}")
        }
    }

    override fun shouldRoll(
        settings: JumpToFlightCompositeSettings,
        state: JumpToFlightCompositeState,
        vehicle: PokemonEntity
    ): Boolean {
        return when (state.activeController.get()) {
            GenericLandBehaviour.KEY -> landBehaviour.shouldRoll(settings.land, state.landState, vehicle)
            BirdAirBehaviour.KEY -> birdBehaviour.shouldRoll(settings.bird, state.flightState, vehicle)
            else -> error("Invalid controller: ${state.activeController.get()}")
        }
    }

    override fun turnOffOnGround(
        settings: JumpToFlightCompositeSettings,
        state: JumpToFlightCompositeState,
        vehicle: PokemonEntity
    ): Boolean {
        return when (state.activeController.get()) {
            GenericLandBehaviour.KEY -> landBehaviour.turnOffOnGround(settings.land, state.landState, vehicle)
            BirdAirBehaviour.KEY -> birdBehaviour.turnOffOnGround(settings.bird, state.flightState, vehicle)
            else -> error("Invalid controller: ${state.activeController.get()}")
        }
    }

    override fun dismountOnShift(
        settings: JumpToFlightCompositeSettings,
        state: JumpToFlightCompositeState,
        vehicle: PokemonEntity
    ): Boolean {
        return when (state.activeController.get()) {
            GenericLandBehaviour.KEY -> landBehaviour.dismountOnShift(settings.land, state.landState, vehicle)
            BirdAirBehaviour.KEY -> birdBehaviour.dismountOnShift(settings.bird, state.flightState, vehicle)
            else -> error("Invalid controller: ${state.activeController.get()}")
        }
    }

    override fun shouldRotatePokemonHead(
        settings: JumpToFlightCompositeSettings,
        state: JumpToFlightCompositeState,
        vehicle: PokemonEntity
    ): Boolean {
        return when (state.activeController.get()) {
            GenericLandBehaviour.KEY -> landBehaviour.shouldRotatePokemonHead(settings.land, state.landState, vehicle)
            BirdAirBehaviour.KEY -> birdBehaviour.shouldRotatePokemonHead(settings.bird, state.flightState, vehicle)
            else -> error("Invalid controller: ${state.activeController.get()}")
        }
    }

    override fun shouldRotatePlayerHead(
        settings: JumpToFlightCompositeSettings,
        state: JumpToFlightCompositeState,
        vehicle: PokemonEntity
    ): Boolean {
        return when (state.activeController.get()) {
            GenericLandBehaviour.KEY -> landBehaviour.shouldRotatePlayerHead(settings.land, state.landState, vehicle)
            BirdAirBehaviour.KEY -> birdBehaviour.shouldRotatePlayerHead(settings.bird, state.flightState, vehicle)
            else -> error("Invalid controller: ${state.activeController.get()}")
        }
    }

    override fun createDefaultState() = JumpToFlightCompositeState()
}

class JumpToFlightCompositeSettings : RidingBehaviourSettings {
    override val key = JumpToFlightCompositeBehaviour.KEY

    var bird: BirdAirSettings = BirdAirSettings()
        private set

    var land: GenericLandSettings = GenericLandSettings()
        private set

    var minimumSpeed: Expression = "0.5".asExpression()
        private set

    var minimumJump: Expression = "0.5".asExpression()
        private set

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        bird.encode(buffer)
        land.encode(buffer)
        buffer.writeExpression(minimumSpeed)
        buffer.writeExpression(minimumJump)
    }

    override fun decode(buffer: RegistryFriendlyByteBuf) {
        bird.decode(buffer)
        land.decode(buffer)
        minimumSpeed = buffer.readExpression()
        minimumJump = buffer.readExpression()
    }
}

class JumpToFlightCompositeState : RidingBehaviourState {
    var activeController = ridingState(GenericLandBehaviour.KEY, Side.CLIENT)
    var currSpeed = ridingState(0.0, Side.BOTH)
    var timeTransitioned = ridingState(-100L, Side.BOTH)
    var landState = GenericLandState()
    var flightState = BirdAirState()

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
        return "JumpToFlightCompositeState(activeController=${activeController.get()}, landState=$landState, flightState=$flightState, currSpeed=${currSpeed.get()}, timeTransitioned=${timeTransitioned.get()})"
    }

    override fun copy() = JumpToFlightCompositeState().also {
        it.activeController.set(activeController.get(), forced = true)
        it.landState = landState.copy()
        it.flightState = flightState.copy()
        it.currSpeed.set(currSpeed.get(), forced = true)
        it.timeTransitioned.set(timeTransitioned.get(), forced = true)
    }

    override fun shouldSync(previous: RidingBehaviourState): Boolean {
        if (previous !is JumpToFlightCompositeState) return false
        if (activeController != previous.activeController) return true
        if (landState.shouldSync(previous.landState)) return true
        if (flightState.shouldSync(previous.flightState)) return true
        return false
    }
}
