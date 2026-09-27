/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.entity.pokemon.ai

import com.bedrockk.molang.runtime.MoLangRuntime
import com.cobblemon.mod.common.api.molang.MoLangFunctions.addFunctions
import com.cobblemon.mod.common.api.molang.MoLangFunctions.addStandardFunctions
import com.cobblemon.mod.common.entity.PlatformType
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.entity.pokemon.PokemonBehaviourFlag
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.entity.pokemon.extensions.*
import com.cobblemon.mod.common.util.getWaterAndLavaIn
import com.cobblemon.mod.common.util.math.geometry.toRadians
import com.cobblemon.mod.common.util.resolveFloat
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.tags.BlockTags
import net.minecraft.tags.FluidTags
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.control.MoveControl
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.pathfinder.PathType
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

class PokemonMoveControl(val pokemonEntity: PokemonEntity) : MoveControl(pokemonEntity) {
    companion object {
        const val VERY_CLOSE = 2.500000277905201E-3

        // Max change in velocity per tick for fliers and swimmers
        const val FLY_SWIM_MAX_ACCEL = 0.015
        // Walkers reach top speed from rest in roughly this many ticks
        const val GROUND_TICKS_TO_TOP_SPEED = 5.0
        // Fliers and swimmers stop once this close to a target they are arriving at
        const val ARRIVAL_TOLERANCE = 0.05
        // Swimmers aim this far above their target, otherwise they get stuck on the lip when leaving water
        const val FLUID_EXIT_Y_OFFSET = 0.05

        // Vanilla values from LivingEntity.travel and aiStep
        const val VANILLA_INPUT_SCALE = 0.98
        const val VANILLA_GROUND_SPEED_FACTOR = 0.21600002
        const val VANILLA_AIR_FRICTION = 0.91
        const val VANILLA_FLYING_SPEED = 0.02
        const val VANILLA_WATER_VERTICAL_DRAG = 0.8
    }

    val runtime = MoLangRuntime().also {
        it.environment.query.addStandardFunctions().addFunctions(pokemonEntity.struct.functions)
    }

    private var waterLevel : Double = 0.0
    var banking = false
    var bankDurationTicks = -1
    var bankForwardBlocksPerTick = 0.1F
    var bankUpwardsBlocksPerTick = 0F
    var bankRightDegreesPerTick = 1F

    fun startBanking(
        forwardBlocksPerTick: Float = 0F,
        upwardsBlocksPerTick: Float = 0F,
        rightDegreesPerTick: Float = 0F,
        durationTicks: Int = -1
    ) {
        this.banking = true
        this.bankForwardBlocksPerTick = forwardBlocksPerTick
        this.bankUpwardsBlocksPerTick = upwardsBlocksPerTick
        this.bankRightDegreesPerTick = rightDegreesPerTick
        this.bankDurationTicks = durationTicks
        operation = Operation.WAIT
    }

    fun stopBanking() {
        this.banking = false
        this.bankForwardBlocksPerTick = 0F
        this.bankUpwardsBlocksPerTick = 0F
        this.bankRightDegreesPerTick = 0F
        this.bankDurationTicks = -1
    }

    override fun tick() {
        mob.setDiscardFriction(false) // ignored while trying to move but if stopped or doing something else this needs reenabled
        if (banking && operation != Operation.WAIT) {
            banking = false
        }

        if (pokemonEntity.isDeadOrDying) {
            pokemonEntity.speed = 0F
            pokemonEntity.yya = 0F
            return
        }

        if (!pokemonEntity.isFlying() && !pokemonEntity.isInLiquid && !pokemonEntity.canWalk() && pokemonEntity.canFly()) {
            // If it's never put into flight then there are some pathing checks that fail. In particular,
            // #canUpdatePath in OmniPathNavigation. It can be not on ground and yet not activated flight yet.
            // This removes that edge case.
            pokemonEntity.setFlying(true)
        }

        val behaviour = pokemonEntity.behaviour
        val mediumSpeed = runtime.resolveFloat(if (pokemonEntity.getCurrentPoseType() in setOf(PoseType.FLY, PoseType.HOVER)) {
            behaviour.moving.fly.flySpeedHorizontal
        } else if (pokemonEntity.isEyeInFluid(FluidTags.WATER) || pokemonEntity.isEyeInFluid(FluidTags.LAVA)) {
            behaviour.moving.swim.swimSpeed
        } else {
            behaviour.moving.walk.walkSpeed
        }) * 2.5F

        val baseSpeed = mob.getAttributeValue(Attributes.MOVEMENT_SPEED).toFloat() * this.speedModifier.toFloat()
        // Mult the baseSpeed by the square root of the effectivescale to prevent foot slide (alpha/baby scaling)
        val adjustedSpeed = baseSpeed * mediumSpeed * sqrt(pokemonEntity.pokemon.effectiveScale)

        if (banking) {
            pokemonEntity.yRot += bankRightDegreesPerTick
            pokemonEntity.travel(Vec3(0.0, bankUpwardsBlocksPerTick.toDouble(), bankForwardBlocksPerTick.toDouble()).scale(mediumSpeed.toDouble()))
            if (bankDurationTicks > 0) {
                bankDurationTicks--
            }
            if (bankDurationTicks == 0) {
                banking = false
                operation = Operation.WAIT
            }
        }
        if (operation == Operation.STRAFE) {
            var movingDistanceTotal = Mth.sqrt(strafeForwards * strafeForwards + strafeRight * strafeRight)
            if (movingDistanceTotal < 1.0f) {
                movingDistanceTotal = 1.0f
            }

            movingDistanceTotal = adjustedSpeed / movingDistanceTotal

            val adjustedForward = strafeForwards * movingDistanceTotal
            val adjustedStrafe = strafeRight * movingDistanceTotal

            val xComponent = -Mth.sin(mob.yRot.toRadians())
            val zComponent = Mth.cos(mob.yRot.toRadians())
            val xMovement = adjustedForward * zComponent - adjustedStrafe * xComponent
            val zMovement = adjustedStrafe * zComponent + adjustedForward * xComponent
            if (!isWalkable(xMovement, zMovement)) {
                strafeForwards = 1.0f
                strafeRight = 0.0f
            }
            mob.speed = adjustedSpeed
            mob.setZza(strafeForwards)
            mob.setXxa(strafeRight)
            operation = Operation.WAIT
        } else if (operation == Operation.MOVE_TO) {
            // Don't instantly move to WAIT for fluid movements as they overshoot their mark.
            if (!pokemonEntity.isFlying() && !pokemonEntity.isInLiquid) {
                operation = Operation.WAIT
            }
            // Follow the path when there is one, otherwise go straight for the wanted position
            val steerTarget = pokemonEntity.navigation.getSteerTarget()
            val target = steerTarget?.position ?: Vec3(wantedX, wantedY, wantedZ)
            val arrive = steerTarget?.isFinal ?: true

            val xDist = target.x - mob.x
            val zDist = target.z - mob.z
            var yDist = target.y - mob.y

            if (xDist * xDist + yDist * yDist + zDist * zDist < VERY_CLOSE) {
                // If we're close enough, pull up stumps here.
                mob.setZza(0F)
                mob.yya = 0F
                // If we're super close and we're fluid movers, forcefully stop moving so you don't overshoot
                if ((pokemonEntity.isFlying() || pokemonEntity.isInLiquid)) {
                    operation = Operation.WAIT
                    mob.deltaMovement = Vec3.ZERO
                }
                return
            }

            val horizontalDistanceFromTarget = xDist * xDist + zDist * zDist
            val closeHorizontally = horizontalDistanceFromTarget < VERY_CLOSE
            if (!closeHorizontally) {
                pokemonEntity.orientEntityToTravel()
            }

            val (inWater, inLava) = mob.level().getWaterAndLavaIn(mob.boundingBox)
            val inFluid = inWater || inLava
            var verticalHandled = false
            val blockPos = mob.blockPosition()
            val blockState = mob.level().getBlockState(blockPos)
            val voxelShape = blockState.getCollisionShape(mob.level(), blockPos)

            if (pokemonEntity.getBehaviourFlag(PokemonBehaviourFlag.FLYING) || inFluid) {
                verticalHandled = true
                val fluidTarget = if (inFluid) target.add(0.0, FLUID_EXIT_Y_OFFSET, 0.0) else target
                yDist = fluidTarget.y - mob.y

                if (arrive && fluidTarget.distanceTo(mob.position()) < ARRIVAL_TOLERANCE) {
                    operation = Operation.WAIT
                    mob.deltaMovement = Vec3.ZERO
                    stopInputs()
                    return
                }
                steerTowards(fluidTarget, adjustedSpeed.toDouble(), FLY_SWIM_MAX_ACCEL, arrive, horizontalOnly = false)
            } else {
                // TODO: sprinting should likely be dictated by an objective?
                val topSpeed = groundTopSpeed(adjustedSpeed.toDouble())
                steerTowards(target, topSpeed, topSpeed / GROUND_TICKS_TO_TOP_SPEED, arrive, horizontalOnly = true)
            }

            if (!verticalHandled) {
                val tooBigToStep = yDist > pokemonEntity.behaviour.moving.stepHeight
                val xComponent = -Mth.sin(mob.yRot.toRadians()).toDouble()
                val zComponent = Mth.cos(mob.yRot.toRadians()).toDouble()

                val motion = Vec3(xComponent, 0.0, zComponent).normalize()
                val offset = motion.scale(adjustedSpeed.toDouble())
                val closeEnoughToJump = !mob.isFree(offset.x, 0.0, offset.z)// sqrt(xDist * xDist + zDist * zDist) - 0.5 <  entity.width / 2 + entity.movementSpeed * 4

                if (tooBigToStep &&
                    closeEnoughToJump ||
                    !voxelShape.isEmpty && mob.y < voxelShape.max(Direction.Axis.Y) + blockPos.y.toDouble() &&
                    !blockState.`is`(BlockTags.DOORS) &&
                    !blockState.`is`(BlockTags.FENCES)
                ) {
                    mob.jumpControl.jump()
                    operation = Operation.JUMPING
                }
            }

            if (closeHorizontally && abs(yDist) < VERY_CLOSE) {
                operation = Operation.WAIT
            }
        } else if (operation == Operation.JUMPING) {
            mob.speed = adjustedSpeed
            mob.yya = 0F
            if (mob.onGround() || pokemonEntity.getBehaviourFlag(PokemonBehaviourFlag.FLYING)) {
                operation = Operation.WAIT
            }
        } else {
            mob.setZza(0.0f)
            mob.yya = 0F
        }

        if (operation == Operation.WAIT && !mob.navigation.isInProgress) {
            if (mob.onGround() && behaviour.moving.walk.canWalk && pokemonEntity.getBehaviourFlag(PokemonBehaviourFlag.FLYING)) {
                pokemonEntity.setBehaviourFlag(PokemonBehaviourFlag.FLYING, false)
            }

            if (this.pokemonEntity.isBattling) {
                if (this.pokemonEntity.getBehaviourFlag(PokemonBehaviourFlag.FLYING)) {
                    // Flying Pokemon have extremely low vertical deceleration and can fly into the stratosphere if their movement is not dampened
                    // This can happen when:
                    // A Pokemon was jumping when the battle begins
                    // A Pokemon receives knockback from sweeping edge striking a nearby target, wind charges, etc.
                    mob.deltaMovement = Vec3(mob.deltaMovement.x, min(0.01, mob.deltaMovement.y), mob.deltaMovement.z)
                }
                if (mob.isInWater) {
                    // In battle water antics
                    // Borrowing hard from Minecraft's Boat logic
                    var e = 0.0F
                    val exposedForm = this.pokemonEntity.exposedForm
                    if (isUnderwater()) {
                        if (this.pokemonEntity.platform != PlatformType.NONE) {
                            // Float up if on a platform
                            e = 0.3F
                        }
                    } else if (checkInWater()) {
                        if (this.pokemonEntity.platform != PlatformType.NONE ) {
                            // Hold Steady
                            e = ((this.waterLevel - this.pokemonEntity.y) / this.pokemonEntity.bbHeight).toFloat()
                        } else if (exposedForm.behaviour.moving.swim.canBreatheUnderwater) {
                            // allow swimmers to sink a bit into the water
                            e = -1.5F
                        }
                    }
                    if (Mth.abs(e) > VERY_CLOSE) {
                        val vec32: Vec3 = this.pokemonEntity.deltaMovement
                        this.pokemonEntity.setDeltaMovement(vec32.x, (vec32.y + e * (this.pokemonEntity.gravity / 0.65)) * 0.75, vec32.z)
                    }
                }
            }
        }
    }

    /**
     * Applies a steering force toward [target] and takes over velocity from vanilla input and friction.
     * Vanilla water drag still applies after moving, so swimmers steer from their velocity before that drag
     * and add back what it will take off.
     */
    private fun steerTowards(target: Vec3, maxSpeed: Double, maxAccel: Double, arrive: Boolean, horizontalOnly: Boolean) {
        val velocity = restoreZeroedMovement()
        val drag = if (pokemonEntity.isWaterTravelling()) {
            val horizontal = pokemonEntity.getHorizontalWaterDrag().toDouble()
            Vec3(horizontal, VANILLA_WATER_VERTICAL_DRAG, horizontal)
        } else {
            Vec3(1.0, 1.0, 1.0)
        }
        val preDragVelocity = Vec3(velocity.x / drag.x, velocity.y / drag.y, velocity.z / drag.z)

        val mask = if (horizontalOnly) Vec3(1.0, 0.0, 1.0) else Vec3(1.0, 1.0, 1.0)
        val desired = seekVelocity(mob.position().multiply(mask), target.multiply(mask), maxSpeed, maxAccel, arrive)
        val force = steerForce(desired, preDragVelocity.multiply(mask), maxAccel)

        mob.deltaMovement = preDragVelocity.add(force)
        stopInputs()
        mob.setDiscardFriction(true)
    }

    private fun stopInputs() {
        mob.speed = 0F
        mob.yya = 0F
        mob.setZza(0F)
        mob.setXxa(0F)
    }

    /** aiStep() zeroes velocity components under MIN_MOVEMENT_DISTANCE which swallows slow steering, so put those back. */
    private fun restoreZeroedMovement(): Vec3 {
        val last = pokemonEntity.lastTravelMovement ?: return mob.deltaMovement
        val current = mob.deltaMovement
        fun restore(now: Double, before: Double) = if (now == 0.0 && abs(before) < LivingEntity.MIN_MOVEMENT_DISTANCE) before else now
        mob.deltaMovement = Vec3(restore(current.x, last.x), restore(current.y, last.y), restore(current.z, last.z))
        return mob.deltaMovement
    }

    /**
     * Speed where vanilla ground friction balances the walk input force, so steered walkers keep vanilla pace.
     * Could be replaced in the future with direct topSpeed calculations in the pokemon species form
     */
    private fun groundTopSpeed(speed: Double): Double {
        val blockFriction = mob.level()
            .getBlockState(mob.getBlockPosBelowThatAffectsMyMovement())
            .block.getFriction().toDouble()
        val frictionInfluencedSpeed = if (mob.onGround()) {
            speed * (VANILLA_GROUND_SPEED_FACTOR / (blockFriction * blockFriction * blockFriction))
        } else {
            VANILLA_FLYING_SPEED
        }
        val inputForce = speed * VANILLA_INPUT_SCALE * frictionInfluencedSpeed
        return inputForce / (1 - blockFriction * VANILLA_AIR_FRICTION)
    }

    /*
     * Returns the ideal water level y position of a given pokemon during battle.
     * This is separate for the eye height check and is dependent on if the pokemon is rafted or swimming.
     */
    private fun getStableBattleFloatHeight(): Double {
        val aABB: AABB = this.pokemonEntity.boundingBox
        return if (this.pokemonEntity.exposedForm.behaviour.moving.swim.canBreatheUnderwater) ((aABB.maxY - aABB.minY) / 2.0) else 0.05
    }

    /*
     * Returns true if a pokemon is touching a water block.
     * Based on vanilla Minecraft's Boat.checkInWater()
     */
    private fun checkInWater(): Boolean {
        val aABB: AABB = mob.boundingBox
        val i = Mth.floor(aABB.minX)
        val j = Mth.ceil(aABB.maxX)
        val k = Mth.floor(aABB.minY)
        val l = Mth.ceil(aABB.minY + 0.001)
        val m = Mth.floor(aABB.minZ)
        val n = Mth.ceil(aABB.maxZ)
        var bl = false
        this.waterLevel = -1.7976931348623157E308
        val mutableBlockPos = BlockPos.MutableBlockPos()
        for (o in i until j) {
            for (p in k until l) {
                for (q in m until n) {
                    mutableBlockPos[o, p] = q
                    val fluidState: FluidState = mob.level().getFluidState(mutableBlockPos)
                    if (fluidState.`is`(FluidTags.WATER)) {
                        val f = p.toFloat() + fluidState.getHeight(mob.level(), mutableBlockPos)
                        this.waterLevel = max(f.toDouble(), waterLevel)
                        bl = bl or (aABB.minY < f.toDouble())
                    }
                }
            }
        }
        return bl
    }

    /*
     * Returns true if a pokemon's y level is below it's idealized float height in battle.
     * Based on vanilla Minecraft's Boat.isUnderWater()
     */
    private fun isUnderwater(): Boolean {
        val aABB: AABB = this.pokemonEntity.boundingBox
        val d = aABB.minY + getStableBattleFloatHeight()
        val i = Mth.floor(aABB.minX)
        val j = Mth.ceil(aABB.maxX)
        val k = Mth.floor(aABB.minY)
        val l = Mth.ceil(aABB.maxY)
        val m = Mth.floor(aABB.minZ)
        val n = Mth.ceil(aABB.maxZ)
        val bl = false
        val mutableBlockPos = BlockPos.MutableBlockPos()
        for (o in i until j) {
            for (p in k until l) {
                for (q in m until n) {
                    mutableBlockPos[o, p] = q
                    val fluidState: FluidState = mob.level().getFluidState(mutableBlockPos)
                    if (fluidState.`is`(FluidTags.WATER) && d < (mutableBlockPos.y.toFloat() + fluidState.getHeight(mob.level(), mutableBlockPos)).toDouble()) {
                        return true
                    }
                }
            }
        }
        return bl
    }

    private fun isWalkable(xMovement: Float, zMovement: Float): Boolean {
        val entityNavigation = mob.navigation
        val pathNodeMaker = entityNavigation.nodeEvaluator
        return pathNodeMaker.getPathType(
            mob,
            BlockPos(
                Mth.floor(mob.x + xMovement.toDouble()),
                mob.blockY,
                Mth.floor(mob.z + zMovement.toDouble())
            )
        ) == PathType.WALKABLE
    }

    fun stop() {
        this.operation = Operation.WAIT
        strafeForwards = 0.0f
        strafeRight = 0.0f
    }
}