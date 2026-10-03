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
import com.cobblemon.mod.common.util.getWaterAndLavaIn
import com.cobblemon.mod.common.util.math.InertialEase
import com.cobblemon.mod.common.util.math.arriveSpeed
import com.cobblemon.mod.common.util.math.geometry.toRadians
import com.cobblemon.mod.common.util.resolveFloat
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.tags.BlockTags
import net.minecraft.tags.FluidTags
import net.minecraft.util.Mth
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.control.MoveControl
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.pathfinder.PathType
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import kotlin.math.sqrt

class PokemonMoveControl(val pokemonEntity: PokemonEntity) : MoveControl(pokemonEntity) {
    companion object {
        const val VERY_CLOSE = 2.500000277905201E-3
        const val TICKS_PER_SECOND = 20.0

        // Heading gets to a new direction within 1.5x this. Longer turns slower and smoother
        const val HEADING_BLEND_SECONDS = 0.7F
        // Speed gets to a new target within 1.5x this. Longer speeds up and slows down more gently
        const val SPEED_BLEND_SECONDS = 0.3F
        // Inertial easing can take up to 1.5x its blend time to arrive
        const val EASE_WORST_CASE_BLEND = 1.5
        // Velocity kept each tick in powered flight. Lower stops quicker, vanilla air keeps 0.91
        const val FLYER_DRAG = 0.7
        // Walkers ignore the task's speed so walk animations always match. Same as the walk_speed most tasks use
        const val GROUND_SPEED_MODIFIER = 0.35F
        // Turn size used to work out how fast a mon can turn
        const val REFERENCE_TURN_RADIANS = PI / 2
        // Inertial easing peaks at 1.5x its average rate
        const val PEAK_TURN_RADIANS_PER_TICK = 1.5 * REFERENCE_TURN_RADIANS / HEADING_BLEND_SECONDS / TICKS_PER_SECOND
        // Fliers and swimmers stop once this close to a target they are arriving at
        const val ARRIVAL_TOLERANCE = 0.05
        // Swimmers aim this far above their target, otherwise they get stuck on the lip when leaving water
        const val FLUID_EXIT_Y_OFFSET = 0.05

        // Vanilla values from LivingEntity.travel and aiStep
        const val VANILLA_INPUT_SCALE = 0.98
        const val VANILLA_GROUND_SPEED_FACTOR = 0.21600002
        const val VANILLA_AIR_FRICTION = 0.91
        const val VANILLA_FLYING_SPEED = 0.02

        /** Speed where vanilla ground friction on a block with [blockFriction] balances a walk input of [speed]. */
        fun vanillaGroundTopSpeed(speed: Double, blockFriction: Double): Double {
            val frictionInfluencedSpeed = speed * (VANILLA_GROUND_SPEED_FACTOR / (blockFriction * blockFriction * blockFriction))
            val inputForce = speed * VANILLA_INPUT_SCALE * frictionInfluencedSpeed
            return inputForce / (1 - blockFriction * VANILLA_AIR_FRICTION)
        }
    }

    val runtime = MoLangRuntime().also {
        it.environment.query.addStandardFunctions().addFunctions(pokemonEntity.struct.functions)
    }

    private var waterLevel : Double = 0.0
    var banking = false
    // Where path following is steering this tick, null when not following a path
    private var currentSteerTarget: Vec3? = null
    var bankDurationTicks = -1

    // Speed along the heading in blocks per tick of travel, eased so speed changes are smooth. Heading lives on the entity
    private val speedEase = InertialEase(SPEED_BLEND_SECONDS)
    // What steering asked for this tick, null when it isn't steering
    private var steerGoal: SteerGoal? = null

    private class SteerGoal(
        val yaw: Float,
        val pitch: Float,
        val direction: Vec3,
        val speed: Double,
        val topSpeed: Double,
        val distance: Double,
        val horizontalOnly: Boolean
    )
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
        steerGoal = null
        tickOperation()
        updateMotion()
    }

    private fun tickOperation() {
        val followedPath = currentSteerTarget != null
        currentSteerTarget = null
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
        val flying = pokemonEntity.getCurrentPoseType() in setOf(PoseType.FLY, PoseType.HOVER)
        val swimming = pokemonEntity.isEyeInFluid(FluidTags.WATER) || pokemonEntity.isEyeInFluid(FluidTags.LAVA)
        // Sprinting is ground only and has to be asked for by whatever started the path
        val sprintSpeed = behaviour.moving.walk.sprintSpeed
            ?.takeIf { operation == Operation.MOVE_TO && !flying && !swimming && pokemonEntity.navigation.sprinting }
        pokemonEntity.isPokemonSprinting = sprintSpeed != null

        val mediumSpeed = runtime.resolveFloat(if (flying) {
            behaviour.moving.fly.flySpeedHorizontal
        } else if (swimming) {
            behaviour.moving.swim.swimSpeed
        } else {
            behaviour.moving.walk.walkSpeed
        }) * 2.5F

        val taskSpeed = if (flying || swimming) this.speedModifier.toFloat() else GROUND_SPEED_MODIFIER
        val baseSpeed = mob.getAttributeValue(Attributes.MOVEMENT_SPEED).toFloat() * taskSpeed
        // Mult the baseSpeed by the square root of the effectivescale to prevent foot slide (alpha/baby scaling)
        val adjustedSpeed = baseSpeed * mediumSpeed * sqrt(pokemonEntity.pokemon.effectiveScale)
        // Sprint speed is an exact top speed in blocks per second, so it skips attributes, task speed and friction
        val sprintTopSpeed = sprintSpeed?.let {
            runtime.resolveFloat(it).toDouble() / TICKS_PER_SECOND * sqrt(pokemonEntity.pokemon.effectiveScale.toDouble())
        }

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
            val (inWater, inLava) = mob.level().getWaterAndLavaIn(mob.boundingBox)
            val inFluid = inWater || inLava
            val fluidMover = pokemonEntity.getBehaviourFlag(PokemonBehaviourFlag.FLYING) || inFluid
            val topSpeed = if (fluidMover) adjustedSpeed.toDouble() else sprintTopSpeed ?: groundTopSpeed(adjustedSpeed.toDouble())
            // Slowest the speed ease can come down from top speed, so guidance starts braking early enough
            val braking = topSpeed / (SPEED_BLEND_SECONDS * EASE_WORST_CASE_BLEND * TICKS_PER_SECOND)

            // Follow the path when there is one, otherwise go straight for the wanted position
            val steerTarget = pokemonEntity.navigation.getSteerTarget(topSpeed, braking)
            currentSteerTarget = steerTarget?.position
            if (steerTarget == null && followedPath && fluidMover) {
                // The path just ended or was cancelled, so glide to a stop instead of carrying on to the last waypoint.
                // Walkers stop on friction already
                val velocity = mob.deltaMovement
                val brakingDistance = velocity.lengthSqr() / (2 * braking)
                val stop = mob.position().add(velocity.normalize().scale(brakingDistance))
                setWantedPosition(stop.x, stop.y, stop.z, speedModifier)
            }
            val target = steerTarget?.position ?: Vec3(wantedX, wantedY, wantedZ)
            val arrive = steerTarget?.isFinal ?: true
            val maxSpeed = steerTarget?.maxSpeed ?: topSpeed

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
                    speedEase.snap(0F)
                }
                return
            }

            val horizontalDistanceFromTarget = xDist * xDist + zDist * zDist
            val closeHorizontally = horizontalDistanceFromTarget < VERY_CLOSE

            var verticalHandled = false
            val blockPos = mob.blockPosition()
            val blockState = mob.level().getBlockState(blockPos)
            val voxelShape = blockState.getCollisionShape(mob.level(), blockPos)

            if (fluidMover) {
                verticalHandled = true
                val fluidTarget = if (inFluid) target.add(0.0, FLUID_EXIT_Y_OFFSET, 0.0) else target
                yDist = fluidTarget.y - mob.y

                if (arrive && fluidTarget.distanceTo(mob.position()) < ARRIVAL_TOLERANCE) {
                    operation = Operation.WAIT
                    mob.deltaMovement = Vec3.ZERO
                    speedEase.snap(0F)
                    stopInputs()
                    return
                }
                steerTowards(fluidTarget, maxSpeed, topSpeed, braking, arrive, horizontalOnly = false)
            } else {
                steerTowards(target, maxSpeed, topSpeed, braking, arrive, horizontalOnly = true)
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
//                    val wantedBlockPos = BlockPos(wantedX.toInt(), (wantedY).toInt(), wantedZ.toInt())
//                    if (!voxelShape.isEmpty || (mob.y + pokemonEntity.behaviour.moving.stepHeight < mob.level().getBlockState(wantedBlockPos.below()).getCollisionShape(mob.level(), wantedBlockPos.below()).max(Direction.Axis.Y) + wantedBlockPos.below().y.toDouble())) {
                        mob.jumpControl.jump()
                        operation = Operation.JUMPING
//                    }
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

    /** Works out the heading and speed steering wants this tick. Locomotion turns them into thrust in [updateMotion] */
    private fun steerTowards(
        target: Vec3,
        maxSpeed: Double,
        topSpeed: Double,
        braking: Double,
        arrive: Boolean,
        horizontalOnly: Boolean
    ) {
        stopInputs()
        val offset = target.subtract(mob.position()).let { if (horizontalOnly) Vec3(it.x, 0.0, it.z) else it }
        val distance = offset.length()
        if (distance < VERY_CLOSE) return
        val direction = offset.scale(1 / distance)
        steerGoal = SteerGoal(
            yaw = (Mth.atan2(direction.z, direction.x) * Mth.RAD_TO_DEG).toFloat() - 90F,
            // Minecraft pitch is negative looking up
            pitch = -(Mth.atan2(direction.y, direction.horizontalDistance()) * Mth.RAD_TO_DEG).toFloat(),
            direction = direction,
            speed = if (arrive) arriveSpeed(distance, maxSpeed, braking) else maxSpeed,
            topSpeed = topSpeed,
            distance = distance,
            horizontalOnly = horizontalOnly
        )
    }

    /**
     * Eases heading and speed towards this tick's goal and works out the push that makes this tick's step match.
     * Vanilla moves by velocity plus push and then applies friction, so knockback, ice and falling all work like vanilla.
     */
    private fun updateMotion() {
        val goal = steerGoal
        val heading = pokemonEntity.heading
        val turnTarget = pokemonEntity.stationaryTurnTarget
        if (goal == null && turnTarget != null) {
            // Standing still and turning to look at something, same easing as travel so it's an S curve too
            heading.steer(turnTarget, 0F)
            mob.yRot = heading.yaw.value
            pokemonEntity.entityData.set(PokemonEntity.HEADING_TARGET, Vector3f(turnTarget, 0F, 1F))
            if (heading.yaw.isSettled()) {
                pokemonEntity.stationaryTurnTarget = null
            }
        } else if (goal == null) {
            heading.settle(mob.yRot, snap = true)
            pokemonEntity.entityData.set(PokemonEntity.HEADING_TARGET, Vector3f(mob.yRot, 0F, 0F))
        } else {
            pokemonEntity.stationaryTurnTarget = null
            heading.steer(goal.yaw, goal.pitch)
            mob.yRot = heading.yaw.value
            pokemonEntity.entityData.set(PokemonEntity.HEADING_TARGET, Vector3f(goal.yaw, goal.pitch, 1F))
        }

        val forward = heading.forward()
        val currentSpeed = mob.deltaMovement.dot(forward)
        // Walkers can't push off the air so a jump or fall just coasts
        val airborneWalker = goal != null && goal.horizontalOnly && !mob.onGround()
        if (goal == null || airborneWalker || pokemonEntity.isDeadOrDying) {
            // Not pushing, follow the real speed so steering picks up from it without a jump
            speedEase.snap(currentSpeed.toFloat())
            pokemonEntity.pendingThrust = Vec3.ZERO
            return
        }

        // Only go as fast as we're facing the right way, so turning around slows the mon down
        val facing = forward.dot(goal.direction).coerceIn(-1.0, 1.0)
        val turnLeft = acos(facing)
        // Turning circle through the target has radius d / (2 sin θ), and turn rate × radius is the speed that fits it
        val turnLimitedSpeed = if (sin(turnLeft) > 0) PEAK_TURN_RADIANS_PER_TICK * goal.distance / (2 * sin(turnLeft)) else goal.speed
        speedEase.update(min(goal.speed * facing.coerceAtLeast(0.0), turnLimitedSpeed).toFloat())

        // Vanilla steps velocity plus push, so the push is whatever makes up the difference. Capped at top speed
        // so it can't cancel knockback in one go
        val push = (speedEase.value - currentSpeed).coerceIn(-goal.topSpeed, goal.topSpeed)
        pokemonEntity.pendingThrust = forward.scale(push)
    }

    private fun blockFriction(): Double {
        return mob.level().getBlockState(mob.getBlockPosBelowThatAffectsMyMovement()).block.getFriction().toDouble()
    }

    private fun stopInputs() {
        mob.speed = 0F
        mob.yya = 0F
        mob.setZza(0F)
        mob.setXxa(0F)
    }

    /**
     * Speed where vanilla ground friction balances the walk input force, so steered walkers keep vanilla pace.
     * Could be replaced in the future with direct topSpeed calculations in the pokemon species form
     */
    private fun groundTopSpeed(speed: Double): Double {
        val blockFriction = blockFriction()
        if (mob.onGround()) {
            return vanillaGroundTopSpeed(speed, blockFriction)
        }
        val inputForce = speed * VANILLA_INPUT_SCALE * VANILLA_FLYING_SPEED
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