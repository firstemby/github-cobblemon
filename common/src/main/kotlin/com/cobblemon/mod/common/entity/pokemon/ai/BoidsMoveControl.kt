/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.entity.pokemon.ai


import com.cobblemon.mod.common.CobblemonActivities
import com.cobblemon.mod.common.CobblemonNetwork.sendPacketToTrackingPlayers
import com.cobblemon.mod.common.client.render.debug.EntityDebugLine
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.entity.pokemon.extensions.*
import com.cobblemon.mod.common.net.messages.client.debug.ClientboundUpdateObsAvoidPacket
import com.cobblemon.mod.common.util.math.geometry.toRadians
import com.cobblemon.mod.common.util.resolveDouble
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.control.MoveControl
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.*
import kotlin.random.Random

class BoidsMoveControl(val pokemonEntity: PokemonEntity, val target: LivingEntity) : MoveControl(pokemonEntity) {
    companion object {
        const val VERY_CLOSE = 2.500000277905201E-3
        const val MAX_SPEED = 0.3 / 2
        const val MAX_FORCE = 0.02
        const val PREDATOR_MAX_SPEED = 0.6
//        const val MAX_SPEED = 0.2
//        const val MAX_FORCE = 0.013
    }

    // TODO: might need to hold its own velocity so minecraft doesn't do its nonsense to it?
    // Can handle collisions ourselves as well
    var speed: Double? = null
    var drawLines: MutableList<EntityDebugLine> = mutableListOf()

    // wander vars
    var currHorzWander = 0.0
    var currVertWander = 0.0
    val wanderDisp = 15.0 // The degrees of change from tick to tick in wander angle

    override fun tick() {
        // Clear since the info is sent and is now stale
        drawLines.clear()

        // Steering constants
        val orbitRadius = 16.0
        val targetPos = target.position().add(0.0, 4.0, 0.0)
        val arrivalRange = 2.0 // in blocks

        val species = pokemonEntity.pokemon.species.name
        // Set this to ensure the flying pose is active

        // ignore all the friction stuff from outside
        speed?.let { pokemonEntity.setDeltaMovement(pokemonEntity.deltaMovement.normalize().scale(it)) }

        // Constrain to rectangular boundary
        val cubeWidth = 16.0
        val cubeHeight = 16.0
        val cubeCenter = target.position().add(0.0, cubeHeight / 2.0, 0.0)
        //forceRectBoundary(cubeCenter, cubeWidth, cubeHeight)

        // Apply boid forces

        // Obstacle Avoidance
        val whiskerRange = 1.0
        val avoidanceAngle = 20.0 // x degrees to the left, right, up, and down
        //val obsAvoidForce = whisObsAvoid(whiskerRange, avoidanceAngle)

//        if (obsAvoidForce != null) {
//            pokemonEntity.deltaMovement = pokemonEntity.deltaMovement.add(obsAvoidForce.scale(0.5))
//        }

        // TODO: obviously don't hardcode each pokemon. Remove this after testing is finished.
        if (pokemonEntity.pokemon.species.name.lowercase() == "charizard") {
            pokemonEntity.setFlying(true)
            pokemonEntity.resetFallDistance()
            seekPrey(pokemonEntity)
        } else if (pokemonEntity.pokemon.species.name.lowercase() == "garchomp") {
            pokemonEntity.setFlying(false)
            val walkSpeed = pokemonEntity.runtime.resolveDouble(pokemonEntity.behaviour.moving.walk.walkSpeed) * 1.5
            val steerForce = pokemonEntity.steerToTarget(target.position(), walkSpeed, walkSpeed / 12)
            pokemonEntity.deltaMovement = pokemonEntity.deltaMovement.add(steerForce.x, 0.0, steerForce.z)
//            speed?.let { this.mob.setZza(speed!!.toFloat()) }
        } else if (pokemonEntity.pokemon.species.name.lowercase() == "skiddo") {
            pokemonEntity.setFlying(false)
            //wander(pokemonEntity,1.0)
//            val steerForce = pokemonEntity.steerToTargetForce(target.position(), MAX_SPEED, MAX_FORCE)
//            pokemonEntity.deltaMovement = pokemonEntity.deltaMovement.add(steerForce.x, 0.0, steerForce.z)
            aviodPredators(pokemonEntity, includePlayer = true)
            boid(isFlyingOrSwimming = false)
        } else {
            pokemonEntity.setFlying(true)
            pokemonEntity.resetFallDistance()
            wander(pokemonEntity,0.8)
            boid()
            aviodPredators(pokemonEntity, includePlayer = false)

            // Force towards ground


            forceRectBoundary(pokemonEntity, cubeCenter, cubeWidth, cubeHeight)
        }

//        val avoidForce = avoidTarget(target.position(), MAX_SPEED, MAX_FORCE, 10.0)
//        pokemonEntity.deltaMovement = pokemonEntity.deltaMovement.add(avoidForce.scale(1.0))

        // Apply steering force to current movement
//        val dm = pokemonEntity.deltaMovement
//        val w_pos = pokemonEntity.position().add(Vec3(dm.x, 0.0, dm.z))
//        val steerForce = steerToTargetForce(w_pos, MAX_SPEED, MAX_FORCE)
//        pokemonEntity.deltaMovement = pokemonEntity.deltaMovement.add(steerForce)

        // Take note of speed again to avoid minecraft's friction logic
        speed = pokemonEntity.deltaMovement.length() * 0.98

        // Test out what this setting of speed does.
        //speed?.let { this.mob.speed = speed!!.toFloat() }

        // Limit upwards speed to be half of max speed. The boids kept bumping into the top of the box more so than birds
        // would. Honestly birds probably mostly want to move in the x,z plane anyhow
//        pokemonEntity.deltaMovement = Vec3(
//            pokemonEntity.deltaMovement.x,
//            min(pokemonEntity.deltaMovement.y, MAX_SPEED * 0.5),
//            pokemonEntity.deltaMovement.y
//        ).normalize().scale(speed!!)

        pokemonEntity.orientEntityToTravel(0.4f)

        drawLines.add(EntityDebugLine(pokemonEntity.position(), pokemonEntity.position().add(0.0, 2.0, 0.0)))
        if (false && drawLines.isNotEmpty()) { //TODO: Re-enable
            sendPacketToTrackingPlayers(pokemonEntity, ClientboundUpdateObsAvoidPacket(pokemonEntity.id, drawLines))
        }
    }


    private fun aviodPredators(entity: PokemonEntity, includePlayer: Boolean = false) {
        val fearRadius = 5.0
        val brain = entity.brain
        val predators = brain.getMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES).map { nearbyEntities ->
            nearbyEntities.filterIsInstance<PokemonEntity>().filter { pokemon ->
                pokemon.brain.activeNonCoreActivity.orElse(null) == CobblemonActivities.POKEMON_BOIDS &&
                        pokemon.pokemon.species.name.lowercase() == "charizard" &&
                        entity.position().subtract(pokemon.position()).length() <= fearRadius
            }
        }.orElse(emptyList())

        var totalEvasionVec = Vec3.ZERO
        var predatorCount = predators.size

        // If the player should be included in avoidance then add the player as a predator.
        if (includePlayer) {
            totalEvasionVec = totalEvasionVec.add(entity.avoidTarget(target.position(), MAX_SPEED * 1.2, MAX_FORCE * 2, fearRadius))
            predatorCount += 1
        }
        // Average evasion forces for all predators in the area and apply it to the prey entity
        if (predatorCount > 0) {
            for (predator in predators) {
                totalEvasionVec = totalEvasionVec.add(entity.avoidTarget(predator.position(), MAX_SPEED * 1.2, MAX_FORCE * 2, fearRadius))
            }
            totalEvasionVec = totalEvasionVec.scale(1.0 / predatorCount)
            if (entity.isFlying() || entity.isSwimming) {
                entity.deltaMovement = entity.deltaMovement.add(totalEvasionVec)
            } else {
                entity.deltaMovement = entity.deltaMovement.add(Vec3(totalEvasionVec.x, 0.0, totalEvasionVec.z))
            }
        }
    }

    /*************************************************************************************************************
     Seek the closest prey and pursue
     *************************************************************************************************************/
    private fun seekPrey(predator: PokemonEntity) {
        val SeekRadius = 20.0
        val brain = predator.brain
        // Do calcs for head height as it looks better this way than a charizard flying crotch first at a group of pidgeys
        val kindaHeadHeight = predator.position()

        val prey = brain.getMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES).map { nearbyEntities ->
            nearbyEntities.filterIsInstance<PokemonEntity>().filter { pokemon ->
                pokemon.brain.activeNonCoreActivity.orElse(null) == CobblemonActivities.POKEMON_BOIDS &&
                        (pokemon.pokemon.species.name.lowercase() == "pidgey" || pokemon.pokemon.species.name.lowercase() == "skiddo") &&
                        kindaHeadHeight.subtract(pokemon.position()).length() <= SeekRadius
            }
        }.orElse(emptyList())

        // Find the closest prey and seek towards it
        val nearestPrey = prey.minByOrNull { pokePrey ->
            kindaHeadHeight.subtract(pokePrey.position()).length()
        }
        nearestPrey?.let { prey ->

            // Do the actual attack
            // TODO: make this not dumb
            if(kindaHeadHeight.subtract(nearestPrey.position()).length() < 0.5 + predator.bbWidth + nearestPrey.bbWidth) {
                //predator.doHurtTarget(nearestPrey)
            }

            // Steer towards the target
            val steerForce = predator.steerToTarget(prey.position(), PREDATOR_MAX_SPEED, MAX_FORCE)
            predator.deltaMovement = predator.deltaMovement.add(steerForce)
        }

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

            // Add the whisker to the debug lines to be drawn
            drawLines.add(EntityDebugLine(startPos, endPos, 1.0f, 0.0f, 0.0f))
            //visualizeRay(level, startPos, endPos, hit.type != HitResult.Type.MISS)

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

    private fun wander(entity: PokemonEntity, forceScale: Double = 1.0) {
        // Wander behaviour
        /*************************************************************************************************************
         * The wander behaviour is based of Reynolds wander behaviour which can be seen in the below link under wander
         * This version however is adapted to 3dimensions.
         * https://slsdo.github.io/steering-behaviors/
         *************************************************************************************************************/
        // Get curr steering positions in left/right and up/down offsets
        val normVec = if(entity.deltaMovement.lengthSqr() > 0.0001) {
            entity.lookAngle.normalize()
        } else {
            Vec3(
                Random.nextDouble(-1.0, 1.0),
                Random.nextDouble(-1.0, 1.0),
                Random.nextDouble(-1.0, 1.0)
            ).normalize()
        }

        var wanderOff = normVec

        val currPitchS = sin(Mth.atan2(wanderOff.y, wanderOff.horizontalDistance()))
        if (currPitchS >= 0.5f || currPitchS <= -0.5f) { //TODO: make this y limit class wide
            this.currVertWander = 0.0
        }
        wanderOff = wanderOff.xRot(this.currVertWander.toRadians())
        wanderOff = wanderOff.yRot(this.currHorzWander.toRadians())

        // Base the center off the current direction of travel
        val centerDistance = 4.0 // How many blocks the wander 'sphere' is in front of the entity
        val wanderCenter = entity.position().add(normVec.scale(centerDistance))
        // Get the wander circle's radius
        val wanderRadius = 4.0

        val wanderPoint = wanderCenter.add(wanderOff.scale(wanderRadius))

        // change wander points
        val randVert = Random.nextDouble(-wanderDisp, wanderDisp)
        val randHorz = Random.nextDouble(-wanderDisp, wanderDisp)
        currVertWander = Mth.wrapDegrees(randVert + currVertWander)
        currHorzWander = Mth.wrapDegrees(randHorz + currHorzWander)

        drawLines.add(EntityDebugLine(pokemonEntity.position(), wanderPoint, 1.0f, 0.0f, 0.0f, 1.0f)) // Add WanderPoint
        drawLines.add(EntityDebugLine(pokemonEntity.position(), wanderCenter, 0.0f, 1.0f, 0.0f, 1.0f)) // Add wander center
        val wanderForce = entity.steerToTarget(wanderPoint, MAX_SPEED, MAX_FORCE)
        if(pokemonEntity.isFlying() || pokemonEntity.isSwimming) {
            entity.deltaMovement = pokemonEntity.deltaMovement.add(wanderForce.scale(forceScale))
        } else {
            entity.deltaMovement = pokemonEntity.deltaMovement.add(Vec3(wanderForce.x, 0.0, wanderForce.z).scale(forceScale))
        }

    }

    private fun boid(isFlyingOrSwimming: Boolean = true) {
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
            if( isFlyingOrSwimming) {
                pokemonEntity.deltaMovement = pokemonEntity.deltaMovement.add(force)
            } else {
                pokemonEntity.deltaMovement = pokemonEntity.deltaMovement.add(Vec3(force.x, 0.0, force.z))
            }
        }
        // alignment
        val alignmentForce = alignment(boids)?.scale(0.0)
        alignmentForce?.let { force ->
            if( isFlyingOrSwimming) {
                pokemonEntity.deltaMovement = pokemonEntity.deltaMovement.add(force)
            } else {
                pokemonEntity.deltaMovement = pokemonEntity.deltaMovement.add(Vec3(force.x, 0.0, force.z))
            }
        }

        // cohesion
        val cohesionForce = cohesion(boids)?.scale(0.6)
        cohesionForce?.let { force ->
            if( isFlyingOrSwimming) {
                pokemonEntity.deltaMovement = pokemonEntity.deltaMovement.add(force)
            } else {
                pokemonEntity.deltaMovement = pokemonEntity.deltaMovement.add(Vec3(force.x, 0.0, force.z))
            }
        }
    }

    private fun separation(boids: List<PokemonEntity>): Vec3? {
        var seperationVec: Vec3? = null

        // Gather the total separation vec of all close entities
        boids.forEach { boid ->
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
        val neighborDist = 4.0
        var summedVel: Vec3? = null
        var alignmentVec: Vec3? = null
        var boidCount = 0

        // Gather the average position of the boid
        boids.forEach { boid ->
            val vecToOther = boid.position().subtract(this.pokemonEntity.position())
            val distToOther = vecToOther.length()
            val widthOfViewInDeg = 180
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
        val cohesionRadius = 8.0

        // Gather the average position of the boid
        boids.forEach { boid ->
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

    private fun forceRectBoundary(entity: PokemonEntity, basePos: Vec3, rectWidth: Double, rectHeight: Double) {
        val maxBoundaryForce = MAX_FORCE //* 2.0
        val correctiveForce = getRectCorrectiveForce(basePos, rectWidth, rectHeight)
        if (correctiveForce != null) {
            //val correctiveForce = steerToTargetForce(correctivePos, speedLimit, maxForce)
            var steer = correctiveForce.subtract(entity.deltaMovement)
            if (steer.length() > maxBoundaryForce) {
                steer = steer.normalize().scale(maxBoundaryForce)
            }
            entity.setDeltaMovement(entity.deltaMovement.add(steer))
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
}

//val desiredScale = if (distToTarget > slowDownRadius) speedLimit else (distToTarget/slowDownRadius).pow(2) * speedLimit
//desiredVec = desiredVec.normalize().scale(desiredScale)