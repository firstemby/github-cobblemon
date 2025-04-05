/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.riding.behaviour

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.SmoothDouble
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3

/**
 * Small wrapper around a RidingBehaviour to provide sane defaults in the event that the behaviour is not active.
 *
 * @author landonjw
 */
class RidingController<Settings : RidingBehaviourSettings, State : RidingBehaviourState>(val behaviour: RidingBehaviour<Settings, State>) : RidingBehaviour<Settings, State> {

    override val key = behaviour.key

    override fun isActive(settings: Settings, state: State, vehicle: PokemonEntity) =
        behaviour.isActive(settings, state, vehicle)

    override fun tick(settings: Settings, state: State, vehicle: PokemonEntity, driver: Player, input: Vec3) =
        behaviour.tick(settings, state, vehicle, driver, input)

    override fun pose(settings: Settings, state: State, vehicle: PokemonEntity) =
        behaviour.pose(settings, state, vehicle)

    override fun speed(settings: Settings, state: State, vehicle: PokemonEntity, driver: Player): Float {
        if (!isActive(settings, state, vehicle)) return 0.0F
        return behaviour.speed(settings, state, vehicle, driver)
    }

    override fun rotation(settings: Settings, state: State, vehicle: PokemonEntity, driver: LivingEntity): Vec2 {
        if (!isActive(settings, state, vehicle)) return vehicle.rotationVector
        return behaviour.rotation(settings, state, vehicle, driver)
    }

    override fun velocity(
        settings: Settings,
        state: State,
        vehicle: PokemonEntity,
        driver: Player,
        input: Vec3
    ): Vec3 {
        if (!isActive(settings, state, vehicle)) return Vec3.ZERO
        return behaviour.velocity(settings, state, vehicle, driver, input)
    }

    override fun angRollVel(
        settings: Settings,
        state: State,
        vehicle: PokemonEntity,
        driver: Player,
        deltaTime: Double
    ): Vec3 {
        if (!isActive(settings, state, vehicle)) return Vec3.ZERO
        return behaviour.angRollVel(settings, state, vehicle, driver, deltaTime)
    }

    override fun rotationOnMouseXY(
        settings: Settings,
        state: State,
        vehicle: PokemonEntity,
        driver: Player,
        mouseY: Double,
        mouseX: Double,
        mouseYSmoother: SmoothDouble,
        mouseXSmoother: SmoothDouble,
        sensitivity: Double,
        deltaTime: Double
    ): Vec3 {
        if (!isActive(settings, state, vehicle)) return Vec3.ZERO
        return behaviour.rotationOnMouseXY(
            settings,
            state,
            vehicle,
            driver,
            mouseY,
            mouseX,
            mouseYSmoother,
            mouseXSmoother,
            sensitivity,
            deltaTime
        )
    }

    override fun canJump(settings: Settings, state: State, vehicle: PokemonEntity, driver: Player): Boolean {
        if (!isActive(settings, state, vehicle)) return false
        return behaviour.canJump(settings, state, vehicle, driver)
    }

    override fun shouldRotatePlayerHead(settings: Settings, state: State, vehicle: PokemonEntity): Boolean {
        if (!isActive(settings, state, vehicle)) return false
        return behaviour.shouldRotatePlayerHead(settings, state, vehicle)
    }

    override fun shouldRotatePokemonHead(settings: Settings, state: State, vehicle: PokemonEntity): Boolean {
        if (!isActive(settings, state, vehicle)) return false
        return behaviour.shouldRotatePokemonHead(settings, state, vehicle)
    }

    override fun dismountOnShift(settings: Settings, state: State, vehicle: PokemonEntity): Boolean {
        if (!isActive(settings, state, vehicle)) return false
        return behaviour.dismountOnShift(settings, state, vehicle)
    }

    override fun turnOffOnGround(settings: Settings, state: State, vehicle: PokemonEntity): Boolean {
        if (!isActive(settings, state, vehicle)) return false
        return behaviour.turnOffOnGround(settings, state, vehicle)
    }

    override fun shouldRoll(settings: Settings, state: State, vehicle: PokemonEntity): Boolean {
        if (!isActive(settings, state, vehicle)) return false
        return behaviour.shouldRoll(settings, state, vehicle)
    }

    override fun inertia(settings: Settings, state: State, vehicle: PokemonEntity): Double {
        if (!isActive(settings, state, vehicle)) return 0.5
        return behaviour.inertia(settings, state, vehicle)
    }

    override fun useRidingAltPose(settings: Settings, state: State, vehicle: PokemonEntity, driver: Player): Boolean {
        if (!isActive(settings, state, vehicle)) return false
        return behaviour.useRidingAltPose(settings, state, vehicle, driver)
    }

    override fun useAngVelSmoothing(settings: Settings, state: State, vehicle: PokemonEntity): Boolean {
        if (!isActive(settings, state, vehicle)) return true
        return behaviour.useAngVelSmoothing(settings, state, vehicle)
    }

    override fun rideFovMultiplier(settings: Settings, state: State, vehicle: PokemonEntity, driver: Player): Float {
        if (!isActive(settings, state, vehicle)) return 1.0F
        return behaviour.rideFovMultiplier(settings, state, vehicle, driver)
    }

    override fun gravity(settings: Settings, state: State, vehicle: PokemonEntity, regularGravity: Double): Double {
        if (!isActive(settings, state, vehicle)) return regularGravity
        return behaviour.gravity(settings, state, vehicle, regularGravity)
    }

    override fun jumpForce(
        settings: Settings,
        state: State,
        vehicle: PokemonEntity,
        driver: Player,
        jumpStrength: Int
    ): Vec3 {
        if (!isActive(settings, state, vehicle)) return Vec3.ZERO
        return behaviour.jumpForce(settings, state, vehicle, driver, jumpStrength)
    }

    override fun setRideBar(settings: Settings, state: State, vehicle: PokemonEntity, driver: Player): Float {
        if (!isActive(settings, state, vehicle)) return 0.0F
        return behaviour.setRideBar(settings, state, vehicle, driver)
    }

    override fun createDefaultState(): State {
        return behaviour.createDefaultState()
    }

}
