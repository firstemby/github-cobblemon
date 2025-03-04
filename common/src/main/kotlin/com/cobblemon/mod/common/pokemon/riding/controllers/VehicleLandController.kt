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
import com.cobblemon.mod.common.api.riding.RidingStyle
import com.cobblemon.mod.common.api.riding.controller.RideController
import com.cobblemon.mod.common.api.riding.controller.posing.PoseOption
import com.cobblemon.mod.common.api.riding.controller.posing.PoseProvider
import com.cobblemon.mod.common.api.riding.stats.RidingStat
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.riding.controllers.BirdAirController.Companion
import com.cobblemon.mod.common.pokemon.riding.states.LandVehicleState
import com.cobblemon.mod.common.util.*
import net.minecraft.client.Minecraft
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.Shapes
import kotlin.math.*

class VehicleLandController : RideController {
    companion object {
        val KEY: ResourceLocation = cobblemonResource("land/vehicle")
    }

    var canJump = "true".asExpression()
        private set
    var jumpVector = listOf("0".asExpression(), "0.3".asExpression(), "0".asExpression())
        private set
    var speed = "0.3".asExpression()
        private set
    var driveFactor = "1.0".asExpression()
        private set
    var reverseDriveFactor = "0.25".asExpression()
        private set

    var minimumSpeedToTurn = "0.1".asExpression()
        private set
    var rotationSpeed = "45/20".asExpression()
        private set
    var lookYawLimit = "101".asExpression()
        private set

    // TODO: Tweak these stats to a workable state.
    var handlingExpr: Expression = "q.get_ride_stats('SKILL', 'AIR', 140.0, 20.0)".asExpression()
        private set
    var topSpeedExpr: Expression = "q.get_ride_stats('SPEED', 'AIR', 1.0, 0.5)".asExpression()
        private set
    // Max accel is a whole 1.0 in 2 seconds. The conversion in the function below is to convert seconds to ticks
    var accelExpr: Expression = "q.get_ride_stats('ACCELERATION', 'AIR', (1.0 / (20.0 * 2.0)), (1.0 / (20.0 * 6.0)))".asExpression()
        private set
    // Between 60 seconds and 10 seconds at the lowest when at full speed.
    var staminaExpr: Expression = "q.get_ride_stats('SKILL', 'AIR', 60.0, 10.0)".asExpression()
        private set
    var jumpExpr: Expression = "q.get_ride_stats('SKILL', 'AIR', 300.0, 128.0)".asExpression()
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
            /*
            //This might not actually work, depending on what the yPos actually is. yPos of the middle of the entity? the feet?
            if (it.y.toDouble() == (entity.position().y)) {
                val blockState = entity.level().getBlockState(it.below())
                return@any (!blockState.isAir && blockState.fluidState.isEmpty)
            }
            */
            true
        }
    }

    override fun speed(entity: PokemonEntity, driver: Player): Float {

        //Retrieve Vehicle state
        val state = getState(entity, ::LandVehicleState)

        //retrieve stats
        val topSpeed = getRuntime(entity).resolveDouble(topSpeedExpr) * 2
        val accel = getRuntime(entity).resolveDouble(accelExpr) * 2

        //speed up and slow down based on input
        if( driver.zza > 0.0 && state.currSpeed < topSpeed)
        {
            state.currSpeed = min( state.currSpeed + accel , topSpeed)
        }
        else if ( driver.zza < 0.0 && state.currSpeed > 0.0)
        {
            //Decelerate is currently always half of accel
            state.currSpeed = max( state.currSpeed - (accel * 2) , 0.0)
        }
        else{
            state.currSpeed = max( state.currSpeed - (accel / 2.0) , 0.0)
        }

        return state.currSpeed.toFloat()
    }

    override fun rotation(entity: PokemonEntity, driver: LivingEntity): Vec2 {
        //Retrieve Vehicle state
        val state = getState(entity, ::LandVehicleState)

        val rotationDegrees = driver.xxa * getRuntime(entity).resolveFloat(this.rotationSpeed)

        if( Minecraft.getInstance().options.keyJump.isDown() ){
            //If the current drift has not been set and the car is rotating then set drift
            if( state.currDrift == 0.0 && rotationDegrees != 0.0f) {
                //Set to base value which will be half.
                state.currDrift = sign(rotationDegrees) * 2.5
                state.turnSmoother.reset()
            }

            //Calculate any changes to drift
            val driftChangeRate = (1.0 / (20.0 * 0.15))

            //If Drift is negative already
            if(state.currDrift < 0) {
                state.currDrift = (state.currDrift + (sign(rotationDegrees) * driftChangeRate)).coerceIn(-5.0, -1.0)
            }else{
                state.currDrift = (state.currDrift + (sign(rotationDegrees) * driftChangeRate)).coerceIn(1.0, 5.0)
            }
            return Vec2(driver.xRot, entity.yRot - state.currDrift.toFloat())
        }
        //If the drift key is not held then reset the curr drift to 0 and use normal rotation
        else{
            //If the drift key has just been released perform boost addition and drift reset
            if( state.currDrift != 0.0){
                state.turnSmoother.reset()
                state.currDrift = 0.0
                state.driftBoost = min(state.driftBoost + 1.0, 2.0)
            }

            //Restrict turning while boost is active
            val boostRotDebuff = if( state.driftBoost != 0.0 ) 0.5f else 1.0f

            return Vec2(driver.xRot, entity.yRot - (rotationDegrees * boostRotDebuff) )
            //return Vec2(driver.xRot, state.turnSmoother.getNewDeltaValue((entity.yRot - rotationDegrees).toDouble(), 1.0).toFloat())
        }

    }

    override fun velocity(entity: PokemonEntity, driver: Player, input: Vec3): Vec3 {
        //Retrieve Vehicle state
        val state = getState(entity, ::LandVehicleState)

        val runtime = getRuntime(entity)
        val driveFactor = runtime.resolveFloat(this.driveFactor)
        var g = driver.zza * driveFactor
        if (g <= 0.0f) {
            g *= runtime.resolveFloat(this.reverseDriveFactor)
        }
        val grav = -1.0
        var velocity = Vec3(0.0,0.0,0.0)

        //If the vehicle is currently drifting then do a "poor mans" rotation of the velocity vector to offset it
        //from the front of the vehicle. This likely needs cleaned up to be
        if( state.currDrift != 0.0){
            //Drift speed should be slower the higher the drift is
            //normalize the drift speed
            val driftSpeed =  (1.0 - ((abs(state.currDrift) - 1.0) / (5.0 - 1.0))) * 0.5 + 0.3

            velocity = Vec3((1.0 * state.currSpeed) * (driftSpeed / 2.0) * -sign(state.currDrift) + min(state.driftBoost, 0.5) * 1.25,
                            grav,
                         (1.0 * state.currSpeed) * driftSpeed + min(state.driftBoost, 0.5) * 1.25)
        }else{
            velocity = Vec3(0.0 , grav, state.currSpeed + min(state.driftBoost, 0.5) * 1.25)
        }

        //tick down boost
        state.driftBoost = max( state.driftBoost - (1.0 / (20.0 * 2)), 0.0)

        return velocity
    }

    override fun updatePassengerRotation(entity: PokemonEntity, driver: LivingEntity) {
        driver.yRot += (entity.riding.deltaRotation.y)
        driver.setYHeadRot(driver.yHeadRot + (entity.riding.deltaRotation.y))
    }

    override fun clampPassengerRotation(entity: PokemonEntity, driver: LivingEntity) {
        val f = Mth.wrapDegrees(driver.yRot - entity.yRot)
        val runtime = getRuntime(entity)
        val lookYawLimit = 120.0f
        val g = Mth.clamp(f, -lookYawLimit, lookYawLimit)
        driver.yRotO += g - f
        driver.yRot = driver.yRot + g - f
        driver.setYHeadRot(driver.yRot)
    }

    override fun canJump(entity: PokemonEntity, driver: Player) = getRuntime(entity).resolveBoolean(canJump)

    override fun jumpForce(entity: PokemonEntity, driver: Player, jumpStrength: Int): Vec3 {
        val runtime = getRuntime(entity)
        runtime.environment.query.addFunction("jump_strength") { DoubleValue(jumpStrength.toDouble()) }
        val jumpVector = jumpVector.map { runtime.resolveFloat(it) }
        return Vec3(jumpVector[0].toDouble(), jumpVector[1].toDouble(), jumpVector[2].toDouble())
    }

    override fun inertia(entity: PokemonEntity ) : Double {
        val state = getState(entity, ::LandVehicleState)

        //val inertia = if(Minecraft.getInstance().options.keyJump.isDown()) 0.1 else 0.5

        return 0.1
    }

    override fun rideFovMult(entity: PokemonEntity, driver: Player): Float {
        val state = getState(entity, ::LandVehicleState)

        //Only increase fov when actively using the boost.
        val fovIncrease = (min( state.driftBoost, 0.5 ) * 0.3).toFloat()
        return 1.0f + fovIncrease

    }

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        super.encode(buffer)
        buffer.writeString(this.speed.getString())
        buffer.writeString(this.canJump.getString())
        buffer.writeString(this.jumpVector[0].getString())
        buffer.writeString(this.jumpVector[1].getString())
        buffer.writeString(this.jumpVector[2].getString())
        buffer.writeString(this.driveFactor.getString())
        buffer.writeString(this.reverseDriveFactor.getString())
        buffer.writeString(this.minimumSpeedToTurn.getString())
        buffer.writeString(this.rotationSpeed.getString())
        buffer.writeString(this.lookYawLimit.getString())
        buffer.writeString(this.handlingExpr.getString())
        buffer.writeString(this.staminaExpr.getString())
        buffer.writeString(this.jumpExpr.getString())
        buffer.writeString(this.topSpeedExpr.getString())
        buffer.writeString(this.accelExpr.getString())
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
        this.minimumSpeedToTurn = buffer.readString().asExpression()
        this.rotationSpeed = buffer.readString().asExpression()
        this.lookYawLimit = buffer.readString().asExpression()
        this.handlingExpr = buffer.readString().asExpression()
        this.staminaExpr = buffer.readString().asExpression()
        this.jumpExpr = buffer.readString().asExpression()
        this.topSpeedExpr = buffer.readString().asExpression()
        this.accelExpr = buffer.readString().asExpression()
    }

}