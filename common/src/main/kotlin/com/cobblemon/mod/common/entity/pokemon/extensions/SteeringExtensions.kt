package com.cobblemon.mod.common.entity.pokemon.extensions

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.math.geometry.toDegrees
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import kotlin.math.sqrt


/*************************************************************************************************************
Velocity steering, detailed in: https://www.red3d.com/cwr/steer/gdc99/
Behaviours give a desired velocity and [steerForce] turns it into a force limited by the max acceleration.
 *************************************************************************************************************/

/** Desired velocity toward [target]. With [arrive] it slows to stop on the target. */
fun seekVelocity(from: Vec3, target: Vec3, maxSpeed: Double, maxAccel: Double, arrive: Boolean): Vec3 {
    val toTarget = target.subtract(from)
    val speed = if (arrive) arriveSpeed(toTarget.length(), maxSpeed, maxAccel) else maxSpeed
    return toTarget.normalize().scale(speed)
}

/**
 * Fastest speed that can still brake to a stop within [distance], from v² = 2ad.
 * Capped at [distance] so the last tick lands on the target instead of stepping past it.
 */
fun arriveSpeed(distance: Double, maxSpeed: Double, maxAccel: Double): Double {
    return minOf(maxSpeed, sqrt(2.0 * maxAccel * distance), distance)
}

fun steerForce(desired: Vec3, current: Vec3, maxForce: Double): Vec3 {
    val steer = desired.subtract(current)
    return if (steer.lengthSqr() > maxForce * maxForce) steer.normalize().scale(maxForce) else steer
}

/*************************************************************************************************************
Face the direction of travel. Visual smoothing happens client side in PokemonBodyRotationControl
 *************************************************************************************************************/
fun PokemonEntity.orientEntityToTravel() {
    // Too slow for the direction to mean anything
    if (deltaMovement.horizontalDistance() < LivingEntity.MIN_MOVEMENT_DISTANCE) return
    yRot = Mth.atan2(deltaMovement.z, deltaMovement.x).toDegrees() - 90.0f
}