/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.riding.controllers

import com.bedrockk.molang.Expression
import com.cobblemon.mod.common.api.riding.controller.RideController
import com.cobblemon.mod.common.api.riding.controller.posing.PoseOption
import com.cobblemon.mod.common.api.riding.controller.posing.PoseProvider
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.entity.pokemon.PokemonBehaviourFlag
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.riding.states.CompositeState
import com.cobblemon.mod.common.util.adapters.riding.RideControllerAdapter
import com.cobblemon.mod.common.util.asExpression
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.getString
import com.cobblemon.mod.common.util.readString
import com.cobblemon.mod.common.util.resolveFloat
import com.cobblemon.mod.common.util.writeString
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3

class FallToFlightCompositeController : RideController {
    override val key = KEY
    override val poseProvider = PoseProvider(PoseType.STAND)
        .with(PoseOption(PoseType.WALK) { it.entityData.get(PokemonEntity.MOVING) })
    override val condition: (PokemonEntity) -> Boolean = { true }

    var minimumForwardSpeed: Expression = "0.5".asExpression()
        private set
    var minimumFallSpeed: Expression = "0.5".asExpression()
        private set

    var landController: RideController = GenericLandController()
        private set
    var flightController: RideController = GliderAirController()
        private set

    override fun pose(entity: PokemonEntity): PoseType {
        return getActiveController(entity).poseProvider.select(entity)
    }

    fun getActiveController(entity: PokemonEntity): RideController {
        val state = getState(entity, ::CompositeState)
        return state.activeController ?: let {
            val controller = if (entity.getCurrentPoseType() in PoseType.FLYING_POSES) {
                entity.setBehaviourFlag(PokemonBehaviourFlag.FLYING, true)
                flightController
            } else {
                entity.setBehaviourFlag(PokemonBehaviourFlag.FLYING, false)
                landController
            }
            state.activeController = controller
            state.timeTransitioned = entity.level().gameTime
            controller
        }
    }

    fun checkShouldBeFlying(entity: PokemonEntity, isFlyingAlready: Boolean): Boolean {
        val runtime = getRuntime(entity)
        val minFallingSpeed = runtime.resolveFloat(minimumFallSpeed)
        val minForwardSpeed = runtime.resolveFloat(minimumForwardSpeed)
        val grounded = entity.onGround()
        return if (isFlyingAlready) {
            !grounded
        } else {
            entity.deltaMovement.y <= -minFallingSpeed && entity.deltaMovement.horizontalDistance() >= minForwardSpeed
        }
    }

    override fun speed(
        entity: PokemonEntity,
        driver: Player
    ): Float {
        val state = getState(entity, ::CompositeState)
        val shouldBeFlying = checkShouldBeFlying(entity, state.activeController == flightController)
        if (state.activeController == flightController && !shouldBeFlying) { // && entity.onGround() && state.timeTransitioned + 20 < entity.level().gameTime) {
            state.activeController = landController
            entity.setBehaviourFlag(PokemonBehaviourFlag.FLYING, false)
        } else if (state.activeController == landController && shouldBeFlying) {
            state.activeController = flightController
            entity.setBehaviourFlag(PokemonBehaviourFlag.FLYING, true)
        }

        return getActiveController(entity).speed(entity, driver)
    }

    override fun rotation(entity: PokemonEntity, driver: LivingEntity): Vec2 {
        return getActiveController(entity).rotation(entity, driver)
    }

    override fun velocity(
        entity: PokemonEntity,
        driver: Player,
        input: Vec3
    ): Vec3 {
        return getActiveController(entity).velocity(entity, driver, input)
    }

    override fun canJump(entity: PokemonEntity, driver: Player) = true

    override fun jumpForce(
        entity: PokemonEntity,
        driver: Player,
        jumpStrength: Int
    ): Vec3 {
        val controller = getActiveController(entity)
        return controller.jumpForce(entity, driver, jumpStrength)
    }

    override fun gravity(entity: PokemonEntity, regularGravity: Double): Double? {
        return getActiveController(entity).gravity(entity, regularGravity)
    }

    override fun shouldRoll(entity: PokemonEntity): Boolean = getActiveController(entity).shouldRoll(entity)

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        super.encode(buffer)
        buffer.writeString(minimumForwardSpeed.getString())
        buffer.writeString(minimumFallSpeed.getString())
        landController.encode(buffer)
        flightController.encode(buffer)
    }

    override fun decode(buffer: RegistryFriendlyByteBuf) {
        minimumForwardSpeed = buffer.readString().asExpression()
        minimumFallSpeed = buffer.readString().asExpression()
        landController = buffer.readResourceLocation().let { key ->
            val controller = RideControllerAdapter.types[key]?.getConstructor()?.newInstance() ?: error("Unknown controller key: $key")
            controller.decode(buffer)
            controller
        }
        flightController = buffer.readResourceLocation().let { key ->
            val controller = RideControllerAdapter.types[key]?.getConstructor()?.newInstance() ?: error("Unknown controller key: $key")
            controller.decode(buffer)
            controller
        }
    }

    companion object {
        val KEY = cobblemonResource("composite/fall_to_flight")
    }
}