/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.riding.controllers

import com.bedrockk.molang.Expression
import com.bedrockk.molang.runtime.value.DoubleValue
import com.cobblemon.mod.common.Rollable
import com.cobblemon.mod.common.api.riding.controller.RideController
import com.cobblemon.mod.common.api.riding.controller.posing.PoseOption
import com.cobblemon.mod.common.api.riding.controller.posing.PoseProvider
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.riding.states.GenericLandState
import com.cobblemon.mod.common.pokemon.riding.states.JetAirState
import com.cobblemon.mod.common.util.*
import net.minecraft.client.Minecraft
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.SmoothDouble
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.Shapes
import kotlin.math.*

class GenericLandController : RideController {
    companion object {
        val KEY: ResourceLocation = cobblemonResource("land/generic")
    }

//    private var previousVelocity = Vec3d.ZERO

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

    var handlingExpr: Expression = "q.get_ride_stats('SKILL', 'LAND', 140.0, 20.0)".asExpression()
        private set
    var topSpeedExpr: Expression = "q.get_ride_stats('SPEED', 'LAND', 1.0, 0.3)".asExpression()
        private set
    // Max accel is a whole 1.0 in 1 second. The conversion in the function below is to convert seconds to ticks
    var accelExpr: Expression = "q.get_ride_stats('ACCELERATION', 'LAND', (1.0 / (20.0 * 0.5)), (1.0 / (20.0 * 5.0)))".asExpression()
        private set
    // Between 30 seconds and 10 seconds at the lowest when at full speed.
    var staminaExpr: Expression = "q.get_ride_stats('STAMINA', 'LAND', 30.0, 10.0)".asExpression()
        private set
    //Between a one block jump and a ten block jump
    var jumpExpr: Expression = "q.get_ride_stats('JUMP', 'LAND', 10.0, 1.0)".asExpression()
        private set

    @Transient
    override val key: ResourceLocation = KEY
    @Transient
    override val poseProvider: PoseProvider = PoseProvider(PoseType.STAND).with(PoseOption(PoseType.WALK) { it.deltaMovement.horizontalDistance() > 0.1 })
    @Transient
    override val condition: (PokemonEntity) -> Boolean = { entity ->
        // Are there any blocks under the mon that aren't air or fluid
        // Cant just check one block since some mons may be more than one block big
        // This should be changed so that the any predicate is only ran on blocks under the mon
        Shapes.create(entity.boundingBox).blockPositionsAsListRounded().any {
            //Need to check other fluids
            if (entity.isInWater || entity.isUnderWater) {
                return@any false
            }
            //This might not actually work, depending on what the yPos actually is. yPos of the middle of the entity? the feet?
            if (it.y.toDouble() == (entity.position().y)) {
                val blockState = entity.level().getBlockState(it.below())
                return@any !blockState.isAir && blockState.fluidState.isEmpty
            }
            true
        }
    }

    override fun speed(entity: PokemonEntity, driver: Player): Float {
        val state = getState(entity, ::GenericLandState)
        return state.rideVel.length().toFloat()
    }

    override fun rotation(entity: PokemonEntity, driver: LivingEntity): Vec2 {
        return Vec2(driver.xRot * 0.5f, driver.yRot)
    }

    override fun velocity(entity: PokemonEntity, driver: Player, input: Vec3): Vec3 {
        val currSpeed = 1.0

        val runtime = getRuntime(entity)
        val driveFactor = runtime.resolveFloat(this.driveFactor)
        val strafeFactor = runtime.resolveFloat(this.strafeFactor)
        val f = driver.xxa * strafeFactor
        var g = driver.zza * driveFactor
        if (g <= 0.0f) {
            g *= runtime.resolveFloat(this.reverseDriveFactor)
        }
        val gravity = -1.0

        val state = getState(entity, ::GenericLandState)


        calculateRideSpaceVel(entity, driver, state)

        //Jump the thang!
        if (Minecraft.getInstance().options.keyJump.isDown() && entity.onGround()) {
            state.rideVel = Vec3(state.rideVel.x, 2.0, state.rideVel.z)
        }

        //This is cheap.
        //Also make it stop quicker the slower it is.
        val maxSpeed = 1.0
        //state.currVel = state.currVel.lerp( Vec3(0.0, state.currVel.y, 0.0), 1.0/20.0 )


        //entity.deltaMovement = entity.deltaMovement.lerp(velocity, 1.0)
        //state.currVel = state.currVel.add( f.toDouble() / 20.0, gravity / 20.0 , g.toDouble() / 20.0)
        return state.rideVel
    }

    override fun shouldRoll(entity: PokemonEntity): Boolean {
        return false
    }

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

        //Smooth out mouse input.
        val smoothingSpeed = 4
        val xInput = xMouseSmoother.getNewDeltaValue(xMouse * 0.1, deltaTime * smoothingSpeed);
        val yInput = yMouseSmoother.getNewDeltaValue(yMouse * 0.1, deltaTime * smoothingSpeed);

        //yaw, pitch, roll
        return Vec3(0.0, yInput, xInput)
    }

    override fun canJump(entity: PokemonEntity, driver: Player) = false

    //TODO: bring in stamina stats
    override fun setRideBar(entity: PokemonEntity, driver: Player): Float {

        //Retrieve stamina from state and tick up at a rate of 0.1 a second
        val state = getState(entity, ::GenericLandState)
        val staminaGain = (1.0 / 20.0)*0.1
        state.stamina = min(state.stamina + staminaGain,1.0).toFloat()
        return (state.stamina / 1.0f)
    }

    override fun jumpForce(entity: PokemonEntity, driver: Player, jumpStrength: Int): Vec3 {
        val runtime = getRuntime(entity)
        runtime.environment.query.addFunction("jump_strength") { DoubleValue(jumpStrength.toDouble()) }
        val jumpVector = jumpVector.map { runtime.resolveFloat(it) }
        return Vec3(jumpVector[0].toDouble(), jumpVector[1].toDouble(), jumpVector[2].toDouble())
    }

    override fun gravity(entity: PokemonEntity, regularGravity: Double): Double {
        return 0.0
    }

    override fun inertia(entity: PokemonEntity ): Double = 0.3

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
    }

    /*
    *  Normalizes the current speed between minSpeed and maxSpeed.
    *  The result is clamped between 0.0 and 1.0, where 0.0 represents minSpeed and 1.0 represents maxSpeed.
    */
    fun normalizeSpeed(currSpeed: Double, minSpeed: Double, maxSpeed: Double): Double {
        require(maxSpeed > minSpeed) { "maxSpeed must be greater than minSpeed" }
        return ((currSpeed - minSpeed) / (maxSpeed - minSpeed)).coerceIn(0.0, 1.0)
    }

    fun calculateRideSpaceVel( entity: PokemonEntity, driver: Player, state: GenericLandState){

        val topSpeed = getRuntime(entity).resolveDouble(topSpeedExpr)
        val accel = getRuntime(entity).resolveDouble(accelExpr)
        val speed = state.rideVel.length()

        val minSpeed = 0.0

        //speed up and slow down based on input
        if (driver.zza > 0.0 && speed <= topSpeed && state.stamina > 0.0f) {
            //modify acceleration to be slower when at closer speeds to top speed
            val accelMod = max((normalizeSpeed(speed, minSpeed, topSpeed) - 1).pow(2), 0.1)
            state.rideVel = Vec3(state.rideVel.x, state.rideVel.y, min(state.rideVel.z + (accel * accelMod) , topSpeed))
        }
        else if (driver.zza >= 0.0 && state.stamina == 0.0f || speed > topSpeed) {
            state.rideVel = Vec3(state.rideVel.x, state.rideVel.y, max(state.rideVel.z - ((accel) / 4) , minSpeed))
        }
        else if (driver.zza < 0.0 && speed > minSpeed) {
            //modify deccel to be slower when at closer speeds to minimum speed
            val deccelMod = max( -(normalizeSpeed(speed, minSpeed, topSpeed)) + 1, 0.0)

            //Decelerate currently always a constant half of max acceleration.
            state.rideVel = Vec3(state.rideVel.x, state.rideVel.y, max( state.rideVel.z - ((accel * deccelMod) / 2) , minSpeed))
        }

        state.rideVel = Vec3(state.rideVel.x, max(state.rideVel.y - 0.2, -1.0) , state.rideVel.z)

        if (entity.onGround()) {
            state.rideVel = Vec3(state.rideVel.x, 0.0 , state.rideVel.z)
        }

    }
}