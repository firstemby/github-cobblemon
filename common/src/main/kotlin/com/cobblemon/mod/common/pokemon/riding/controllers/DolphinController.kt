/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.riding.controllers

import com.bedrockk.molang.runtime.value.DoubleValue
import com.cobblemon.mod.common.Rollable
import com.cobblemon.mod.common.api.riding.controller.RideController
import com.cobblemon.mod.common.api.riding.controller.posing.PoseOption
import com.cobblemon.mod.common.api.riding.controller.posing.PoseProvider
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Pokemon
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
import kotlin.math.sqrt

class DolphinController : RideController {
    companion object {
        val KEY: ResourceLocation = cobblemonResource("swim/dolphin")
    }

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
    var lastVelocity = Vec3(0.0,0.0,0.0)
        private set

    private val rollSmoother = SmoothDouble()

    override val key: ResourceLocation = KEY
    override val poseProvider: PoseProvider = PoseProvider(PoseType.FLOAT)
        .with(PoseOption(PoseType.SWIM) { it.deltaMovement.length() >= 0.05 })
    override val condition: (PokemonEntity) -> Boolean = { entity ->

        //If there are only fluid blocks or air block below the ride
        //or if the entity is in water then activate the controller
        Shapes.create(entity.boundingBox).blockPositionsAsListRounded().any {
            if (it.y.toDouble() == (entity.position().y)) {
                val blockState = entity.level().getBlockState(it.below())
                return@any (blockState.isAir || !blockState.fluidState.isEmpty ) ||
                        (entity.isInWater || entity.isUnderWater)
            }
            true
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

    override fun inertia(entity: PokemonEntity ): Double
    {
        return 0.05
    }

    override fun velocity(entity: PokemonEntity, driver: Player, input: Vec3): Vec3 {
        val runtime = getRuntime(entity)

        if (!entity.isInWater && !entity.isUnderWater)
        {
            lastVelocity = Vec3(lastVelocity.x, lastVelocity.y - 0.035, lastVelocity.z)
            return lastVelocity
        }

        val driveFactor = runtime.resolveFloat(this.driveFactor)
        val strafeFactor = runtime.resolveFloat(this.strafeFactor)
        val f = driver.xxa * strafeFactor
        var g = driver.zza * driveFactor
        if (g <= 0.0f) {
            g *= runtime.resolveFloat(this.reverseDriveFactor)
        }

        var yComp = 0.0

        //Get roll to add a left and right strafe during roll
        //Not sure if this is desired? Will need to mess around with this
        //a bit more
        var zComp = 0.0
        val rollable = driver as? Rollable
        if (rollable != null)
        {
            //Can be used again if I better figure out how to deadzone it near the tops?
            zComp = -1.0 * g.toDouble() * sin(Math.toRadians(rollable.roll.toDouble()))
            yComp = -1.0 * g.toDouble() * sin(Math.toRadians(rollable.pitch.toDouble()))
        }

        val currVelocity = Vec3(0.0, yComp , g.toDouble())
        lastVelocity = currVelocity
        return currVelocity
    }

    override fun angRollVel(entity: PokemonEntity, driver: Player, deltaTime: Double): Vec3 {

        if (!entity.isInWater && !entity.isUnderWater)
        {
            return Vec3(0.0, 0.0, 0.0)
        }

        val rollable = driver as? Rollable

        //this should be changed to be speed maybe?
        val movingForce = driver.zza
        if (rollable != null) {
            var yawAngVel = 3 * sin(Math.toRadians(rollable.roll.toDouble())).toFloat()
            var pitchAngVel = -1 * Math.abs(sin(Math.toRadians(rollable.roll.toDouble())).toFloat())

            //limit rotation modulation when pitched up heavily or pitched down heavily
            yawAngVel *= (abs(cos(Math.toRadians(rollable.pitch.toDouble())))).toFloat()
            pitchAngVel *= (abs(cos(Math.toRadians(rollable.pitch.toDouble())))).toFloat()
            //if you are not pressing forward then don't turn
            //yawAngVel *= movingForce
            //Ignore x,y,z its angular velocity:
            //yaw, pitch, roll
            return Vec3(yawAngVel.toDouble(), pitchAngVel.toDouble(), 0.0)
        }

        return Vec3(0.0, 0.0, 0.0)
    }

    override fun canJump(entity: PokemonEntity, driver: Player) = getRuntime(entity).resolveBoolean(canJump)

    override fun jumpForce(entity: PokemonEntity, driver: Player, jumpStrength: Int): Vec3 {
        val runtime = getRuntime(entity)
        runtime.environment.query.addFunction("jump_strength") { DoubleValue(jumpStrength.toDouble()) }
        val jumpVector = jumpVector.map { runtime.resolveFloat(it) }
        return Vec3(jumpVector[0].toDouble(), jumpVector[1].toDouble(), jumpVector[2].toDouble())
    }

    override fun shouldRoll(entity: PokemonEntity) = true

    override fun turnOffOnGround(entity: PokemonEntity): Boolean = true

    override fun dismountOnShift(entity: PokemonEntity): Boolean = false

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        super.encode(buffer)
        buffer.writeString(this.speed.getString())
        buffer.writeString(this.canJump.getString())
        buffer.writeString(this.jumpVector[0].getString())
        buffer.writeString(this.jumpVector[1].getString())
        buffer.writeString(this.jumpVector[2].getString())
        buffer.writeString(this.driveFactor.getString())
        buffer.writeString(this.reverseDriveFactor.getString())
        buffer.writeString(this.strafeFactor.getString())
        buffer.writeDouble(this.lastVelocity.x)
        buffer.writeDouble(this.lastVelocity.y)
        buffer.writeDouble(this.lastVelocity.z)
    }


    override fun decode(buffer: RegistryFriendlyByteBuf) {
        this.speed = buffer.readString().asExpression()
        this.canJump = buffer.readString().asExpression()
        this.jumpVector = listOf(
            buffer.readString().asExpression(),
            buffer.readString().asExpression(),
            buffer.readString().asExpression()
        )
        this.driveFactor = buffer.readString().asExpression()
        this.reverseDriveFactor = buffer.readString().asExpression()
        this.strafeFactor = buffer.readString().asExpression()
        this.strafeFactor = buffer.readString().asExpression()
        val x = buffer.readDouble()
        val y = buffer.readDouble()
        val z = buffer.readDouble()
        this.lastVelocity = Vec3(x, y, z)
    }
}