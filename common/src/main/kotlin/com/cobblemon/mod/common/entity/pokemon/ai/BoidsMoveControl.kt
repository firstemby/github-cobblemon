/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.entity.pokemon.ai


import com.cobblemon.mod.common.CobblemonActivities
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.math.geometry.toDegrees
import com.cobblemon.mod.common.util.math.geometry.toRadians
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.control.MoveControl
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

class BoidsMoveControl(val pokemonEntity: PokemonEntity, val target: LivingEntity) : MoveControl(pokemonEntity) {
    companion object {
        const val VERY_CLOSE = 2.500000277905201E-3
        const val MAX_SPEED = 0.3
        const val MAX_FORCE = 0.013
    }

    // TODO: might need to hold its own velocity so minecraft doesn't do its nonsense to it?
    // Can handle collisions ourselves as well
    var speed: Double? = null

    override fun tick() {

        // Steering constants
        val orbitRadius = 16.0
        val targetPos = target.position().add(0.0, 4.0, 0.0)
        val arrivalRange = 2.0 // in blocks

        // Handle collision
        if (pokemonEntity.horizontalCollision) {
            pokemonEntity.setYRot(pokemonEntity.getYRot() + 180.0f)
            this.speed = 0.1
        }

        // Set this to ensure the flying pose is active
        pokemonEntity.setFlying(true)
        // Just so I can test stuff
        pokemonEntity.resetFallDistance()
        // ignore all the friction stuff from outside
        speed?.let { pokemonEntity.setDeltaMovement(pokemonEntity.deltaMovement.normalize().scale(it)) }

        val cubeWidth = 15.0
        val cubeHeight = 6.0
        val cubeCenter = target.position().add(cubeWidth, cubeHeight / 2.0, 0.0)
        forceRectBoundary(cubeCenter, cubeWidth, cubeHeight)

        // TODO: maybe move this to a sensor that lives spins up on the boid activity creation
        // Get a list of other boid entities in this entity's vicinity
        val brain = pokemonEntity.brain
        val boids = brain.getMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES).map { nearbyEntities ->
            nearbyEntities.filterIsInstance<PokemonEntity>().filter { pokemon ->
                pokemon.brain.activeNonCoreActivity.orElse(null) == CobblemonActivities.POKEMON_BOIDS
            }
        }.orElse(emptyList())

        // separation
        val separationForce = separation(boids)?.scale(0.8)
        separationForce?.let { force ->
            pokemonEntity.deltaMovement = pokemonEntity.deltaMovement.add(force)
        }
        // alignment
        val alignmentForce = alignment(boids)?.scale(0.7)
        alignmentForce?.let { force ->
            pokemonEntity.deltaMovement = pokemonEntity.deltaMovement.add(force)
        }

        // cohesion
        val cohesionForce = cohesion(boids)?.scale(0.6)
        cohesionForce?.let { force ->
            pokemonEntity.deltaMovement = pokemonEntity.deltaMovement.add(force)
        }


        // Apply steering force to current movement
//        val steerForce = steerToTargetForce(targetPos, speedLimit, maxForce, arrivalRange)
//        pokemonEntity.setDeltaMovement(pokemonEntity.deltaMovement.add(steerForce))

        // Take note of speed again to avoid minecraft's friction logic
        speed = pokemonEntity.deltaMovement.length()

        // Limit upwards speed to be half of max speed. The boids kept bumping into the top of the box more so than birds
        // would. Honestly birds probably mostly want to move in the x,z plane anyhow
        pokemonEntity.deltaMovement = Vec3(
            pokemonEntity.deltaMovement.x,
            min(pokemonEntity.deltaMovement.y, MAX_SPEED),
            pokemonEntity.deltaMovement.y
        ).normalize().scale(speed!!)

        orientEntityToTravel()
    }


    // If on arrivalRange is not specified then simply don't arrive
    private fun steerToTargetForce(targetPos: Vec3, maxSpeed: Double, maxForce: Double, arrivalRange: Double? = null): Vec3 {

        // Ensure we can't have a negative range here
        val absArrivalRange = arrivalRange?.let { abs(arrivalRange) }

        // Calculate the vector from the pokemons current position to the
        // target position. Set its desired magnitude to max speed or a
        // portion of it if within the arrivalRange
        val toTargetVec = targetPos.subtract(pokemonEntity.position())

        val distToTarget = toTargetVec.length()
        val desiredSpeed = if (absArrivalRange != null && distToTarget < absArrivalRange) (distToTarget/absArrivalRange) * maxSpeed else maxSpeed
        val desiredVec = toTargetVec.normalize().scale(desiredSpeed)

        var steer = desiredVec.subtract(pokemonEntity.deltaMovement)
        if (steer.length() > maxForce) {
            steer = steer.normalize().scale(maxForce)
        }

        return steer
    }

    private fun separation(boids: List<PokemonEntity>): Vec3? {
        val desiredSeperation = 1.5
        var seperationVec: Vec3? = null

        // Gather the total separation vec of all close entities
        boids.forEach { boid ->
            val toTargetVec = pokemonEntity.position().subtract(boid.position())
            val currDist = toTargetVec.length()
            if (currDist < desiredSeperation) {
                if (seperationVec == null) seperationVec = Vec3.ZERO
                // Make the seperation force inversely proprotional the serperation distance
                seperationVec = seperationVec.add(toTargetVec.normalize().scale(1.0 - (currDist / desiredSeperation)))
            }
        }

        if (seperationVec != null) {
            seperationVec = seperationVec.normalize().scale(MAX_SPEED)
            seperationVec = seperationVec.subtract(pokemonEntity.deltaMovement)
            if (seperationVec.length() > MAX_FORCE) {
                seperationVec = seperationVec.normalize().scale(MAX_FORCE)
            }
        }
        return seperationVec
    }

    private fun alignment(boids: List<PokemonEntity>): Vec3? {
        val neighborDist = 3.0
        var summedVel: Vec3? = null
        var alignmentVec: Vec3? = null
        var boidCount = 0

        // Gather the average position of the boid
        boids.forEach { boid ->
            val vecToOther = boid.position().subtract(this.pokemonEntity.position())
            val distToOther = vecToOther.length()
            val widthOfViewInDeg = 90
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

            alignmentVec = averageVel.normalize().scale(MAX_SPEED)
            alignmentVec = alignmentVec.subtract(pokemonEntity.deltaMovement)
            if (alignmentVec.length() > MAX_FORCE) {
                alignmentVec = alignmentVec.normalize().scale(MAX_FORCE)
            }
        }
        return alignmentVec
    }

    private fun cohesion(boids: List<PokemonEntity>): Vec3? {
        //val neighborDist = 5.0
        var summedPos: Vec3? = null
        var cohesionVec: Vec3? = null
        var boidCount = 0

        // Gather the average position of the boid
        boids.forEach { boid ->
            if (summedPos == null) summedPos = Vec3.ZERO
            summedPos = summedPos.add(boid.position())
            boidCount++
        }

        if (summedPos != null) {
            // Get the average position by dividing by the number of boids sampled
            val averagePos = summedPos.scale(1.0 / boidCount)
            // Get the vector towards that average position and scale
            cohesionVec = averagePos.subtract(pokemonEntity.position())
            cohesionVec = cohesionVec.normalize().scale(MAX_SPEED)
            cohesionVec = cohesionVec.subtract(pokemonEntity.deltaMovement)
            if (cohesionVec.length() > MAX_FORCE) {
                cohesionVec = cohesionVec.normalize().scale(MAX_FORCE)
            }
        }
        return cohesionVec
    }

    private fun forceRectBoundary(basePos: Vec3, rectWidth: Double, rectHeight: Double) {
        val maxBoundaryForce = MAX_FORCE * 2.0
        val correctiveForce = getRectCorrectiveForce(basePos, rectWidth, rectHeight)
        if (correctiveForce != null) {
            //val correctiveForce = steerToTargetForce(correctivePos, speedLimit, maxForce)
            var steer = correctiveForce.subtract(pokemonEntity.deltaMovement)
            if (steer.length() > maxBoundaryForce) {
                steer = steer.normalize().scale(maxBoundaryForce)
            }
            pokemonEntity.setDeltaMovement(pokemonEntity.deltaMovement.add(steer))
        }
    }

    // Returns the desired force to correct the entity back into the rectangle area
    private fun getRectCorrectiveForce( basePos: Vec3, rectWidth: Double, rectHeight: Double): Vec3? {
        val entityPos = pokemonEntity.position()
        val posFromBase = entityPos.subtract(basePos)
        val cubeHalfWidth = rectWidth / 2.0 // Useful as the basePoint is in the middle of the cube
        val cubeHalfHeight = rectHeight / 2.0

        // Check the three possible faces for exceeding the boundary (x, y, and z faces of cube)
        val xExceed = max(0.0, abs(posFromBase.x) - cubeHalfWidth)
        val yExceed = max(0.0, abs(posFromBase.y) - cubeHalfHeight)
        val zExceed = max(0.0, abs(posFromBase.z) - cubeHalfWidth)

        return if ( xExceed == 0.0 && yExceed == 0.0 && zExceed == 0.0) {
            null
        } else {
            var retVel = pokemonEntity.deltaMovement
            if (xExceed != 0.0) retVel = Vec3( -posFromBase.x.sign * abs(retVel.x), retVel.y, retVel.z)
            if (yExceed != 0.0) retVel = Vec3( retVel.x, -posFromBase.y.sign * abs(retVel.y), retVel.z)
            if (zExceed != 0.0) retVel = Vec3( retVel.x, retVel.y, -posFromBase.z.sign * abs(retVel.z))
            retVel = retVel.normalize().scale(MAX_SPEED)
            return retVel
        }
    }

    private fun orientEntityToTravel() {
        // Ensure the entity is facing the direction it is moving in both pitch and yaw
        val normVec = pokemonEntity.deltaMovement.normalize()
        pokemonEntity.yRot = Mth.atan2(normVec.z, normVec.x).toDegrees() - 90.0f
        pokemonEntity.xRot = asin(normVec.y.toFloat()).toDegrees()
    }
}

//val desiredScale = if (distToTarget > slowDownRadius) speedLimit else (distToTarget/slowDownRadius).pow(2) * speedLimit
//desiredVec = desiredVec.normalize().scale(desiredScale)