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
import com.cobblemon.mod.common.client.render.debug.EntityDebugLine
import com.cobblemon.mod.common.entity.PlatformType
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.entity.ai.OmniPathNavigation
import com.cobblemon.mod.common.entity.ai.steering.SteerArbitrator
import com.cobblemon.mod.common.entity.ai.steering.rules.ObstacleAvoidRule
import com.cobblemon.mod.common.entity.ai.steering.rules.SeekRule
import com.cobblemon.mod.common.entity.ai.steering.rules.WanderRule
import com.cobblemon.mod.common.entity.pokemon.PokemonBehaviourFlag
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.entity.pokemon.extensions.*
import com.cobblemon.mod.common.util.getWaterAndLavaIn
import com.cobblemon.mod.common.util.math.geometry.toRadians
import com.cobblemon.mod.common.util.resolveFloat
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
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

    var obsCorrectDir = 0F
    var drawLines: MutableList<EntityDebugLine> = mutableListOf()
    var prevForce: Vec3? = null

    var steerSpeed = this.getTopSpeed(pokemonEntity)
    private val steerArbitrator by lazy {
        val steerArbitrator = SteerArbitrator(
            entity = pokemonEntity,
            maxForce = 0.02,
            rules = mutableListOf(
                ObstacleAvoidRule(
                    entity = pokemonEntity,
                    debugColor = Triple(1f, 0f, 0f),
                ),
                SeekRule(
                    entity = pokemonEntity,
                    priority = 10,
                    maxForce = 0.02,
                    debugColor = Triple(0f, 1f, 0f),
                    horzOnly = true,
                    targetProvider = { e ->
                        (e.navigation as? OmniPathNavigation)?.pathFollowTarget //TODO: maybe change this seems silly. Probably want a rule that checks explicitly?
                    }
                ),
                WanderRule(
                    entity = pokemonEntity,
                    debugColor = Triple(0f, 1f, 1f),
                    horzOnly = true
                )
            )
        )
        steerArbitrator
    }


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
        val adjustedSpeed = baseSpeed * mediumSpeed

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
        // Here we call the wander on every pokemon no matter what then they wander then leave
        // Just ignore all operatations and do whatever is being tested.
        if (false) {
//            val horzOnly = true
//
//            var topSpeed = gatherTopSpeed(pokemonEntity)
//            var maxForce = 0.02 // Arbitrarily chosen as of right now.
//            pokemonEntity.isPokemonSprinting = false
//
//            // grab forward travel vector direction
//            val forwardVec = if (pokemonEntity.deltaMovement.horizontalDistanceSqr() < 0.001) {
//                // No meaningful velocity so derive from yaw orientation instead
//                val yaw = Math.toRadians(pokemonEntity.yRot.toDouble())
//                Vec3(-sin(yaw), 0.0, cos(yaw))
//            } else {
//                if (horzOnly) Vec3(pokemonEntity.deltaMovement.x, 0.0, pokemonEntity.deltaMovement.z).normalize()
//                else pokemonEntity.deltaMovement.normalize()
//            }
//            val pointAhead = pokemonEntity.position().add(forwardVec)
//            val aheadForce = pokemonEntity.steerToTarget(pointAhead, topSpeed, maxForce, horzOnly = horzOnly)
//
//
////            val player = pokemonEntity.level().getNearestPlayer(pokemonEntity, 10.0) ?: return
////            // Flee direction — away from player, flattened to XZ
////            val toPlayerVec = pokemonEntity.position().subtract(player.position())
////
////            if (toPlayerVec.length() < 6.0) {
////                pokemonEntity.isPokemonSprinting = true
//////                topSpeed = 0.4  // hardcoded high value
//////                maxForce = 0.04
////                topSpeed *= 2.0f
////                maxForce *= 2
////            }
////
////            val fleePoint = pokemonEntity.position().add(toPlayerVec)
////            val aheadForce = pokemonEntity.steerToTarget(fleePoint, topSpeed, maxForce, horzOnly = horzOnly)
//
//            // For now just move forward by seeking towards a point ahead
//            val lookAheadTicks = 10 // How far in the future of this entities current movement to look ahead for collisions
//            val lookAheadDist = max(pokemonEntity.deltaMovement.length() * lookAheadTicks, pokemonEntity.bbWidth / 2.0) // At least always look ahead the width of the mons own hitbox
//
//            // Get the collision box
//            val inflationAmount = 0.0 //pokemonEntity.bbWidth / 5.0 //TODO: is this needed? it seems to help a bit keeping them off walls but would stop them from entering gaps they truly could
//            val collBox = pokemonEntity.boundingBox.move(forwardVec.scale(lookAheadDist)).inflate(inflationAmount, 0.0, inflationAmount) // Inflate a bit
//            val shapes = pokemonEntity.level().getBlockCollisions(pokemonEntity, collBox)
//
//            // Gather the nearest shape
//            val nearestShape = shapes.minByOrNull { shape ->
//                shape.bounds().center.distanceToSqr(pokemonEntity.position())
//            }
//
//            var avoidancForce = Vec3.ZERO
//
//            if (nearestShape != null) {
//                var hasLeftForce = false
//                var hasRightForce = false
//                val right = Vec3(-forwardVec.z, 0.0, forwardVec.x) //TODO: expand to 3 dimensions
//
//                val obsCenter = nearestShape.bounds().center
//                val toObs = obsCenter.subtract(pokemonEntity.position())
//
//                // Get the lateral avoidance point to seek to (the avoidance force really)
//                // It is the negated vector of toObs - Proj(toObs->forwardVec)
//                val projVec = forwardVec.scale(toObs.dot(forwardVec)) // Can do this because forwardVec is normalized
//                val lateralComponent = toObs.subtract(projVec) // TODO: moved back so that two opposing forces turn the dude around
//
//                avoidancForce = if (lateralComponent.lengthSqr() < 0.001) { // If its dead ahead then just turn right
//                    val rightVec = forwardVec.cross(Vec3(0.0,1.0,0.0))
//                    obsCorrectDir = 1F
//                    pokemonEntity.steerToTarget(pokemonEntity.position().add(rightVec), topSpeed, maxForce, horzOnly = horzOnly)
//                } else {
//                    // Scale the strength of the avoidance vec based off how close to being dead center it is
//
//                    var avoidanceVec = lateralComponent.scale(-1.0)
//                    if (horzOnly) avoidanceVec = Vec3(avoidanceVec.x, 0.0, avoidanceVec.z)
//                    //var avoidStrength = (abs(toObs.dot(avoidanceVec)) / (pokemonEntity.bbWidth / 2.0) ).coerceIn(0.0, 1.0)
//
//                    // 0.5 to add half the block width allowance since if it catches the corner of a block it still gets the center
//                    var avoidStrength = sqrt(1 - (avoidanceVec.length() / (pokemonEntity.bbWidth / 2.0 + 0.5) ).coerceIn(0.0, 0.7))
//                    // Track which side this force is pushing toward
//                    //val newObsCorrectDir = if (avoidanceVec.normalize().dot(right) > 0) 1F else -1f
//
//                    // If the new direction is opposite the last direction then invert it
////                    if (obsCorrectDir == 0F) {
////                        obsCorrectDir = newObsCorrectDir
////                    } else if (newObsCorrectDir != obsCorrectDir) {
////                        avoidanceVec = avoidanceVec.scale(-1.0)
////                        //avoidStrength = 1.0
////                    }
//
//                    val avoidancePoint = pokemonEntity.position().add(avoidanceVec)
//                    pokemonEntity.steerToTarget(avoidancePoint, topSpeed, maxForce * avoidStrength, horzOnly = horzOnly)
//                }
//            } else {
//                obsCorrectDir = 0F
//            }
//
//            // accumulte forces
//            // TODO: steertoTarget also checks stuff like this. Should that be different? The forces could compound here
//            val accumForce = aheadForce.scale(0.5).add(avoidancForce.scale(1.0))
//            var finalForce = if( avoidancForce == Vec3.ZERO) aheadForce
//                else avoidancForce
//
//            prevForce?.let { finalForce = it.lerp(finalForce, 0.1) }
//            prevForce = finalForce
//
//            pokemonEntity.deltaMovement = pokemonEntity.deltaMovement.add(finalForce)
//
//            // Must be called to make sure we have complete control over our entities velocity.
//            mob.speed = 0f
//            mob.setZza(0f)
//            mob.setXxa(0f)
//            mob.setDiscardFriction(true)
//            pokemonEntity.orientEntityToTravel(1.0f)
//            pokemonEntity.setFlying(false)
//
//            drawLines.add(EntityDebugLine(forwardVec))
//            if (drawLines.isNotEmpty()) {
//                sendPacketToTrackingPlayers(pokemonEntity, ClientboundUpdateObsAvoidPacket(pokemonEntity.id, drawLines.toList()))
//                drawLines.clear()
//            }

            steerSpeed = getTopSpeed(pokemonEntity)
            val force = steerArbitrator.calculate(debug = true)
            pokemonEntity.deltaMovement = pokemonEntity.deltaMovement.add(force)
            mob.speed = 0f
            mob.setZza(0f)
            mob.setXxa(0f)
            mob.setDiscardFriction(true)
            pokemonEntity.orientEntityToTravel(1.0f)

        } else if (operation == Operation.STRAFE) {
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
            var xDist = wantedX - mob.x
            var zDist = wantedZ - mob.z
            var yDist = wantedY - mob.y

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
                // NEW
                pokemonEntity.orientEntityToTravel(1.0f)

                // OLD
//                val angleToTarget = Mth.atan2(zDist, xDist).toDegrees() - 90.0f
//                val currentMovingAngle = mob.yRot
//                val steppedAngle = Mth.approachDegrees(currentMovingAngle, angleToTarget,  100 * mediumSpeed)
//                mob.yRot = steppedAngle
            }

            val (inWater, inLava) = mob.level().getWaterAndLavaIn(mob.boundingBox)
            val inFluid = inWater || inLava
            var verticalHandled = false
            val blockPos = mob.blockPosition()
            val blockState = mob.level().getBlockState(blockPos)
            val voxelShape = blockState.getCollisionShape(mob.level(), blockPos)

            if (pokemonEntity.getBehaviourFlag(PokemonBehaviourFlag.FLYING) || inFluid) {
                verticalHandled = true
                mob.yya = 0F
                mob.speed = 0F
                // Refinement is to prevent the entity from spinning around trying to get to a super precise location.
                val refine: (Double) -> Double = { if (abs(it) < 0.05) 0.0 else it }

                val fullDistance = Vec3(
                    xDist,
                    refine(yDist + 0.05), // + 0.05 for dealing with swimming out of water, they otherwise get stuck on the lip
                    zDist
                )


                val speed = adjustedSpeed.toDouble()
                //val scale = min(adjustedSpeed.toDouble(), fullDistance.length())

                yDist = fullDistance.y

                val maxForce = 0.015

                // Gather the point to be steered to
                val nav = pokemonEntity.navigation
                nav.pathFollowTarget?.let { rt ->
                    wantedX = rt.x
                    wantedZ = rt.z
                    wantedY = rt.y
                }
                // + 0.05 for dealing with swimming out of water, they otherwise get stuck on the lip
                val steerForce = pokemonEntity.steerToTarget(Vec3(wantedX, refine(wantedY + 0.05), wantedZ), speed, maxForce)
                mob.deltaMovement = mob.deltaMovement.add(steerForce)

                mob.speed = 0f
                mob.setZza(0f)
                mob.setXxa(0f)
                mob.setDiscardFriction(true)
            } else {
                // division is to slow the speed down a bit so they don't overshoot when they get there.
                var forwardSpeed = adjustedSpeed
                var minForce = 0.015 // If its not at least this it breaks quite a few things. looking at you snorlax

                val finalGoal: BlockPos? = pokemonEntity.navigation.path?.target
                val distanceToFinalGoal = finalGoal?.let {
                    pokemonEntity.position().distanceTo(it.center).toFloat()
                } ?: 0.0f

                // TODO: sprinting should likely be dictated by an objective?
//                if (distanceToFinalGoal > 8f) { //TODO: scale this to hitbox size
//                    pokemonEntity.isPokemonSprinting = true
//                    forwardSpeed *= 1.4f
//                    minForce = 0.02 //TODO: figure out what to actually do for this
//                } else {
//                    pokemonEntity.isPokemonSprinting = false
//                }

                // New way
                val blockFriction = mob.level()
                    .getBlockState(mob.getBlockPosBelowThatAffectsMyMovement())
                    .block.getFriction()
                val frictionInfluencedSpeed = if (mob.onGround()) {
                    forwardSpeed * (0.21600002f / (blockFriction * blockFriction * blockFriction))
                } else {
                    0.02f // getFlyingSpeed() default for non-player controlled. Also hopefully our checks above this caught this case
                }


                val baseForce = (forwardSpeed * 0.98) * frictionInfluencedSpeed

                // We find where the equilibrium between vanilla friction and the input force usually is to get a topSpeed for this mon.
                // Could be replaced in the future with direct topSpeed calculations in the pokemon species form
                val frictionAppliedAfter = blockFriction * 0.91
                val topSpeed = baseForce / (1 - frictionAppliedAfter)
                val currSpeed = pokemonEntity.deltaMovement.length()

                //TODO: is this needed?
//                if (!pokemonEntity.isPokemonSprinting && currSpeed > topSpeed) { // TODO: this might mess up some stuff like tnt pushing mobs around
//                    pokemonEntity.deltaMovement = pokemonEntity.deltaMovement.normalize().scale(lerp(currSpeed, topSpeed, 0.1))
//                }

                // Get a force that looks natural
                val maxForce = minForce //max(baseForce * forceDiv, minForce)

                // Gather the point to be steered to
                val nav = pokemonEntity.navigation
                nav.pathFollowTarget?.let { rt ->
                    wantedX = rt.x
                    wantedZ = rt.z
                    wantedY = rt.y
                }

                val steerForce = pokemonEntity.steerToTarget(
                    Vec3(wantedX, wantedY, wantedZ),
                    topSpeed, // Is never reached as minecraft relies on top speed being obtained when force=friction
                    maxForce,
                    horzOnly = true
                )

                // If this happens a lot then we have issues
                val mdt = LivingEntity.MIN_MOVEMENT_DISTANCE
                if ( abs(steerForce.x) < mdt || abs(steerForce.y) < mdt || abs(steerForce.z) < mdt) {
                    //Cobblemon.LOGGER.info("INFO: Steer Force dimension smaller than minimum movement distance value") //TODO: figure this out
                }

                pokemonEntity.deltaMovement = pokemonEntity.deltaMovement.add(steerForce)
                //pokemonEntity.deltaMovement = pokemonEntity.deltaMovement.add(Vec3(0.0103766, 0.0, 0.0))
                mob.speed = 0f
                mob.setZza(0f)
                mob.setXxa(0f)
                mob.setDiscardFriction(true)

                // Old way
//                mob.speed = forwardSpeed
            }

            if (!verticalHandled) {
                val tooBigToStep = yDist > pokemonEntity.behaviour.moving.stepHeight
                val xComponent = -Mth.sin(mob.yRot.toRadians()).toDouble()
                val zComponent = Mth.cos(mob.yRot.toRadians()).toDouble()

                val motion = Vec3(xComponent, 0.0, zComponent).normalize()
                val offset = motion.scale(mob.speed.toDouble())
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

    fun getTopSpeed(pokemonEntity: PokemonEntity): Double {
        val behaviour = pokemonEntity.behaviour
        var flyingOrSwimming = false
        val mediumSpeed = runtime.resolveFloat(if (pokemonEntity.getCurrentPoseType() in setOf(PoseType.FLY, PoseType.HOVER)) {
            flyingOrSwimming = true
            behaviour.moving.fly.flySpeedHorizontal
        } else if (pokemonEntity.isEyeInFluid(FluidTags.WATER) || pokemonEntity.isEyeInFluid(FluidTags.LAVA)) {
            flyingOrSwimming = true
            behaviour.moving.swim.swimSpeed
        } else {
            behaviour.moving.walk.walkSpeed
        }) * 2.5F //TODO: idk why this is 2.5 but I think we're stuck with it

        val baseSpeed = mob.getAttributeValue(Attributes.MOVEMENT_SPEED).toFloat() * 0.4f //TODO: does this ever need to not be hardcoded?
        val adjustedSpeed = baseSpeed * mediumSpeed

        val forwardSpeed = adjustedSpeed

        val blockFriction = mob.level()
            .getBlockState(mob.getBlockPosBelowThatAffectsMyMovement())
            .block.getFriction()
        val frictionInfluencedSpeed = if (mob.onGround() || flyingOrSwimming) { // TODO: should flyingOrSwimming be treated completely differently?
            forwardSpeed * (0.21600002f / (blockFriction * blockFriction * blockFriction))
        } else {
            0.02f // getFlyingSpeed() default for non-player controlled
        }

        // Force applied every tick through the usual vanilla route
        val baseForce = (forwardSpeed * 0.98) * frictionInfluencedSpeed

        // We find where the equilibrium between vanilla friction and the input force usually is to get a topSpeed for this mon.
        // Could be replaced in the future with direct topSpeed calculations in the pokemon species form
        val frictionAppliedAfter = blockFriction * 0.91
        val topSpeed = baseForce / (1 - frictionAppliedAfter)

        return topSpeed
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