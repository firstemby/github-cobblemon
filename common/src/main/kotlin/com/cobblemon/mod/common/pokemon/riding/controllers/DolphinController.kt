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
import com.cobblemon.mod.common.api.riding.stats.RidingStat
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.riding.states.DolphinLiquidState
import com.cobblemon.mod.common.util.*
import net.minecraft.client.Minecraft
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth.lerp
import net.minecraft.util.SmoothDouble
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import kotlin.math.*

class DolphinController : RideController {
    companion object {
        val KEY: ResourceLocation = cobblemonResource("swim/dolphin")
    }

    override val key: ResourceLocation = KEY
    override val poseProvider: PoseProvider = PoseProvider(PoseType.FLOAT)
        .with(PoseOption(PoseType.SWIM) { it.deltaMovement.length() >= 0.05 })
    /*
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
     */

    override val condition: (PokemonEntity) -> Boolean = { true }

    //TODO:Implement real values for this
    private val statExpressions: Map<RidingStat, Expression> = mapOf(
        RidingStat.SKILL to "q.get_ride_stats('SKILL', 'LIQUID', 135.0, 45.0)".asExpression(),
        RidingStat.SPEED to "q.get_ride_stats('SPEED', 'LIQUID', 0.6, 0.2)".asExpression(),
        RidingStat.ACCELERATION to "q.get_ride_stats('ACCELERATION', 'LIQUID', (1.0 / (20.0 * 3.0)), (1.0 / (20.0 * 8.0)))".asExpression(),
        RidingStat.STAMINA to "q.get_ride_stats('STAMINA', 'LIQUID', 120.0, 20.0)".asExpression(),
        //max y level for the ride
        RidingStat.JUMP to "q.get_ride_stats('JUMP', 'LIQUID', 200.0, 128.0)".asExpression()
    )

    var glideTopSpeedExpr: Expression = "q.get_ride_stats('SPEED', 'LIQUID', 2.0, 1.0)".asExpression()
        private set

    var infiniteStamina: Expression = "false".asExpression()
        private set
    var infiniteAltitude: Expression = "false".asExpression()
        private set


    override fun getStat(entity: PokemonEntity, stat: RidingStat): Double {
        val expr = statExpressions[stat] ?: return 0.0
        return getRuntime(entity).resolveDouble(expr)
    }

    override fun speed(entity: PokemonEntity, driver: Player): Float {

        //Retrieve the current bird controller state and stats
        val state = getState(entity, ::DolphinLiquidState)

        return state.rideVel.length().toFloat()
    }

    override fun rotation(entity: PokemonEntity, driver: LivingEntity): Vec2 {
        return Vec2(driver.xRot * 0.5f, driver.yRot)
    }

    override fun velocity(entity: PokemonEntity, driver: Player, input: Vec3): Vec3 {
        var yVel = 0.0
        var leftForce = 0.0
        var upForce = 0.0
        var forwardForce = 0.0
        val state = getState(entity, ::DolphinLiquidState)

        //Perform ride velocity update
        calculateRideSpaceVel(entity, driver, state)

        //Translate ride space velocity to world space velocity.
        val rollable = driver as? Rollable
        if (rollable != null) {
            //Need to deadzone this when straight up or down

            upForce += -1.0 * sin(Math.toRadians(rollable.pitch.toDouble())) * state.rideVel.z
            forwardForce += cos(Math.toRadians(rollable.pitch.toDouble())) * state.rideVel.z

            upForce += cos(Math.toRadians(rollable.pitch.toDouble())) * state.rideVel.y
            forwardForce += sin(Math.toRadians(rollable.pitch.toDouble())) * state.rideVel.y
        }


        //Bring the ride out of the sky when stamina is depleted.
        if (state.stamina <= 0.0) {
            upForce -= 0.3
        }

        val altitudeLimit = this.getStat(entity, RidingStat.JUMP)

        //Only limit altitude if altitude is not infinite
        if (!getRuntime(entity).resolveBoolean(infiniteAltitude)) {
            //Provide a hard limit on altitude
            upForce = if (entity.y >= altitudeLimit && upForce > 0) 0.0 else upForce
        }

        val velocity = Vec3(state.rideVel.x , upForce, forwardForce)
        return velocity
    }

    override fun angRollVel(entity: PokemonEntity, driver: Player, deltaTime: Double): Vec3 {

        val rollable = driver as? Rollable
        val state = getState(entity, ::DolphinLiquidState)

        //TODO: Tie in handling
        val handling = this.getStat(entity, RidingStat.SKILL)
        val topSpeed = this.getStat(entity, RidingStat.SPEED)
        val rotationChangeRate = 10.0

        if (rollable != null) {
            var yawForce =  rotationChangeRate * sin(Math.toRadians(rollable.roll.toDouble()))
            //for a bit of correction on the rolls limit it to a quarter the amount
            var pitchForce = -0.35 * rotationChangeRate * abs(sin(Math.toRadians(rollable.roll.toDouble())))

            //limit rotation modulation when pitched up heavily or pitched down heavily
            yawForce *= abs(cos(Math.toRadians(rollable.pitch.toDouble())))
            pitchForce *= abs(cos(Math.toRadians(rollable.pitch.toDouble()))) * 1.5

            return Vec3(yawForce, pitchForce, 0.0)
        }

        return Vec3(0.0, 0.0, 0.0)
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
        if (driver !is Rollable) return Vec3.ZERO
        val rollable = driver as Rollable

        val state = getState(entity, ::DolphinLiquidState)
        val handling = this.getStat(entity, RidingStat.SKILL)
        val topSpeed = this.getStat(entity, RidingStat.SPEED)

        //Smooth out mouse input.
        val smoothingSpeed = 4
        val invertRoll = if (Cobblemon.config.invertRoll) -1 else 1
        val invertPitch = if (Cobblemon.config.invertPitch) -1 else 1
        val xInput = xMouseSmoother.getNewDeltaValue(xMouse * 0.1 * invertRoll, deltaTime * smoothingSpeed);
        val yInput = yMouseSmoother.getNewDeltaValue(yMouse * 0.1 * invertPitch, deltaTime * smoothingSpeed);

        //limit rolling based on handling and current speed.
        //modulated by speed so that when flapping idle in air you are ont wobbling around to look around
        val rotMin = 15.0
        var rollForce = xInput
        val rotLimit = max(handling * normalizeVal(state.rideVel.length(), 0.0, topSpeed).pow(3), rotMin)

        //Limit roll by non linearly decreasing inputs towards
        // a rotation limit based on the current distance from
        // that rotation limit
        if (abs(rollable.roll + rollForce) < rotLimit) {
            if (sign(rollForce) == sign(rollable.roll).toDouble()) {
                val d = abs(abs(rollable.roll) - rotLimit)
                rollForce *= (d.pow(2)) / (rotLimit.pow(2))
            }
        } else if (sign(rollForce) == sign(rollable.roll).toDouble()) {
            rollForce = 0.0
        }

        //Give the ability to yaw with x mouse input when at low speeds.
        val yawForce = xInput * ( 1.0 - normalizeVal(state.rideVel.length(), 0.0, topSpeed).pow(3))

        //yaw, pitch, roll
        return Vec3(yawForce, yInput, rollForce)
    }

    override fun canJump(
        entity: PokemonEntity,
        driver: Player
    ): Boolean {
        return false
    }

    override fun setRideBar(entity: PokemonEntity, driver: Player): Float {
        //Retrieve stamina from state
        val state = getState(entity, ::DolphinLiquidState)

        return (state.stamina / 1.0f)
    }

    override fun jumpForce(
        entity: PokemonEntity,
        driver: Player,
        jumpStrength: Int
    ): Vec3 {
        return Vec3.ZERO
    }

    override fun gravity(entity: PokemonEntity, regularGravity: Double): Double {
        return 0.0
    }

    override fun turnOffOnGround(entity: PokemonEntity): Boolean {
        return false
    }

    override fun shouldRoll(entity: PokemonEntity) = true

    override fun rideFovMult(entity: PokemonEntity, driver: Player): Float {
        val topSpeed = this.getStat(entity, RidingStat.SPEED)
        val glideTopSpeed = getRuntime(entity).resolveDouble(glideTopSpeedExpr)
        val state = getState(entity, ::DolphinLiquidState)

        //Must I ensure that topspeed is greater than minimum?
        val normalizedGlideSpeed = normalizeVal(state.rideVel.length(), topSpeed, glideTopSpeed)

        //Only ever want the fov change to be a max of 0.2 and for it to have non linear scaling.
        return 1.0f + normalizedGlideSpeed.pow(2).toFloat() * 0.2f

    }

    override fun useAngVelSmoothing(entity: PokemonEntity): Boolean = false

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        super.encode(buffer)
        buffer.writeString(glideTopSpeedExpr.toString())
        buffer.writeString(infiniteStamina.toString())
        buffer.writeString(infiniteAltitude.toString())
    }

    override fun decode(buffer: RegistryFriendlyByteBuf) {
        glideTopSpeedExpr = buffer.readString().asExpression()
        infiniteStamina = buffer.readString().asExpression()
        infiniteAltitude = buffer.readString().asExpression()
    }

    /*
    *  Normalizes the given value between a min and a max.
    *  The result is clamped between 0.0 and 1.0, where 0.0 represents x is at or below min
    *  and 1.0 represents x is at or above it.
    */
    fun normalizeVal(x: Double, min: Double, max: Double): Double {
        require(max > min) { "max must be greater than min" }
        return ((x - min) / (max - min)).coerceIn(0.0, 1.0)
    }

    /*s
    *  Calculates the change in the ride space vector due to player input and ride state
    */
    fun calculateRideSpaceVel(entity: PokemonEntity, driver: Player, state: DolphinLiquidState) {


        //retrieve stats
        val topSpeed = this.getStat(entity, RidingStat.SPEED)
        val glideTopSpeed = getRuntime(entity).resolveDouble(glideTopSpeedExpr)
        val accel = this.getStat(entity, RidingStat.ACCELERATION)
        val staminaStat = this.getStat(entity, RidingStat.STAMINA)

        var glideSpeedChange = 0.00

        val currSpeed = state.rideVel.length()

        //Flag for determining if player is actively inputting
        var activeInput = false

        //speed up and slow down based on input
        if (driver.zza != 0.0f && state.stamina > 0.0) {
            //make sure it can't exceed top speed
            val forwardInput = when {
                driver.zza > 0 && state.rideVel.z > topSpeed -> 0.0
                driver.zza < 0 && state.rideVel.z < (-topSpeed / 3.0) -> 0.0
                else -> driver.zza.sign
            }

            state.rideVel = Vec3(
                state.rideVel.x,
                state.rideVel.y,
                (state.rideVel.z + (accel * forwardInput.toDouble())))

            activeInput = true
        }

        //Lateral movement based on driver input.
        val latTopSpeed = topSpeed / 2.0
        if (driver.xxa != 0.0f && state.stamina > 0.0) {
            state.rideVel = Vec3(
                (state.rideVel.x + (accel * driver.xxa)).coerceIn(-latTopSpeed, latTopSpeed),
                state.rideVel.y,
                state.rideVel.z)
            activeInput = true
        }
        else {
            state.rideVel = Vec3(
                lerp(state.rideVel.x, 0.0, latTopSpeed / 20.0),
                state.rideVel.y,
                state.rideVel.z)
        }

        //Vertical movement based on driver input.
        val vertTopSpeed = topSpeed / 2.0
        val vertInput = when {
            Minecraft.getInstance().options.keyJump.isDown() -> 1.0
            Minecraft.getInstance().options.keyShift.isDown() -> -1.0
            else -> 0.0
        }

        if (vertInput != 0.0 && state.stamina > 0.0) {
            state.rideVel = Vec3(
                state.rideVel.x,
                (state.rideVel.y + (accel * vertInput)).coerceIn(-vertTopSpeed, vertTopSpeed),
                state.rideVel.z)
            activeInput = true
        }
        else {
            state.rideVel = Vec3(
                state.rideVel.x,
                lerp(state.rideVel.y, 0.0, vertTopSpeed / 20.0),
                state.rideVel.z)
        }

        //Check if the ride should be gliding
        if (activeInput && state.stamina > 0.0) {
            state.gliding = false
        }else {
            state.gliding = true
        }

        //Only perform stamina logic if the ride does not have infinite stamina
        if (!getRuntime(entity).resolveBoolean(infiniteStamina)) {
            if (activeInput) {
                state.stamina -= (0.05 / staminaStat).toFloat()
            }

            //Lose a base amount of stamina just for being airborne
            state.stamina -= (0.01 / staminaStat).toFloat()
        }
        else
        {
            state.stamina = 1.0f
        }

        //air resistance
        //TODO: This method is very unrefined and will need to be an active point of testing
        /*
        state.rideVel = Vec3(
            state.rideVel.x,
            state.rideVel.y,
            lerp( state.rideVel.z,0.0, topSpeed / ( 20.0 * 30.0)))
         */
    }
}