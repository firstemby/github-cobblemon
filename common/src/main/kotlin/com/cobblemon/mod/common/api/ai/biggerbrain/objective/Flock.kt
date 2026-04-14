/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain.objective


import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext
import com.cobblemon.mod.common.api.ai.ExpressionOrEntityVariable
import com.cobblemon.mod.common.api.ai.asVariables
import com.cobblemon.mod.common.api.ai.biggerbrain.BiggerBrain
import com.cobblemon.mod.common.api.ai.biggerbrain.MovementTracker
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.Distraction
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.idleBoredomDistraction
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.EntityFixation
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.MovementTrackerFixation
import com.cobblemon.mod.common.api.ai.config.objective.ObjectiveConfig
import com.cobblemon.mod.common.api.ai.resolveFloat
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.entity.pokemon.ai.PokemonMoveControl
import com.cobblemon.mod.common.entity.pokemon.extensions.steerToTarget
import com.cobblemon.mod.common.util.asExpression
import com.cobblemon.mod.common.util.getMemorySafely
import com.cobblemon.mod.common.util.math.geometry.toRadians
import com.mojang.datafixers.util.Either
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.math.cos

/**
 * Flock or School to stay cool
 * This objective and fixation is currently not working as intended and needs modification or scrapped.
 */
class Flock(
    val poseTypes: Set<PoseType> = setOf(PoseType.FLY, PoseType.HOVER, PoseType.SWIM, PoseType.FLOAT),
    val separationWeight: Float = 0.8f,
    val alignmentWeight: Float = 0.6f,
    val cohesionWeight: Float = 0.6f,
    val neighborRadius: Float = 8f,
    val maxForce: Double = 0.02
) : Objective {
    var fixation: EntityFixation? = null

    override fun getDescription(entity: LivingEntity) = "Flocking"

    override fun perform(
        entity: LivingEntity,
        brain: BiggerBrain,
        activeObjectiveTags: MutableSet<String>
    ): ObjectiveStatus {
        if (entity !is PokemonEntity) return ObjectiveStatus.INVALID
        val moveControl = entity.moveControl as? PokemonMoveControl ?: return ObjectiveStatus.INVALID

        if (brain.isFixation(fixation)) {
            activeObjectiveTags.add(Objective.OBJECTIVE_TAG_WANDERING)
            return ObjectiveStatus.FIXATED
        } else if (brain.fixation != null) {
            fixation = null
        }

        if (entity.getCurrentPoseType() !in poseTypes) return ObjectiveStatus.INACTIVE

        val leaderUUID = brain.getMemory(CobblemonMemories.HERD_LEADER)
            ?: if (brain.hasMemoryValue(CobblemonMemories.HERD_SIZE)) entity.uuid.toString() else return ObjectiveStatus.INACTIVE

        if (!brain.distract(idleBoredomDistraction)) return ObjectiveStatus.BLOCKED

        fixation = brain.fixate(FlockFixation(this, entity, leaderUUID, moveControl))
        return ObjectiveStatus.FIXATED
    }

    inner class FlockFixation(
        val objective: Flock,
        val pokemonEntity: PokemonEntity,
        val leaderUUID: String,
        val moveControl: PokemonMoveControl,
    ) : EntityFixation, MovementTrackerFixation {
        override val movementTracker: MovementTracker? = null
        var maxSpeed = moveControl.getTopSpeed(pokemonEntity).toFloat()
        var maxForce = 0.02 // TODO: possibly move these to configs
        var flockNumber = 0

        override fun getDescription(entity: LivingEntity) = "Flocking with ${this.flockNumber} friends"

        override fun canDistract(distraction: Distraction, entity: LivingEntity) =
            distraction.isForce || distraction.isComfort || distraction.isDamage

        override fun onDistract(distraction: Distraction, entity: LivingEntity) {}

        override fun tick(entity: LivingEntity, brain: BiggerBrain): Boolean {
            if (entity !is PokemonEntity) return false
            if (entity.getCurrentPoseType() !in objective.poseTypes) return false
            val isLeader = leaderUUID == pokemonEntity.uuid.toString()
            if (!isLeader && brain.getMemory(CobblemonMemories.HERD_LEADER) != leaderUUID) return false


            this.maxSpeed = moveControl.getTopSpeed(pokemonEntity).toFloat() * 1.0f // TODO: can this be made to be static per species? and then friction calcs are done elsewhere?
            this.maxForce = 0.02
            val flockMates = getFlockMates(brain)
            this.flockNumber = flockMates.size
            val force = computeBoidForce(flockMates)
            applyBoidForce(force)
            return true
        }

        private fun getFlockMates(brain: BiggerBrain): List<PokemonEntity> {
            val isLeader = leaderUUID == pokemonEntity.uuid.toString()

            val followers = brain.getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)
                ?.findAll {
                    it is PokemonEntity && it != pokemonEntity &&
                            it.brain.getMemorySafely(CobblemonMemories.HERD_LEADER).orElse(null) == leaderUUID
                }
                ?.filterIsInstance<PokemonEntity>()
                ?: emptyList()

            if (isLeader) return followers

            val level = pokemonEntity.level() as? ServerLevel
            val leader = level?.getEntity(UUID.fromString(leaderUUID)) as? PokemonEntity
            return if (leader != null) followers + leader else followers
        }

        /**
         * Compute all forces involved in classic boid behaviour.
         * Each returns a Vec3 force, scale by respective weight, sum them
         */
        private fun computeBoidForce(flockMates: List<PokemonEntity>): Vec3 {
            // TODO: implement separation, alignment, cohesion

            var totalForce = Vec3.ZERO

            val separationForce = this.separation(flockMates)?.scale(objective.separationWeight.toDouble())
            separationForce?.let { totalForce = totalForce.add(it) }

            val alignmentForce = this.alignment(flockMates)?.scale(objective.alignmentWeight.toDouble())
            alignmentForce?.let { totalForce = totalForce.add(it) }

            val cohesionForce = this.cohesion(flockMates)?.scale(objective.cohesionWeight.toDouble())
            cohesionForce?.let { totalForce = totalForce.add(it) }

            val vel = pokemonEntity.deltaMovement
            val dir = if (vel.lengthSqr() > 0.0001) vel.normalize() else Vec3(
                -kotlin.math.sin(Math.toRadians(pokemonEntity.yRot.toDouble())),
                0.0,
                kotlin.math.cos(Math.toRadians(pokemonEntity.yRot.toDouble()))
            )
            val pointAhead = pokemonEntity.position().add(dir.scale(4.0))
            val forwardForce = pokemonEntity.steerToTarget(pointAhead, maxSpeed.toDouble(), maxForce)
            //totalForce = totalForce.add(dir.scale(maxForce * 1.5))

            return totalForce
        }

        private fun separation(flockMates: List<PokemonEntity>): Vec3? {
            var seperationVec: Vec3? = null

            // Gather the total separation vec of all close entities
            flockMates.forEach { boid ->
                val toTargetVec = pokemonEntity.position().subtract(boid.position())
                val desiredSeperation = boid.bbWidth * 2.0 // Base the seperation distance on the model width
                val currDist = toTargetVec.length()
                if (currDist < desiredSeperation) {
                    if (seperationVec == null) seperationVec = Vec3.ZERO
                    // Make the seperation force inversely proprotional to the serperation distance
                    // Also, scale the force by the bbwidth of the model so
                    seperationVec = seperationVec.add(toTargetVec.normalize().scale((1.0 - (currDist / desiredSeperation)) ))
                }
            }

            if (seperationVec != null) {
                //if (seperationVec.length() > this.maxSpeed) seperationVec = seperationVec.normalize().scale(this.maxSpeed)
                seperationVec = seperationVec.normalize().scale(this.maxSpeed.toDouble())
                seperationVec = seperationVec.subtract(pokemonEntity.deltaMovement)
                if (seperationVec.length() > this.maxForce) {
                    seperationVec = seperationVec.normalize().scale(this.maxForce)
                }
            }
            return seperationVec
        }

        private fun alignment(flockMates: List<PokemonEntity>): Vec3? {
            val neighborDist = objective.neighborRadius
            var summedVel: Vec3? = null
            var alignmentVec: Vec3? = null
            var boidCount = 0

            // Gather the average position of the boid
            flockMates.forEach { boid ->
                val vecToOther = boid.position().subtract(this.pokemonEntity.position())
                val distToOther = vecToOther.length()
                val widthOfViewInDeg = 360
                val isInSightLine = pokemonEntity.deltaMovement.normalize().dot(vecToOther) > cos((widthOfViewInDeg / 2.0).toRadians())

                if (isInSightLine && distToOther < neighborDist) {
                    if (summedVel == null) summedVel = Vec3.ZERO
                    summedVel = summedVel.add(boid.deltaMovement.normalize())
                    boidCount++
                }
            }

            if (summedVel != null) {
                // Get the average vel/alignment by dividing by the number of boids sampled
                val averageVel = summedVel.scale(1.0 / boidCount)

                //alignmentVec =  if (averageVel.length() > this.maxSpeed) averageVel.normalize().scale(this.maxSpeed) else averageVel
                alignmentVec = averageVel.normalize().scale(this.maxSpeed.toDouble())
                alignmentVec = alignmentVec.subtract(pokemonEntity.deltaMovement)
                if (alignmentVec.length() > this.maxForce) {
                    alignmentVec = alignmentVec.normalize().scale(this.maxForce)
                }
            }
            return alignmentVec
        }

        private fun cohesion(flockMates: List<PokemonEntity>): Vec3? {
            var summedPos: Vec3? = null
            var cohesionVec: Vec3? = null
            var boidCount = 0
            val cohesionRadius = objective.neighborRadius

            // Gather the average position of the boid
            flockMates.forEach { boid ->
                val vecToOther = boid.position().subtract(this.pokemonEntity.position())
                val distToOther = vecToOther.length()
                val widthOfViewInDeg = 360
                val isInSightLine = pokemonEntity.deltaMovement.normalize().dot(vecToOther) > cos((widthOfViewInDeg / 2.0).toRadians())

                if (isInSightLine && distToOther < cohesionRadius) { // Only use members that are within a certain radius and eyeline
                    if (summedPos == null) summedPos = Vec3.ZERO
                    summedPos = summedPos.add(boid.position())
                    boidCount++
                }
            }

            if (summedPos != null) {
                // Get the average position by dividing by the number of boids sampled
                var averagePos = summedPos.scale(1.0 / boidCount)

                // Further weight the entire boid towards the leaders position 50/50 all and leader
                val isLeader = leaderUUID == pokemonEntity.uuid.toString()
                if (!isLeader) {
                    val level = pokemonEntity.level() as? ServerLevel
                    val leader = level?.getEntity(UUID.fromString(leaderUUID)) as? PokemonEntity
                    leader?.let { averagePos = averagePos.add(it.position()).scale(0.5) }
                }

                // Get the vector towards that average position and scale
                cohesionVec = averagePos.subtract(pokemonEntity.position())
                //if (cohesionVec.length() > this.maxSpeed) cohesionVec = cohesionVec.normalize().scale(this.maxSpeed)
                cohesionVec = cohesionVec.normalize().scale(this.maxSpeed.toDouble())
                cohesionVec = cohesionVec.subtract(pokemonEntity.deltaMovement)
                if (cohesionVec.length() > this.maxForce) {
                    cohesionVec = cohesionVec.normalize().scale(this.maxForce)
                }
            }

            return cohesionVec
        }

        private fun applyBoidForce(force: Vec3) {
            var finalForce = force
            if (finalForce.length() < maxForce) {
                finalForce = finalForce.normalize().scale(maxForce)
            }
            pokemonEntity.deltaMovement = pokemonEntity.deltaMovement.add(finalForce)
        }
    }

    class Config : ObjectiveConfig {
        val poseTypes: Set<PoseType> = setOf(PoseType.FLY, PoseType.HOVER, PoseType.SWIM, PoseType.FLOAT)
        val separationWeight: ExpressionOrEntityVariable = Either.left("0.8".asExpression())
        val alignmentWeight: ExpressionOrEntityVariable = Either.left("0.6".asExpression())
        val cohesionWeight: ExpressionOrEntityVariable = Either.left("0.6".asExpression())
        val neighborRadius: ExpressionOrEntityVariable = Either.left("8.0".asExpression())
        val maxForce: ExpressionOrEntityVariable = Either.left("0.02".asExpression())

        override fun getVariables(entity: LivingEntity, behaviourConfigurationContext: BehaviourConfigurationContext) =
            listOf(separationWeight, alignmentWeight, cohesionWeight, neighborRadius, maxForce).asVariables()

        override fun create(entity: LivingEntity, behaviourConfigurationContext: BehaviourConfigurationContext): Objective? {
            if (entity !is PokemonEntity) return null
            return Flock(
                poseTypes = poseTypes,
                separationWeight = separationWeight.resolveFloat(behaviourConfigurationContext.runtime),
                alignmentWeight = alignmentWeight.resolveFloat(behaviourConfigurationContext.runtime),
                cohesionWeight = cohesionWeight.resolveFloat(behaviourConfigurationContext.runtime),
                neighborRadius = neighborRadius.resolveFloat(behaviourConfigurationContext.runtime),
                maxForce = maxForce.resolveFloat(behaviourConfigurationContext.runtime).toDouble()
            )
        }
    }
}
