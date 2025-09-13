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
import com.cobblemon.mod.common.util.toVec3d
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.control.MoveControl
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Level
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import org.joml.Matrix3f
import org.joml.Vector3f
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

class BoidsMoveControl(val pokemonEntity: PokemonEntity, val target: LivingEntity) : MoveControl(pokemonEntity) {
    companion object {
        const val VERY_CLOSE = 2.500000277905201E-3
        const val MAX_SPEED = 0.3
        const val MAX_FORCE = 0.013
    }

    // TODO: might need to hold its own velocity so minecraft doesn't do its nonsense to it?
    // Can handle collisions ourselves as well
    var speed: Double? = null

    // wander vars
    var currHorzWander = 0.0
    var currVertWander = 0.0
    val wanderDisp = 15.0 // The degrees of change from tick to tick in wander angle

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

        // Constrain to rectangular boundary
        val cubeWidth = 16.0
        val cubeHeight = 10.0
        val cubeCenter = target.position().add(0.0, cubeHeight / 2.0, 0.0)
        //forceRectBoundary(cubeCenter, cubeWidth, cubeHeight)

        // Apply boid forces
        boid()

        // Obstacle Avoidance
//        val whiskerRange = 1.0
//        val avoidanceAngle = 20.0 // x degrees to the left, right, up, and down
//        val obsAvoidForce = whisObsAvoid(whiskerRange, avoidanceAngle)
//
//        if (obsAvoidForce != null) {
//            pokemonEntity.deltaMovement = pokemonEntity.deltaMovement.add(obsAvoidForce.scale(0.5))
//        }
//        wander()


        // Apply steering force to current movement
//        val steerForce = steerToTargetForce(pokemonEntity.position().add(pokemonEntity.lookAngle), MAX_SPEED, MAX_FORCE)
//        pokemonEntity.deltaMovement = pokemonEntity.deltaMovement.add(steerForce)

        // Take note of speed again to avoid minecraft's friction logic
        speed = pokemonEntity.deltaMovement.length()

        // Limit upwards speed to be half of max speed. The boids kept bumping into the top of the box more so than birds
        // would. Honestly birds probably mostly want to move in the x,z plane anyhow
        pokemonEntity.deltaMovement = Vec3(
            pokemonEntity.deltaMovement.x,
            min(pokemonEntity.deltaMovement.y, MAX_SPEED * 0.5),
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

    /*************************************************************************************************************
     Whisker Obstacle Avoidance
     This function ray casts in a cone in front of the entity
     *************************************************************************************************************/
    private fun whisObsAvoid(range: Double, lookWidth: Double): Vec3? {

        // --- 1. Build the Orientation Matrix ---
        val forward = pokemonEntity.lookAngle.scale(range)
//        val tempUp = if (abs(forward.y) > 0.999) Vec3(0.0, 0.0, 1.0) else Vec3(0.0, 1.0, 0.0)
//        val right = forward.cross(tempUp).normalize()
//        val up = right.cross(forward).normalize()
//
//        val orientation = Matrix3f()
//            .setColumn(0, Vector3f(right.x.toFloat(), right.y.toFloat(), right.z.toFloat()))
//            .setColumn(1, Vector3f(up.x.toFloat(), up.y.toFloat(), up.z.toFloat()))
//            .setColumn(2, Vector3f(forward.x.toFloat(), forward.y.toFloat(), forward.z.toFloat()))

        // --- 2. Define Whiskers in Simple, Local Space ---
        val radLookWidth = lookWidth.toRadians().toFloat()
//        val localWhiskers: List<Vector3f> = listOf(
//            Vector3f(0f, 0f, 1f), // Forward
////            Vector3f(sin(radLookWidth), 0f, cos(radLookWidth)),  // Left
////            Vector3f(-sin(radLookWidth), 0f, cos(radLookWidth)), // Right
////            Vector3f(0f, sin(radLookWidth), cos(radLookWidth)),  // Up
////            Vector3f(0f, -sin(radLookWidth), cos(radLookWidth)) // Down
//        )
//
//        // --- 3. Transform Local Whiskers to World Space using the Matrix ---
//        val whiskerOffsets = localWhiskers.map { localVec ->
//            orientation.transform(localVec, Vector3f()).toVec3d().scale(range) // Transform and store in a new vector
//        }

        // Obtain all points to be raycasted to
        val scaledLookVec = pokemonEntity.deltaMovement.normalize().scale(range)
        val lookPoints: List<Vec3> = listOf(
            forward,
//            forward.yRot(radLookWidth),
//            forward.yRot(-radLookWidth),
//            forward.xRot(90.toRadians()),
//            forward.xRot((-90).toRadians()),
        )

        // iterate through each point and raycast to it starting from the entities pos (may need to be from somewhere else)
        // If a position is hit then add its avoidance force to the totalAvoidance forces.
        var totalAvoidance: Vec3? = null
        val level = pokemonEntity.level()
        val startPos = pokemonEntity.position()
        for (point in lookPoints) {
            val endPos = startPos.add(point)
            val hit = level.clip(
                ClipContext(
                    startPos,
                    endPos,
                    ClipContext.Block.OUTLINE, // If this is being weird then go back to COLLIDER
                    ClipContext.Fluid.ANY, // Birds should probably avoid fluid
                    pokemonEntity
                )
            )

            visualizeRay(level, startPos, endPos, hit.type != HitResult.Type.MISS)

            if (hit.type != HitResult.Type.MISS) {
                // Get the aviodance vector as the vector from the block detection point
                // to the pokemon.
                // Also inversely scale the size of the vector based on the distance the pokemon
                // is to the object. This means close obstacles are avoided more strongly
                val hitDist = hit.location.distanceTo(pokemonEntity.eyePosition)
                val avoidVec = startPos.subtract(endPos).normalize() //.scale(1.0 - (hitDist / range))

                // Add avoid vec to total
                totalAvoidance = totalAvoidance?.add(avoidVec) ?: avoidVec
            }
        }

//        // Scale all forces down to the max foce
//        if (totalAvoidance != null) {
//            // Cap the max total avoidance desire
//            if (totalAvoidance.length() > MAX_SPEED) totalAvoidance = totalAvoidance.normalize().scale(MAX_SPEED)
//            // Convert to Reynolds steering force
//            totalAvoidance = totalAvoidance.subtract(pokemonEntity.deltaMovement)
//            // Cap the max total avoidance force
//            if (totalAvoidance.length() > MAX_FORCE) totalAvoidance = totalAvoidance.normalize().scale(MAX_FORCE)
//        } // Is this next one even cleaner?

        return totalAvoidance?.let { desiredVelocity ->
            // Cap the desired velocity (using lengthSqr is more efficient than length)
            val cappedVelocity = if (desiredVelocity.lengthSqr() > MAX_SPEED * MAX_SPEED) {
                desiredVelocity.normalize().scale(MAX_SPEED)
            } else {
                desiredVelocity
            }

            // Convert to Reynolds steering force
            val steeringForce = cappedVelocity.subtract(pokemonEntity.deltaMovement)

            // Cap the final steering force
            if (steeringForce.lengthSqr() > MAX_FORCE * MAX_FORCE) {
                steeringForce.normalize().scale(MAX_FORCE)
            } else {
                steeringForce
            }
        }
    }

    private fun visualizeRay(level: Level, start: Vec3, end: Vec3, hit: Boolean) {
        // We only want to run this on the server, and only if it's a ServerLevel.
        if (level !is ServerLevel) return

        // Use a different particle for hits and misses for clarity.
        // END_ROD is great because it's bright and doesn't have gravity.
        // FLAME is a good indicator for a collision.
        val particleType: ParticleOptions = if (hit) ParticleTypes.FLAME else ParticleTypes.END_ROD

        val distance = start.distanceTo(end)
        if (distance == 0.0) return

        // How far apart each particle should be
        val stepSize = 0.25
        val direction = end.subtract(start).normalize().scale(stepSize)
        val steps = (distance / stepSize).toInt()

        var currentPos = start
        for (i in 0 until steps) {
            level.sendParticles(particleType, currentPos.x, currentPos.y, currentPos.z, 1, 0.0, 0.0, 0.0, 0.0)
            currentPos = currentPos.add(direction)
        }
    }

    private fun wander() {
        // Wander behaviour
        /*************************************************************************************************************
         * The wander behaviour is based of Reynolds wander behaviour which can be seen in the below link under wander
         * This version however is adapted to 3dimensions.
         * https://slsdo.github.io/steering-behaviors/
         *************************************************************************************************************/
        // Get curr steering positions in left/right and up/down offsets
        val normVec = pokemonEntity.deltaMovement.normalize()
        val currPitch = normVec.y //Not really the entity pitch butttt
        val yWanderOff = sin((this.currVertWander + currPitch).toRadians())
        // Must be thought of as left right be

        val currHeading = Mth.atan2(normVec.z, normVec.x).toDegrees() // Grab the current heading in degrees
        val xWanderOff = cos((this.currHorzWander + currHeading).toRadians())
        val zWanderOff = sin((this.currHorzWander + currHeading).toRadians())

        // Get point of the bird 1 tick in the future and have it be our circles' centerpoint
        val wanderCenter = pokemonEntity.position().add(pokemonEntity.deltaMovement)
        // Get the wander circle's radius
        // NOTE! doing it this way means the wander turn rate is smaller the slower you are moving.
        // Actually that may not be true as then the circle would be closer and the angle then might
        // be the same to the same degree on the circle.
        val wanderRadius = pokemonEntity.deltaMovement.length()
        val wanderOff = Vec3(xWanderOff.toDouble(), -yWanderOff.toDouble(), zWanderOff.toDouble()).scale(wanderRadius)

        val wanderPoint = wanderCenter.add(wanderOff)

        // change wander points
        val randVert = Random.nextDouble(-wanderDisp, wanderDisp)
        val randHorz = Random.nextDouble(-wanderDisp, wanderDisp)
        currVertWander = Mth.wrapDegrees(randVert + currVertWander)
        currHorzWander = Mth.wrapDegrees(randHorz + currHorzWander)

        val wanderForce = steerToTargetForce(wanderPoint, MAX_SPEED, MAX_FORCE)
        pokemonEntity.deltaMovement = pokemonEntity.deltaMovement.add(wanderForce)
    }

    private fun boid() {
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

    }

    private fun separation(boids: List<PokemonEntity>): Vec3? {
        val desiredSeperation = 1.0
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
            //if (seperationVec.length() > MAX_SPEED) seperationVec = seperationVec.normalize().scale(MAX_SPEED)
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

            //alignmentVec =  if (averageVel.length() > MAX_SPEED) averageVel.normalize().scale(MAX_SPEED) else averageVel
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
            //if (cohesionVec.length() > MAX_SPEED) cohesionVec = cohesionVec.normalize().scale(MAX_SPEED)
            cohesionVec = cohesionVec.normalize().scale(MAX_SPEED)
            cohesionVec = cohesionVec.subtract(pokemonEntity.deltaMovement)
            if (cohesionVec.length() > MAX_FORCE) {
                cohesionVec = cohesionVec.normalize().scale(MAX_FORCE)
            }
        }
        return cohesionVec
    }

    private fun forceRectBoundary(basePos: Vec3, rectWidth: Double, rectHeight: Double) {
        val maxBoundaryForce = MAX_FORCE //* 2.0
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
            var desiredPos = pokemonEntity.position().add(pokemonEntity.deltaMovement) // Add delta movement so we aren't slowing this guy down
            if (xExceed != 0.0) desiredPos = Vec3( basePos.x, desiredPos.y, desiredPos.z)
            if (yExceed != 0.0) desiredPos = Vec3( desiredPos.x, basePos.y, desiredPos.z)
            if (zExceed != 0.0) desiredPos = Vec3( desiredPos.x, desiredPos.y, basePos.z)
            return desiredPos.subtract(pokemonEntity.position()).normalize().scale(MAX_SPEED)
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