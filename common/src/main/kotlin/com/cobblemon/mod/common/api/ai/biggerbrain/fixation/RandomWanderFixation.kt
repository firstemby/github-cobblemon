/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain.fixation

import com.cobblemon.mod.common.api.ai.biggerbrain.BiggerBrain
import com.cobblemon.mod.common.api.ai.biggerbrain.LookingTracker
import com.cobblemon.mod.common.api.ai.biggerbrain.MovementStatus
import com.cobblemon.mod.common.api.ai.biggerbrain.MovementTracker
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.Distraction
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.DistractionType
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.WanderAround
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.entity.pokemon.ai.PokemonMoveControl
import com.cobblemon.mod.common.entity.pokemon.extensions.orientEntityToTravel
import com.cobblemon.mod.common.entity.pokemon.extensions.steerToTarget
import com.cobblemon.mod.common.util.math.geometry.toRadians
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import kotlin.math.abs

/**
 * Fixation for random wander (Essentially Reynolds wander for steered entities
 *
 * @author Jackson
 * @since April 13th, 2026
 */
class RandomWanderFixation(
    val objective: WanderAround,
    val pokemonEntity: PokemonEntity,
    val moveControl: PokemonMoveControl,
    val center: Vec3 = pokemonEntity.position(),
    val boundarySize: Double = 25.0,
    val durationTicks: Int = 400
) : EntityFixation, MovementTrackerFixation {
    override val movementTracker: MovementTracker? = null
    var ticksRemaining = durationTicks

    // Wander state — pulled from BoidsMoveControl.wander()
    var currHorzWander = 0.0
    var currVertWander = 0.0
    val wanderDisp = 5.0

    override fun getDescription(entity: LivingEntity) = "Reynolds wandering ($ticksRemaining ticks left)"

    override fun canDistract(distraction: Distraction, entity: LivingEntity) =
        distraction.isForce || distraction.isComfort || distraction.isDamage

    override fun onDistract(distraction: Distraction, entity: LivingEntity) {}

    override fun tick(entity: LivingEntity, brain: BiggerBrain): Boolean {
        if (entity !is PokemonEntity) return false
        if (ticksRemaining-- <= 0) return false

        val maxSpeed = moveControl.getTopSpeed(pokemonEntity) * 50

        // TODO: deadlock detection — track position delta over last ~40 ticks, if average movement < threshold mark objective.deadlock = true and return false

        applyWanderForce(maxSpeed)
        //applyBoundaryForce(maxSpeed)

//        moveControl.mob.speed = 0f // This is done in PokemonMoveControl however it needs to be done
//        moveControl.mob.setZza(0f)
//        moveControl.mob.setXxa(0f)
//        pokemonEntity.setDiscardFriction(true)
//        pokemonEntity.orientEntityToTravel(1.0f)

        return true
    }

    private fun applyWanderForce(maxSpeed: Double) {
        val maxForce = 0.03

        val normVec = if (pokemonEntity.deltaMovement.lengthSqr() > 0.0001) {
            pokemonEntity.lookAngle.normalize()
        } else {
//            Vec3(
//                kotlin.random.Random.nextDouble(-1.0, 1.0),
//                kotlin.random.Random.nextDouble(-1.0, 1.0),
//                kotlin.random.Random.nextDouble(-1.0, 1.0)
//            ).normalize()
            pokemonEntity.lookAngle.normalize()
        }

        var wanderOff = normVec
        val currPitchS = Mth.atan2(wanderOff.y, wanderOff.horizontalDistance())
        if (currPitchS >= 0.85f || currPitchS <= -0.85f) {
            currVertWander = 0.0
        }
        wanderOff = wanderOff.xRot(currVertWander.toFloat().toRadians())
        wanderOff = wanderOff.yRot(currHorzWander.toFloat().toRadians())

        val centerDistance = 4.0
        val wanderCenter = pokemonEntity.position().add(normVec.scale(centerDistance))
        val wanderRadius = 2.0
        val wanderPoint = wanderCenter.add(wanderOff.scale(wanderRadius))

        currVertWander = Mth.wrapDegrees(kotlin.random.Random.nextDouble(-wanderDisp, wanderDisp) + currVertWander)
        currHorzWander = Mth.wrapDegrees(kotlin.random.Random.nextDouble(-wanderDisp, wanderDisp) + currHorzWander)

        val wanderForce = pokemonEntity.steerToTarget(wanderPoint, maxSpeed, maxForce)
        pokemonEntity.deltaMovement = pokemonEntity.deltaMovement.add(wanderForce)
    }

    private fun applyBoundaryForce(maxSpeed: Double) {
        val maxBoundaryForce = 0.02
        val halfSize = boundarySize / 2.0
        val entityPos = pokemonEntity.position()
        val posFromCenter = entityPos.subtract(center)

        val xExceed = maxOf(0.0, abs(posFromCenter.x) - halfSize)
        val yExceed = maxOf(0.0, abs(posFromCenter.y) - halfSize)
        val zExceed = maxOf(0.0, abs(posFromCenter.z) - halfSize)

        if (xExceed == 0.0 && yExceed == 0.0 && zExceed == 0.0) return

        var desiredPos = entityPos.add(pokemonEntity.deltaMovement)
        if (xExceed != 0.0) desiredPos = Vec3(center.x, desiredPos.y, desiredPos.z)
        if (yExceed != 0.0) desiredPos = Vec3(desiredPos.x, center.y, desiredPos.z)
        if (zExceed != 0.0) desiredPos = Vec3(desiredPos.x, desiredPos.y, center.z)

        val correctiveForce = desiredPos.subtract(entityPos).normalize().scale(maxSpeed)
        var steer = correctiveForce.subtract(pokemonEntity.deltaMovement)
        if (steer.length() > maxBoundaryForce) steer = steer.normalize().scale(maxBoundaryForce)
        pokemonEntity.deltaMovement = pokemonEntity.deltaMovement.add(steer)
    }
}
