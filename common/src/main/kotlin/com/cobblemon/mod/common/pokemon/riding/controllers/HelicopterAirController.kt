/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.riding.controllers

import com.bedrockk.molang.Expression
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.Rollable
import com.cobblemon.mod.common.api.riding.controller.RideController
import com.cobblemon.mod.common.api.riding.controller.posing.PoseOption
import com.cobblemon.mod.common.api.riding.controller.posing.PoseProvider
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.*
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.util.SmoothDouble
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.Shapes
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin

/**
 * Allows for flight like a bird controller, except you don't have to do any "flapping" motion,
 * you can just go up and down like creative
 *
 * @author Apion, Jackowes
 * @since January 1, 2025
 */
class HelicopterAirController : RideController {
    override val key = KEY
    override val poseProvider = PoseProvider(PoseType.HOVER)
        .with(PoseOption(PoseType.FLY) { it.deltaMovement.horizontalDistance() > 0.1 })

    //If there are only fluid blocks or air block below the ride
    //then activate the controller. If it is in water the ride will
    //dismount accordingly
    override val condition: (PokemonEntity) -> Boolean = { entity ->
        Shapes.create(entity.boundingBox).blockPositionsAsListRounded().any {
            if (it.y.toDouble() == (entity.position().y)) {
                val blockState = entity.level().getBlockState(it.below())
                return@any (blockState.isAir || !blockState.fluidState.isEmpty)
            }
            true

        }
    }

    var gravity: Expression = "1.0".asExpression()
        private set
    var horizontalAcceleration: Expression = "0.1".asExpression()
        private set
    var verticalVelocity: Expression = "1.0".asExpression()
        private set
    var speed: Expression = "1.0".asExpression()
        private set


    override fun speed(entity: PokemonEntity, driver: Player): Float {
        //Increased max speed to exaggerate movement.
        //This likely just needs to be a static number if it really is just
        //the scalar for the velocity vector
        return 2.0f
    }

    override fun rotation(entity: PokemonEntity, driver: LivingEntity): Vec2 {
        return Vec2(driver.xRot * 0.5f, driver.yRot)
    }

    override fun velocity(entity: PokemonEntity, driver: Player, input: Vec3): Vec3 {
        val runtime = getRuntime(entity)

        //If the player is not modulating height then hover
        var yVel = if (driver.jumping) runtime.resolveDouble(verticalVelocity)
        else if (driver.isShiftKeyDown) -runtime.resolveDouble(verticalVelocity)
        else (0.0)

        yVel *= 0.25
        var xVel = 0.0
        var zVel = 0.0

        val rollable = driver as? Rollable

        //Horizontal velocity is based on pitch and roll
        if (rollable != null) {
            xVel = -1.0 * sin(Math.toRadians(rollable.roll.toDouble()))
            zVel = sin(Math.toRadians(rollable.pitch.toDouble()))
        }

        return Vec3(xVel, yVel, zVel)
    }


    override fun angRollVel(entity: PokemonEntity, driver: Player, deltaTime: Double): Vec3 {

        val rollable = driver as? Rollable

        //In degrees per second? It was supposed to be I think but
        //I have messed something up
        val rotationChangeRate = 2.0
        val rotLimit = 30.0

        var pitchForce = driver.zza * rotationChangeRate
        var rollForce = -1.0 * driver.xxa * rotationChangeRate


        //TODO: Fix accumulated movement causing pitch and roll out of set bounds
        if (rollable != null) {
            //If the roll or pitch have exceeded the limit then do not modulate that
            //rotation in that direction further

            //modulate the forces so that they trend towards 0 as they approach rotation limit.
            //The current function has it drop off very quickly which may not be ideal.
            //Prev function:
            //      pitchForce = pitchForce * max(rotLim - abs(rollable.pitch.toDouble()), 0.0)

            //The three denotes that the force will be 1/3 what it would have been at
            //the rotation limit
            rollForce *= 2.0.pow(-1.0 * (abs(rollable.roll.toDouble()) / ROTATION_LIMIT))

            pitchForce *= 2.0.pow(-1.0 * (abs(rollable.pitch.toDouble()) / ROTATION_LIMIT))

            if (rollable.roll >= ROTATION_LIMIT && rollForce > 0.0) {
                rollForce = 0.0;
            }
            if (rollable.roll <= -ROTATION_LIMIT && rollForce < 0.0) {
                rollForce = 0.0;
            }
            if (rollable.pitch >= ROTATION_LIMIT && pitchForce > 0.0) {
                pitchForce = 0.0;
            }
            if (rollable.pitch <= -ROTATION_LIMIT && pitchForce < 0.0) {
                pitchForce = 0.0;
            }

            //Ignore x,y,z its angular velocity:
            //yaw, pitch, roll
            return Vec3(0.0, pitchForce.toDouble(), rollForce)
        }

        return Vec3(0.0, 0.0, 0.0)
    }

    override fun useAngVelSmoothing(entity: PokemonEntity): Boolean = true


    override fun inertia(entity: PokemonEntity): Double = 0.1

    override fun canJump(
        entity: PokemonEntity,
        driver: Player
    ): Boolean {
        return false
    }

    override fun jumpForce(
        entity: PokemonEntity,
        driver: Player,
        jumpStrength: Int
    ): Vec3 {
        return Vec3.ZERO
    }

    override fun shouldRoll(entity: PokemonEntity): Boolean = true

    override fun rotationOnMouseXY(
        entity: PokemonEntity,
        driver: Player,
        yMouse: Double,
        xMouse: Double,
        yMouseSmoother: SmoothDouble,
        xMouseSmoother: SmoothDouble,
        sensitivity: Double,
        deltaTime: Double
    ): Vec3 {
        if (driver !is Rollable) return Vec3.ZERO

        val rollable = driver as Rollable
        val invertYaw = if (Cobblemon.config.invertYaw) -1 else 1
        //yaw, pitch, roll
        return Vec3(xMouse * invertYaw, 0.0, 0.0)
    }

    override fun gravity(entity: PokemonEntity, regularGravity: Double): Double {
        return 0.0
    }

    //Need to ensure that crouching lowers altitude and doesn't eject the player
    override fun dismountOnShift(entity: PokemonEntity): Boolean = false

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        super.encode(buffer)
        buffer.writeString(gravity.toString())
        buffer.writeString(horizontalAcceleration.toString())
        buffer.writeString(verticalVelocity.toString())
        buffer.writeString(speed.toString())
    }

    override fun decode(buffer: RegistryFriendlyByteBuf) {
        gravity = buffer.readString().asExpression()
        horizontalAcceleration = buffer.readString().asExpression()
        verticalVelocity = buffer.readString().asExpression()
        speed = buffer.readString().asExpression()
    }


    companion object {
        val KEY = cobblemonResource("air/helicopter")
        val ROTATION_LIMIT = 30.0f
    }
}