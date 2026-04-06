package com.cobblemon.mod.common.entity.pokemon.extensions

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.math.geometry.toDegrees
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.control.MoveControl
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.min
import kotlin.math.sign


/*************************************************************************************************************
Steering to Target
Detailed in: https://www.red3d.com/cwr/steer/gdc99/
 *************************************************************************************************************/
fun PokemonEntity.steerToTargetForce(
    targetPos: Vec3,
    maxSpeed: Double,
    maxForce: Double,
    arrivalRange: Double? = null,
    horzOnly: Boolean = false
): Vec3 {
    val cPos = if (horzOnly) Vec3(this.position().x, 0.0, this.position().z) else this.position()
    val tPos = if (horzOnly) Vec3(targetPos.x, 0.0, targetPos.z) else targetPos

    // Ensure we can't have a negative range here
    val absArrivalRange = arrivalRange?.let { abs(arrivalRange) }

    // Calculate the vector from the pokemons current position to the
    // target position. Set its desired magnitude to max speed or a
    // portion of it if within the arrivalRange
    val toTargetVec = tPos.subtract(cPos)

    val distToTarget = toTargetVec.length()
    val desiredSpeed = if (absArrivalRange != null && distToTarget < absArrivalRange) (distToTarget/absArrivalRange) * maxSpeed else maxSpeed
    val desiredVec = toTargetVec.normalize().scale(desiredSpeed)

    var steer = if (horzOnly) desiredVec.subtract(Vec3(this.deltaMovement.x, 0.0, this.deltaMovement.z))
        else desiredVec.subtract(this.deltaMovement)

    if (steer.length() > maxForce) {
        steer = steer.normalize().scale(maxForce)
    }

    return steer
}



/*************************************************************************************************************
Steering Target Avoidance
Detailed in: https://www.red3d.com/cwr/steer/gdc99/
 *************************************************************************************************************/
fun PokemonEntity.avoidTarget(
    targetPos: Vec3,
    maxSpeed: Double,
    maxForce: Double,
    avoidanceRange: Double? = null
): Vec3 {

    // Calculate the vector from the targets current position to the
    // pokemon position. Set its desired magnitude to max speed scaled
    // linearly from 1.0 when right next to the target and 0.0 when on the
    // very edge of the avoidance Range
    var fromTargetVec = this.position().subtract(targetPos)

    // Calculate distance to the target
    val distToTarget = fromTargetVec.length()
    if (distToTarget <  1.0E-4) {fromTargetVec = Vec3(0.0, 1.0, 0.0)} // Ensure the avoidance vector can't be ZERO

    // Return no force otherwise a desired vector will be calculated that slows the velocity.
    if (avoidanceRange != null && distToTarget >= avoidanceRange) return Vec3.ZERO

    // Scale the avoidance force linearly
    val desiredSpeed = when {
        avoidanceRange == null -> maxSpeed // If no avoidance range then just always flee as fast as you can
        avoidanceRange == 0.0 -> maxSpeed // Avoid weird divide by zero nonsense
        distToTarget < avoidanceRange -> (1.0 - (distToTarget / avoidanceRange)) * maxSpeed
        else -> 0.0
    }
    //TODO: determine if this should be scaled by distance. Probably not
    val desiredVec = fromTargetVec.normalize() //.scale(desiredSpeed)

    // Steer the pokemon towards the desired vector
    var steer = desiredVec.subtract(this.deltaMovement)
    if (steer.length() > maxForce) {
        steer = steer.normalize().scale(maxForce)
    }

    return steer
}

/*************************************************************************************************************
Lerp the mobs yRot to its direction of travel
 *************************************************************************************************************/
fun PokemonEntity.orientEntityToTravel(k: Float = 1.0f) {
    val interpAmount = k.coerceIn(0.0f, 1.0f) //TODO: This probably shouldn't be a lerp like this.
    if (this.deltaMovement.length() < LivingEntity.MIN_MOVEMENT_DISTANCE) return

    // Ensure the entity is facing the direction it is moving in both pitch and yaw
    val normVec = this.deltaMovement.normalize()
    val targetAngle = Mth.atan2(normVec.z, normVec.x).toDegrees() - 90.0f
    val currAngle = this.yRot

    val angleDiff = Mth.wrapDegrees(targetAngle - currAngle)
    val maxPerTickRotation = 180.0f  // set really high to ignore for now 15.0f // TODO: is something like this needed or should it be a clientside spring or something?
    val effRot = min(abs(angleDiff), maxPerTickRotation) * sign(angleDiff)
    this.yRot = currAngle + (effRot * interpAmount)
    //entity.xRot = asin(normVec.y.toFloat()).toDegrees()
}