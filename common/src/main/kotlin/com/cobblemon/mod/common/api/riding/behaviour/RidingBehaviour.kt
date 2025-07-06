/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.riding.behaviour

import com.cobblemon.mod.common.api.riding.RidingStyle
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.SmoothDouble
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3

/**
 * Represents the behaviour of a Pokemon when being ridden.
 * This is intended to contain the logic for how a Pokemon should behave when being ridden.
 *
 * This class should remain stateless as it is intended to be shared between multiple instances of Pokemon.
 *
 * @author landonjw
 */
interface RidingBehaviour<Settings : RidingBehaviourSettings, State : RidingBehaviourState> {
    val key: ResourceLocation

    fun getRidingStyle(settings: Settings, state: State): RidingStyle

    fun isActive(settings: Settings, state: State, vehicle: PokemonEntity): Boolean

    fun tick(settings: Settings, state: State, vehicle: PokemonEntity, driver: Player, input: Vec3) {}

    fun pose(settings: Settings, state: State, vehicle: PokemonEntity): PoseType

    fun speed(settings: Settings, state: State, vehicle: PokemonEntity, driver: Player): Float

    fun clampPassengerRotation(settings: Settings, state: State, vehicle: PokemonEntity, driver: LivingEntity) {}

    fun updatePassengerRotation(settings: Settings, state: State, vehicle: PokemonEntity, driver: LivingEntity) {}

    fun rotation(settings: Settings, state: State, vehicle: PokemonEntity, driver: LivingEntity): Vec2

    fun velocity(settings: Settings, state: State, vehicle: PokemonEntity, driver: Player, input: Vec3): Vec3

    fun angRollVel(settings: Settings, state: State, vehicle: PokemonEntity, driver: Player, deltaTime: Double): Vec3

    fun rotationOnMouseXY(
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
    ): Vec3

    fun canJump(settings: Settings, state: State, vehicle: PokemonEntity, driver: Player): Boolean

    fun setRideBar(settings: Settings, state: State, vehicle: PokemonEntity, driver: Player): Float

    fun jumpForce(settings: Settings, state: State, vehicle: PokemonEntity, driver: Player, jumpStrength: Int): Vec3

    fun gravity(settings: Settings, state: State, vehicle: PokemonEntity, regularGravity: Double): Double

    fun rideFovMultiplier(settings: Settings, state: State, vehicle: PokemonEntity, driver: Player): Float

    fun useAngVelSmoothing(settings: Settings, state: State, vehicle: PokemonEntity): Boolean

    fun useRidingAltPose(settings: Settings, state: State, vehicle: PokemonEntity, driver: Player): ResourceLocation

    fun inertia(settings: Settings, state: State, vehicle: PokemonEntity): Double

    fun shouldRoll(settings: Settings, state: State, vehicle: PokemonEntity): Boolean

    fun turnOffOnGround(settings: Settings, state: State, vehicle: PokemonEntity): Boolean

    fun dismountOnShift(settings: Settings, state: State, vehicle: PokemonEntity): Boolean

    fun shouldRotatePokemonHead(settings: Settings, state: State, vehicle: PokemonEntity): Boolean

    fun shouldRotatePlayerHead(settings: Settings, state: State, vehicle: PokemonEntity): Boolean

    fun maxUpStep(settings: Settings, state: State, vehicle: PokemonEntity): Float? = null

    fun canStopRiding(settings: Settings, state: State, vehicle: PokemonEntity, passenger: Player): Boolean = true

    fun createDefaultState(settings: Settings): State

}
