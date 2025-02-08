/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.riding.controllers

import com.cobblemon.mod.common.Rollable
import com.cobblemon.mod.common.api.riding.controller.RideController
import com.cobblemon.mod.common.api.riding.controller.posing.PoseOption
import com.cobblemon.mod.common.api.riding.controller.posing.PoseProvider
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
import kotlin.math.sin

class DolphinController : RideController {
    companion object {
        val KEY: ResourceLocation = cobblemonResource("swim/dolphin")
    }
    var speed = "1.0".asExpression()
        private set
    var driveFactor = "1.0".asExpression()
        private set
    var reverseDriveFactor = "0.25".asExpression()
        private set
    var strafeFactor = "0.2".asExpression()
        private set

    private val rollSmoother = SmoothDouble()

    override val key: ResourceLocation = KEY
    override val poseProvider: PoseProvider = PoseProvider(PoseType.FLOAT)
        .with(PoseOption(PoseType.SWIM) { it.deltaMovement.horizontalDistance() > 0.1 })
    override val condition: (PokemonEntity) -> Boolean = { entity ->
        //This could be kinda weird... what if the top of the mon is in a fluid but the bottom isnt?
        Shapes.create(entity.boundingBox).blockPositionsAsListRounded().any {
            if (entity.isInWater || entity.isUnderWater) {
                return@any true
            }
            val blockState = entity.level().getBlockState(it)
            return@any !blockState.fluidState.isEmpty
        }
    }

    override fun speed(entity: PokemonEntity, driver: Player): Float {
        return getRuntime(entity).resolveFloat(speed)
    }

    override fun rotation(entity: PokemonEntity, driver: LivingEntity): Vec2 {
        return Vec2(driver.xRot * 0.5f, driver.yRot )
    }

    override fun gravity(entity: PokemonEntity, regularGravity: Double): Double? {
        return 0.0
    }

    override fun velocity(entity: PokemonEntity, driver: Player, input: Vec3): Vec3 {
        val runtime = getRuntime(entity)
        val driveFactor = runtime.resolveFloat(this.driveFactor)
        val strafeFactor = runtime.resolveFloat(this.strafeFactor)
        val f = driver.xxa * strafeFactor
        var g = driver.zza * driveFactor
        if (g <= 0.0f) {
            g *= runtime.resolveFloat(this.reverseDriveFactor)
        }



        //Get current angle looking up or down
        //to determine how much the entity rises
        //or falls
        val currXRot = driver.xRot.toDouble()
        //has to be flipped
        val yComp = -1.0f * g.toDouble() *  sin(Math.toRadians(currXRot))

        //Get roll to add a left and right strafe during roll
        //Not sure if this is desired? Will need to mess around with this
        //a bit more
        var zComp = 0.0
        val rollable = driver as? Rollable
        val currRoll = rollable?.roll
        if (currRoll != null)
        {
            zComp = -1.0 * g.toDouble() * sin(Math.toRadians( currRoll.toDouble() ) )
        }

        val velocity = Vec3(zComp, yComp , g.toDouble())

        return velocity
    }

    override fun canJump(entity: PokemonEntity, driver: Player) = false

    override fun jumpForce(entity: PokemonEntity, driver: Player, jumpStrength: Int): Vec3 {
        return Vec3(0.0, 0.0, 0.0)
    }

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        super.encode(buffer)
        buffer.writeString(this.speed.getString())
        buffer.writeString(this.driveFactor.getString())
        buffer.writeString(this.reverseDriveFactor.getString())
        buffer.writeString(this.strafeFactor.getString())
    }

    override fun shouldRoll(entity: PokemonEntity) = true

    override fun decode(buffer: RegistryFriendlyByteBuf) {
        this.speed = buffer.readString().asExpression()
        this.driveFactor = buffer.readString().asExpression()
        this.reverseDriveFactor = buffer.readString().asExpression()
        this.strafeFactor = buffer.readString().asExpression()
    }
}