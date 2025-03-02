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
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.riding.states.DolphinState
import com.cobblemon.mod.common.util.*
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.SmoothDouble
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.Shapes
import kotlin.math.*

class DolphinController : RideController {
    companion object {
        val KEY: ResourceLocation = cobblemonResource("swim/dolphin")
    }

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

    var handlingExpr: Expression = "(q.get_ride_stats('SKILL', 'LAND') / 100) * (140.0 - 20.0) + 20.0".asExpression()
        private set
    var topSpeedExpr: Expression = "(q.get_ride_stats('SPEED', 'LAND') / 100) * (1.0 - 0.5) + 0.5".asExpression()
        private set
    var accelExpr: Expression = ("(q.get_ride_stats('ACCELERATION', 'LAND') / 100.0) * " +
            "( (1.0 / (1.0 * 2))  - (1.0 / (20 * 6.0))) + (1.0 / (20 * 6.0))").asExpression()
        private set
    var staminaExpr: Expression = "(q.get_ride_stats('SKILL', 'LAND') / 100) * (60.0 - 10.0) + 10.0".asExpression()
        private set
    var jumpExpr: Expression = "(q.get_ride_stats('SKILL', 'LAND') / 100) * (300.0 - 128.0) + 128.0".asExpression()
        private set


    override fun speed(entity: PokemonEntity, driver: Player): Float {
        //Retrieve Vehicle state
        val state = getState(entity, ::DolphinState)

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
        //If neither forcing deccel or accel then apply water resistance
        else{
            state.currSpeed = max( state.currSpeed - (accel / 4.0) , 0.0)
        }

        return state.currSpeed.toFloat()
    }

    override fun rotation(entity: PokemonEntity, driver: LivingEntity): Vec2 {
        return Vec2(driver.xRot * 0.5f, driver.yRot )
    }

    override fun gravity(entity: PokemonEntity, regularGravity: Double): Double? {
        return 0.0
    }

    override fun inertia(entity: PokemonEntity ): Double
    {
        return 0.1
    }

    override fun velocity(entity: PokemonEntity, driver: Player, input: Vec3): Vec3 {
        val runtime = getRuntime(entity)

        if (!entity.isInWater && !entity.isUnderWater)
        {
            return Vec3(entity.deltaMovement.x, entity.deltaMovement.x - 0.035, entity.deltaMovement.z )
        }

        var yComp = 0.0

        //TODO:Determine a better way to move around at low speed.
        //Get roll to add a left and right strafe during roll
        //Not sure if this is desired? Will need to mess around with this
        //a bit more
        var zComp = 0.0
        val rollable = driver as? Rollable
        if (rollable != null)
        {
            //Can be used again if I better figure out how to deadzone it near the tops?
            zComp = -1.0 * sin(Math.toRadians( rollable.roll.toDouble() ) )
            yComp = -1.0 * sin(Math.toRadians( rollable.pitch.toDouble() ) )
        }

        val currVelocity = Vec3(0.0, yComp , 1.0)
        return currVelocity
    }

    override fun angRollVel(entity: PokemonEntity, driver: Player, deltaTime: Double): Vec3 {

        val rollable = driver as? Rollable

        //TODO: Tie in handling
        val handling = getRuntime(entity).resolveDouble(handlingExpr)
        val rotationChangeRate = 10.0

        if( rollable != null )
        {
            var yawForce =  rotationChangeRate * sin( Math.toRadians( rollable.roll.toDouble() ))
            //for a bit of correction on the rolls limit it to a quarter the amount
            var pitchForce = -0.25 * rotationChangeRate * abs(sin( Math.toRadians( rollable.roll.toDouble() )))

            //limit rotation modulation when pitched up heavily or pitched down heavily
            yawForce *= abs(cos( Math.toRadians( rollable.pitch.toDouble() )))
            pitchForce *= abs(cos( Math.toRadians( rollable.pitch.toDouble() ))) * 1.5

            //Create a deadzone for easier leveling out
            if( rollable.roll <= 0.0 && rollable.roll >= -0.0)
            {
                yawForce = 0.0
                pitchForce = 0.0
            }
            return Vec3( yawForce, pitchForce, 0.0)
        }

        return Vec3(0.0, 0.0, 0.0)
    }

    override fun canJump(entity: PokemonEntity, driver: Player) = false

    override fun jumpForce(
        entity: PokemonEntity,
        driver: Player,
        jumpStrength: Int
    ): Vec3 {
        return Vec3.ZERO
    }

    override fun shouldRoll(entity: PokemonEntity) = true

    override fun turnOffOnGround(entity: PokemonEntity): Boolean = true

    override fun dismountOnShift(entity: PokemonEntity): Boolean = false

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        super.encode(buffer)
        buffer.writeString(this.handlingExpr.getString())
        buffer.writeString(this.staminaExpr.getString())
        buffer.writeString(this.jumpExpr.getString())
        buffer.writeString(this.topSpeedExpr.getString())
        buffer.writeString(this.accelExpr.getString())
    }


    override fun decode(buffer: RegistryFriendlyByteBuf) {
        this.handlingExpr = buffer.readString().asExpression()
        this.staminaExpr = buffer.readString().asExpression()
        this.jumpExpr = buffer.readString().asExpression()
        this.topSpeedExpr = buffer.readString().asExpression()
        this.accelExpr = buffer.readString().asExpression()
    }
}

