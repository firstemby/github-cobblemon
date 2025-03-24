/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.riding

import com.bedrockk.molang.runtime.MoLangRuntime
import com.bedrockk.molang.runtime.value.DoubleValue
import com.cobblemon.mod.common.Rollable
import com.cobblemon.mod.common.api.molang.MoLangFunctions.setup
import com.cobblemon.mod.common.api.riding.controller.RideController
import com.cobblemon.mod.common.api.riding.stats.RidingStat
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.toProperties
import com.cobblemon.mod.common.util.withQueryValue
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.SmoothDouble
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import kotlin.math.sin
import kotlin.math.sin

data class RidingManager(val entity: PokemonEntity) {
    var lastSpeed = 0F
    var states: MutableMap<ResourceLocation, RidingState> = mutableMapOf()
    var deltaRotation = Vec2.ZERO

    val runtime: MoLangRuntime by lazy {
        MoLangRuntime()
            .setup()
            .withQueryValue("entity", entity.struct)
            .also {
                it.environment.query.addFunction("passenger_count") { DoubleValue(entity.passengers.size.toDouble()) }
                it.environment.query.addFunction("get_ride_stats") { params ->
                    val rideStat = RidingStat.valueOf(params.getString(0).uppercase())
                    val rideStyle = RidingStyle.valueOf(params.getString(1).uppercase())
                    val maxVal = params.getDouble(2)
                    val minVal = params.getDouble(3)
                    //TODO: Use the mons actual boost once implemented
                    val normalizedStat = entity.rideProp.calculate(rideStat, rideStyle, 0) / 100.0f
                    val trueStatVal = (normalizedStat *  (maxVal - minVal)) + minVal

                    DoubleValue(trueStatVal)
                }
            }
    }

    fun <T : RidingState> getState(id: ResourceLocation, constructor: (PokemonEntity) -> T): T {
        val storedState = states[id]
        if (storedState == null) {
            val newState = constructor(entity)
            states[id] = newState
            return newState
        }

        return storedState as T
    }

    fun getController(entity: PokemonEntity): RideController? {
        return entity.pokemon.riding.controller?.takeIf { it.condition.invoke(entity) }
    }

    /**
     * Responsible for handling riding conditions and transitions amongst controllers. This will tick
     * whenever the entity receives a tickControlled interaction.
     */
    fun tick(entity: PokemonEntity, driver: Player, input: Vec3) {
        val controller = getController(entity) ?: return

        val pose = controller.pose(entity)
        entity.entityData.set(PokemonEntity.POSE_TYPE, pose)
        //val speedPlayer = driver.deltaMovement.horizontalDistance()
        //driver.displayClientMessage(Component.literal("Speed player: ").withStyle { it.withColor(ChatFormatting.GREEN) }.append(Component.literal("$speedPlayer b/t")), true)
        //val speedEntity = entity.deltaMovement.length()  //* 20 * 60 * 60) / ( 1000 )
        //println(speedEntity)
        //driver.displayClientMessage(Component.literal("Speed: ").withStyle { it.withColor(ChatFormatting.GREEN) }.append(Component.literal("${String.format("%.2f", speedEntity)} km/h")), true)
        
    }

    fun speed(entity: PokemonEntity, driver: Player): Float {
        val controller = getController(entity) ?: return 0.05F
        this.lastSpeed = controller.speed(entity, driver)
        return this.lastSpeed
    }

    fun controlledRotation(entity: PokemonEntity, driver: Player): Vec2 {
        val controller = getController(entity) ?: return entity.rotationVector
        val previousRotation = entity.rotationVector
        val rotation = controller.rotation(entity, driver)
        this.deltaRotation = Vec2(rotation.x - previousRotation.x, rotation.y - previousRotation.y)
        return rotation
    }

    fun clampPassengerRotation(entity: PokemonEntity, driver: LivingEntity) {
        val controller = getController(entity) ?: return
        return controller.clampPassengerRotation(entity, driver)
    }

    fun updatePassengerRotation(entity: PokemonEntity, driver: LivingEntity) {
        val controller = getController(entity) ?: return
        return controller.updatePassengerRotation(entity, driver)
    }

    fun velocity(entity: PokemonEntity, driver: Player, input: Vec3): Vec3 {
        val controller = getController(entity) ?: return Vec3.ZERO
        return controller.velocity(entity, driver, input)
    }

    fun setRideBar(entity: PokemonEntity, driver: Player): Float {
        val controller = getController(entity) ?: return 0.0f
        return controller.setRideBar(entity, driver)
    }

    fun rideFovMult(entity: PokemonEntity, driver: Player): Float {
        val controller = getController(entity) ?: return 1.0f
        return controller.rideFovMult(entity, driver)
    }

    fun canJump(entity: PokemonEntity, driver: Player): Boolean {
        val controller = getController(entity) ?: return false
        return controller.canJump(entity, driver)
    }

    fun jumpVelocity(entity: PokemonEntity, driver: Player, jumpStrength: Int): Vec3 {
        val controller = getController(entity) ?: return Vec3.ZERO
        return controller.jumpForce(entity, driver, jumpStrength)
    }

    fun useRidingAltPose(entity: PokemonEntity, driver: Player): Boolean {
        val controller = getController(entity) ?: return false
        return controller.useRidingAltPose(entity, driver)
    }

    /**
     * Used to supply the delta for the lerp() between velocity
     * vectors. Called inertia for now as the way it feels when
     * affecting the ride is somewhat akin to inertia.
     */
    fun inertia(entity: PokemonEntity): Double {
        val controller = getController(entity) ?: return 0.5
        return controller.inertia(entity)
    }

    fun gravity(entity: PokemonEntity, regularGravity: Double): Double? {
        val controller = getController(entity) ?: return null
        return controller.gravity(entity, regularGravity)
    }

    //Maybe should be changed to be a function that conveys:
    // "turn on use our camera system function"
    fun shouldRoll(entity: PokemonEntity): Boolean {
        val controller = getController(entity) ?: return false
        return controller.shouldRoll(entity)
    }

    fun shouldRotatePlayerHead(entity: PokemonEntity): Boolean {
        val controller = getController(entity) ?: return false
        return controller.shouldRotatePlayerHead()
    }

    fun shouldRotatePokemonHead(entity: PokemonEntity): Boolean {
        val controller = getController(entity) ?: return false
        return controller.shouldRotatePokemonHead()
    }

    fun rotationOnMouseXY(
        entity: PokemonEntity,
        driver: Player,
        yMouse: Double,
        xMouse: Double,
        yMouseSmoother: SmoothDouble,
        xMouseSmoother: SmoothDouble,
        sensitivity: Double,
        deltaTime: Double
    ): Vec3 {
        val controller = getController(entity) ?: return Vec3.ZERO
        return controller.rotationOnMouseXY(
            entity,
            driver,
            yMouse,
            xMouse,
            yMouseSmoother,
            xMouseSmoother,
            sensitivity,
            deltaTime
        ) }

    /**
     * Determines whether mouse input will affect the rotation of
     * the rollable object. Need to remove this as it's not used anymore
     */
    //I think this might better serve as a "use smoothing on angular velocity"
    fun useAngVelSmoothing(entity: PokemonEntity): Boolean {
        val controller = getController(entity) ?: return true
        return controller.useAngVelSmoothing(entity)
    }

    fun turnOffOnGround(entity: PokemonEntity): Boolean {
        val controller = getController(entity) ?: return false
        return controller.turnOffOnGround(entity)
    }

    fun angRollVel(entity: PokemonEntity, driver: Player, deltaTime: Double): Vec3 {
        val controller = getController(entity) ?: return Vec3(0.0, 0.0, 0.0)
        return controller.angRollVel(entity, driver, deltaTime)
    }

    fun dismountOnShift(): Boolean {
        val controller = getController(entity) ?: return false
        return controller.dismountOnShift(entity)
    }
}