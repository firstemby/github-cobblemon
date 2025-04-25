/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.riding.behaviour.types

import com.bedrockk.molang.Expression
import com.bedrockk.molang.runtime.MoLangMath.lerp
import com.cobblemon.mod.common.api.riding.RidingStyle
import com.cobblemon.mod.common.api.riding.behaviour.*
import com.cobblemon.mod.common.api.riding.posing.PoseOption
import com.cobblemon.mod.common.api.riding.posing.PoseProvider
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.*
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.util.Mth
import net.minecraft.util.SmoothDouble
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.Shapes
import kotlin.math.*

class GenericLandBehaviour : RidingBehaviour<GenericLandSettings, GenericLandState> {
    companion object {
        val KEY = cobblemonResource("land/generic")
    }

    override val key = KEY

    override fun getRidingStyle(settings: GenericLandSettings, state: GenericLandState): RidingStyle {
        return RidingStyle.LAND
    }

    val poseProvider = PoseProvider<GenericLandSettings, GenericLandState>(PoseType.STAND)
        .with(PoseOption(PoseType.WALK) { _, state, _ ->
            return@PoseOption abs(state.rideVelocity.get().z) > 0.2
        })

    override fun isActive(
        settings: GenericLandSettings,
        state: GenericLandState,
        vehicle: PokemonEntity
    ): Boolean {
        return Shapes.create(vehicle.boundingBox).blockPositionsAsListRounded().any {
            //Need to check other fluids
            if (vehicle.isInWater || vehicle.isUnderWater) {
                return@any false
            }
            //This might not actually work, depending on what the yPos actually is. yPos of the middle of the entity? the feet?
            if (it.y.toDouble() == (vehicle.position().y)) {
                val blockState = vehicle.level().getBlockState(it.below())
                return@any !blockState.isAir && blockState.fluidState.isEmpty
            }
            true
        }
    }

    override fun pose(
        settings: GenericLandSettings,
        state: GenericLandState,
        vehicle: PokemonEntity
    ): PoseType {
        return this.poseProvider.select(settings, state, vehicle)
    }

    override fun speed(
        settings: GenericLandSettings,
        state: GenericLandState,
        vehicle: PokemonEntity,
        driver: Player
    ): Float {
        return state.rideVelocity.get().length().toFloat()
    }

    override fun updatePassengerRotation(
        settings: GenericLandSettings,
        state: GenericLandState,
        vehicle: PokemonEntity,
        driver: LivingEntity
    ) {

        //Take the inverse so that it cancels out how
        //much the entity rotates.
        val turnAmount = calcRotAmount(settings, state, vehicle, driver)

        val topSpeed = vehicle.runtime.resolveDouble(settings.speedExpr)
        val maxYawDiff = 90.0f

        //Normalize the current rotation diff
        val rotMod = Mth.wrapDegrees(driver.yRot - vehicle.yRot) / maxYawDiff

        val rotAmount = 10.0f

        //Take the inverse so that you turn more at higher speeds
        val normSpeed = 1.0f - 0.5f*normalizeVal(state.rideVelocity.get().length(), 0.0, topSpeed).toFloat()

        //driver.yRot += (entity.riding.deltaRotation.y - turnAmount)
        //driver.setYHeadRot(driver.yHeadRot + (entity.riding.deltaRotation.y) - turnAmount)
    }

    override fun clampPassengerRotation(
        settings: GenericLandSettings,
        state: GenericLandState,
        vehicle: PokemonEntity,
        driver: LivingEntity
    ) {
        val f = Mth.wrapDegrees(driver.yRot - vehicle.yRot)
        val lookYawLimit = 90.0f
        val g = Mth.clamp(f, -lookYawLimit, lookYawLimit)
        driver.yRotO += g - f
        driver.yRot = driver.yRot + g - f
        driver.setYHeadRot(driver.yRot)
    }


    override fun rotation(
        settings: GenericLandSettings,
        state: GenericLandState,
        vehicle: PokemonEntity,
        driver: LivingEntity
    ): Vec2 {
        val turnAmount =  calcRotAmount(settings, state, vehicle, driver)

        return Vec2(vehicle.xRot, vehicle.yRot + turnAmount )

    }

    /*
   *  Calculates the rotation amount given the difference between
   *  the current player y rot and entity y rot. This gives the
   *  affect of influencing a rides rotation through the mouse
   *  without it being instant.
   */
    fun calcRotAmount(
        settings: GenericLandSettings,
        state: GenericLandState,
        vehicle: PokemonEntity,
        driver: LivingEntity
    ): Float {
        val topSpeed = vehicle.runtime.resolveDouble(settings.speedExpr)
        //In degrees per second
        val handling = vehicle.runtime.resolveDouble(settings.handlingExpr)
        val maxYawDiff = 90.0f

        //Normalize the current rotation diff
        val rotDiff = Mth.wrapDegrees(driver.yRot - vehicle.yRot)
        val rotDiffNorm = rotDiff / maxYawDiff

        //Take the square root so that the ride levels out quicker when at lower differences between entity
        //y and driver y
        //This influences the speed of the turn based on how far in one direction you're looking
        val rotDiffMod = (sqrt(abs(rotDiffNorm)) * rotDiffNorm.sign)

        //Take the inverse so that you turn less at higher speeds
        val normSpeed = 1.0f // = 1.0f - 0.5f*normalizeVal(state.rideVelocityocity.length(), 0.0, topSpeed).toFloat()

        // TODO: This needs to be tweaked with in game results and testing
        val turnRate = (handling.toFloat() / 20.0f) * 2

        //Ensure you only ever rotate as much difference as there is between the angles.
        val turnSpeed = turnRate  * rotDiffMod * normSpeed
        val rotAmount = turnSpeed.coerceIn(-abs(rotDiff), abs(rotDiff))

        return rotAmount
    }


    override fun velocity(
        settings: GenericLandSettings,
        state: GenericLandState,
        vehicle: PokemonEntity,
        driver: Player,
        input: Vec3
    ): Vec3 {
        //Limit horizontal velocity that may have accumulated through alternate behaviour
        val topSpeed = vehicle.runtime.resolveDouble(settings.speedExpr)
        val currVel = state.rideVelocity.get()
//        state.rideVelocity.set(Vec3(
//            max(currVel.x, topSpeed),
//            currVel.y,
//            max(currVel.z, topSpeed)
//        ))

        state.rideVelocity.set(calculateRideSpaceVel(settings, state, vehicle, driver))

        //This is cheap.
        //Also make it stop quicker the slower it is.
        val maxSpeed = 1.0
        //state.currVel = state.currVel.lerp( Vec3(0.0, state.currVel.y, 0.0), 1.0/20.0 )


        //entity.deltaMovement = entity.deltaMovement.lerp(velocity, 1.0)
        //state.currVel = state.currVel.add( f.toDouble() / 20.0, gravity / 20.0 , g.toDouble() / 20.0)
        return state.rideVelocity.get()
    }

    private fun calculateRideSpaceVel(
        settings: GenericLandSettings,
        state: GenericLandState,
        vehicle: PokemonEntity,
        driver: Player
    ): Vec3 {
        val topSpeed = vehicle.runtime.resolveDouble(settings.speedExpr)
        val accel = vehicle.runtime.resolveDouble(settings.accelerationExpr)
        val speed = state.rideVelocity.get().length()

        //Flag for determining if player is actively inputting
        var activeInput = false

        var newVelocity = Vec3(state.rideVelocity.get().x, state.rideVelocity.get().y, state.rideVelocity.get().z)


        //speed up and slow down based on input
        if (driver.zza != 0.0f && state.stamina.get() > 0.0) {
            //make sure it can't exceed top speed
            val forwardInput = when {
                driver.zza > 0 && newVelocity.z > topSpeed -> 0.0
                driver.zza < 0 && newVelocity.z < (-topSpeed / 3.0) -> 0.0
                else -> driver.zza.sign
            }

            newVelocity = Vec3(
                newVelocity.x,
                newVelocity.y,
                (newVelocity.z + (accel * forwardInput.toDouble())))

            activeInput = true
        }

        //Gravity logic
        if (vehicle.onGround()) {
            newVelocity = Vec3(newVelocity.x, 0.0, newVelocity.z)
        } else {
            val gravity = (9.8 / ( 20.0)) * 0.2
            val terminalVel = 2.0

            val fallingForce = gravity -  ( newVelocity.z.sign *gravity *(abs(newVelocity.z) / 2.0))
            newVelocity = Vec3(newVelocity.x, max(newVelocity.y - fallingForce, -terminalVel), newVelocity.z)
        }

        //ground Friction
        if( abs(newVelocity.z) > 0 && vehicle.onGround() && !activeInput) {
            newVelocity = newVelocity.subtract(0.0, 0.0, min(0.03 * newVelocity.z.sign, newVelocity.z))
        }

        //Jump the thang!
        if (driver.jumping && vehicle.onGround()) {
            val jumpVel = 1.0
            newVelocity = newVelocity.add(0.0, 1.0, 0.0)

            //Ensure this doesn't add unwanted forward velocity
            val mag = if(newVelocity.length() < topSpeed) newVelocity.length() else topSpeed
            newVelocity = newVelocity.normalize().scale(mag)
        }

        return newVelocity
    }

    /*
    *  Normalizes the current speed between minSpeed and maxSpeed.
    *  The result is clamped between 0.0 and 1.0, where 0.0 represents minSpeed and 1.0 represents maxSpeed.
    */
    private fun normalizeVal(currSpeed: Double, minSpeed: Double, maxSpeed: Double): Double {
        require(maxSpeed > minSpeed) { "maxSpeed must be greater than minSpeed" }
        return ((currSpeed - minSpeed) / (maxSpeed - minSpeed)).coerceIn(0.0, 1.0)
    }

    override fun angRollVel(
        settings: GenericLandSettings,
        state: GenericLandState,
        vehicle: PokemonEntity,
        driver: Player,
        deltaTime: Double
    ): Vec3 {
        return Vec3.ZERO
    }

    override fun rotationOnMouseXY(
        settings: GenericLandSettings,
        state: GenericLandState,
        vehicle: PokemonEntity,
        driver: Player,
        mouseY: Double,
        mouseX: Double,
        mouseYSmoother: SmoothDouble,
        mouseXSmoother: SmoothDouble,
        sensitivity: Double,
        deltaTime: Double
    ): Vec3 {
        //Smooth out mouse input.
        val smoothingSpeed = 4
        val xInput = mouseXSmoother.getNewDeltaValue(mouseX * 0.1, deltaTime * smoothingSpeed);
        val yInput = mouseYSmoother.getNewDeltaValue(mouseY * 0.1, deltaTime * smoothingSpeed);

        //yaw, pitch, roll
        return Vec3(0.0, yInput, xInput)
    }

    override fun canJump(
        settings: GenericLandSettings,
        state: GenericLandState,
        vehicle: PokemonEntity,
        driver: Player
    ): Boolean {
        return false
    }

    override fun setRideBar(
        settings: GenericLandSettings,
        state: GenericLandState,
        vehicle: PokemonEntity,
        driver: Player
    ): Float {
        //Retrieve stamina from state and tick up at a rate of 0.1 a second
        return (state.stamina.get() / 1.0f)
    }

    override fun jumpForce(
        settings: GenericLandSettings,
        state: GenericLandState,
        vehicle: PokemonEntity,
        driver: Player,
        jumpStrength: Int
    ): Vec3 {
        return Vec3.ZERO
    }

    override fun gravity(
        settings: GenericLandSettings,
        state: GenericLandState,
        vehicle: PokemonEntity,
        regularGravity: Double
    ): Double {
        return 0.0
    }

    override fun rideFovMultiplier(
        settings: GenericLandSettings,
        state: GenericLandState,
        vehicle: PokemonEntity,
        driver: Player
    ): Float {
        return 1.0f
    }

    override fun useAngVelSmoothing(
        settings: GenericLandSettings,
        state: GenericLandState,
        vehicle: PokemonEntity
    ): Boolean {
        return false
    }

    override fun useRidingAltPose(
        settings: GenericLandSettings,
        state: GenericLandState,
        vehicle: PokemonEntity,
        driver: Player
    ): Boolean {
        return false
    }

    override fun inertia(
        settings: GenericLandSettings,
        state: GenericLandState,
        vehicle: PokemonEntity
    ): Double {
        return 0.5
    }

    override fun shouldRoll(
        settings: GenericLandSettings,
        state: GenericLandState,
        vehicle: PokemonEntity
    ): Boolean {
        return false
    }

    override fun turnOffOnGround(
        settings: GenericLandSettings,
        state: GenericLandState,
        vehicle: PokemonEntity
    ): Boolean {
        return false
    }

    override fun dismountOnShift(
        settings: GenericLandSettings,
        state: GenericLandState,
        vehicle: PokemonEntity
    ): Boolean {
        return false
    }

    override fun shouldRotatePokemonHead(
        settings: GenericLandSettings,
        state: GenericLandState,
        vehicle: PokemonEntity
    ): Boolean {
        return false
    }

    override fun shouldRotatePlayerHead(
        settings: GenericLandSettings,
        state: GenericLandState,
        vehicle: PokemonEntity
    ): Boolean {
        return false
    }

    override fun createDefaultState(settings: GenericLandSettings) = GenericLandState()
}

class GenericLandSettings : RidingBehaviourSettings {
    override val key = GenericLandBehaviour.KEY

    var canJump = "true".asExpression()
        private set

    var speedExpr: Expression = "q.get_ride_stats('SPEED', 'LAND', 1.0, 0.3)".asExpression()
        private set

    // Max accel is a whole 1.0 in 1 second. The conversion in the function below is to convert seconds to ticks
    var accelerationExpr: Expression =
        "q.get_ride_stats('ACCELERATION', 'LAND', (1.0 / (20.0 * 1.5)), (1.0 / (20.0 * 5.0)))".asExpression()
        private set

    // Between 30 seconds and 10 seconds at the lowest when at full speed.
    var staminaExpr: Expression = "q.get_ride_stats('STAMINA', 'LAND', 30.0, 10.0)".asExpression()
        private set

    //Between a one block jump and a ten block jump
    var jumpExpr: Expression = "q.get_ride_stats('JUMP', 'LAND', 10.0, 1.0)".asExpression()
        private set

    var handlingExpr: Expression = "q.get_ride_stats('SKILL', 'LAND', 140.0, 20.0)".asExpression()
        private set

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeResourceLocation(key)
        buffer.writeExpression(speedExpr)
        buffer.writeExpression(accelerationExpr)
        buffer.writeExpression(staminaExpr)
        buffer.writeExpression(jumpExpr)
        buffer.writeExpression(handlingExpr)
    }

    override fun decode(buffer: RegistryFriendlyByteBuf) {
        speedExpr = buffer.readExpression()
        accelerationExpr = buffer.readExpression()
        staminaExpr = buffer.readExpression()
        jumpExpr = buffer.readExpression()
        handlingExpr = buffer.readExpression()
    }

}

class GenericLandState : RidingBehaviourState() {
    var currSpeed = ridingState(0.0, Side.BOTH)

    override fun copy() = GenericLandState().also {
        it.rideVelocity.set(this.rideVelocity.get(), forced = true)
        it.stamina.set(this.stamina.get(), forced = true)
    }

    override fun shouldSync(previous: RidingBehaviourState): Boolean {
        if (previous !is GenericLandState) return false
        return super.shouldSync(previous)
    }
}
