/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.riding.behaviour.types.air

import com.bedrockk.molang.Expression
import com.bedrockk.molang.runtime.MoLangMath.lerp
import com.cobblemon.mod.common.api.riding.RidingStyle
import com.cobblemon.mod.common.api.riding.behaviour.*
import com.cobblemon.mod.common.api.riding.posing.PoseOption
import com.cobblemon.mod.common.api.riding.posing.PoseProvider
import com.cobblemon.mod.common.api.riding.sound.RideSoundSettingsList
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.*
import com.cobblemon.mod.common.util.math.geometry.toRadians
import net.minecraft.client.Minecraft
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import net.minecraft.util.SmoothDouble
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.Shapes
import org.joml.Matrix3f
import kotlin.math.*

class RocketBehaviour : RidingBehaviour<RocketSettings, RocketState> {
    companion object {
        val KEY = cobblemonResource("air/rocket")
    }

    override val key = KEY

    override fun getRidingStyle(settings: RocketSettings, state: RocketState): RidingStyle {
        return RidingStyle.AIR
    }

    val poseProvider = PoseProvider<RocketSettings, RocketState>(PoseType.HOVER)
        .with(PoseOption(PoseType.FLY) { _, state, _ ->
            return@PoseOption state.boosting.get()
        })

    override fun isActive(
        settings: RocketSettings,
        state: RocketState,
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
        settings: RocketSettings,
        state: RocketState,
        vehicle: PokemonEntity
    ): PoseType {
        return this.poseProvider.select(settings, state, vehicle)
    }

    override fun speed(
        settings: RocketSettings,
        state: RocketState,
        vehicle: PokemonEntity,
        driver: Player
    ): Float {
        // Use this as a "tick" function and check to see if the driver is "boosting"
        if(vehicle.level().isClientSide) {

            //If the forward key is not held then it cannot be boosting
            if(Minecraft.getInstance().options.keyUp.isDown()) {
                val boostKeyPressed = Minecraft.getInstance().options.keySprint.isDown()

                //If on the previous tick the boost key was held then don't change if the ride is boosting
                if(state.boostIsToggleable.get() && boostKeyPressed) {
                    //flip the boosting state if boost key is pressed
                    state.boosting.set(!state.boosting.get())
                }

                //If the boost key is not held then next tick boosting is toggleable
                state.boostIsToggleable.set(!boostKeyPressed)
            } else {
                //Turn off boost and reset boost params
                state.boostIsToggleable.set(true)
                state.boosting.set(false)
                state.canSpeedBurst.set(true)
            }

        }

        return state.rideVelocity.get().length().toFloat()
    }

    override fun updatePassengerRotation(
        settings: RocketSettings,
        state: RocketState,
        vehicle: PokemonEntity,
        driver: LivingEntity
    ) {
        return
    }

    override fun clampPassengerRotation(
        settings: RocketSettings,
        state: RocketState,
        vehicle: PokemonEntity,
        driver: LivingEntity
    ) {
        val f = Mth.wrapDegrees(driver.yRot - vehicle.yRot)
        val lookYawLimit = 90.0f
        val g = Mth.clamp(f, -lookYawLimit, lookYawLimit)

        val ticksBeforeCorrection = 40

        if(state.prevCamYRot.get() == driver.yRot){
            state.ticksNoInput.set(state.ticksNoInput.get() + 1)
        } else {
            state.ticksNoInput.set(0)
        }
        val correctionForce = if(state.ticksNoInput.get() >= ticksBeforeCorrection && f != 0.0f && state.boosting.get()) 1f * min(sqrt(lookYawLimit / (abs(f))), 10.0f) else 0.0f
        driver.yRotO += g - f
        driver.yRot = driver.yRot + g - f //- ( clamp(f.sign * correctionForce, -abs(f), abs(f)) )
        //driver.yRot = driver.yRot + g - f - ( min )
        state.prevCamYRot.set(driver.yRot)
        driver.setYHeadRot(driver.yRot)
    }


    override fun rotation(
        settings: RocketSettings,
        state: RocketState,
        vehicle: PokemonEntity,
        driver: LivingEntity
    ): Vec2 {

        var newMomentum = state.turnMomentum.get().toDouble()

        // Grab turningAcceleration and divide 20 twice to get
        val turningAcceleration = (vehicle.runtime.resolveDouble(settings.handlingExpr) / 20.0f) / 20.0f * 2
        val turnInput =  (driver.xxa *-1.0f) * turningAcceleration

        val maxTurnMomentum = 80.0f / 20.0f

        // Base boost stats off of normal turning stats
        val boostMaxTurnMomentum = maxTurnMomentum * 0.1f
        val boostTurnInput = turnInput * 0.15f

        if(state.boosting.get()) {
            if(driver.xxa != 0.0f && abs(newMomentum + turnInput) < (boostMaxTurnMomentum)) { //If max momentum will not be exceeded then modulate
                newMomentum += boostTurnInput
            } else {
                newMomentum = lerp(newMomentum, 0.0, 0.05)
            }
        } else {
            if(driver.xxa == 0.0f) { //If no turning input then lerp to 0
                newMomentum = lerp(newMomentum, 0.0, 0.05)
            } else if(abs(newMomentum + turnInput) < maxTurnMomentum) { //If max momentum will not be exceeded then modulate
                newMomentum += turnInput
            }
        }


        state.turnMomentum.set(newMomentum.toFloat())
        driver.yRot += newMomentum.toFloat()
        return Vec2(driver.xRot, vehicle.yRot + newMomentum.toFloat())

    }


    override fun velocity(
        settings: RocketSettings,
        state: RocketState,
        vehicle: PokemonEntity,
        driver: Player,
        input: Vec3
    ): Vec3 {
        val retVel = calculateRideSpaceVel(settings, state, vehicle, driver)
        state.rideVelocity.set(retVel)
        return retVel
    }

    private fun calculateRideSpaceVel(
        settings: RocketSettings,
        state: RocketState,
        vehicle: PokemonEntity,
        driver: Player
    ): Vec3 {
        val topSpeed = vehicle.runtime.resolveDouble(settings.speedExpr) * 2
        val accel = vehicle.runtime.resolveDouble(settings.accelerationExpr) * 0.5 * 2

        var newVelocity = vehicle.deltaMovement

        // Align the direction of movement with the world coordinate space so as not to modify it through turning
        val yawAligned = Matrix3f().rotateY(vehicle.yRot.toRadians())
        newVelocity = (newVelocity.toVector3f().mul(yawAligned)).toVec3d()

        //speed up and slow down based on input
        if (driver.zza != 0.0f && state.stamina.get() > 0.0 && !state.boosting.get()) {
            //make sure it can't exceed top speed
            val forwardInput = when {
                vehicle.deltaMovement.horizontalDistance() > topSpeed && (driver.zza.sign == newVelocity.z.sign.toFloat()) -> 0.0
                else -> driver.zza.sign
            }

            newVelocity = Vec3(
                newVelocity.x,
                newVelocity.y,
                (newVelocity.z + (accel * forwardInput.toDouble())))

        } else if ((state.stamina.get() > 0.0 && state.boosting.get())) {
            val boostSpeed = topSpeed * 5
            val boostAccel = if(newVelocity.length() < boostSpeed) accel * 3 else 0.0
            val forwardInput = 1.0f
            var burst = 0.0f

            if(state.canSpeedBurst.get()) {
                burst = 0.25f
                state.canSpeedBurst.set(false)
            }

            newVelocity = Vec3(
                newVelocity.x,
                newVelocity.y,
                newVelocity.z + (boostAccel * forwardInput.toDouble()) + burst)
        }

        //Vertical movement based on driver input.
        val maxVertSpeed = 0.5f
        var vertInput = 0.0

        //If boosting then don't lose altitude
        //otherwise propel up or fall
        if (state.boosting.get() && !driver.jumping && vehicle.deltaMovement.y > (-maxVertSpeed * 0.25)) {
            vertInput = -0.7
            newVelocity = Vec3(
                newVelocity.x,
                newVelocity.y + accel * vertInput,
                newVelocity.z)
        } else if(state.boosting.get() && vehicle.deltaMovement.y < maxVertSpeed) {
            vertInput = when {
                driver.jumping -> 2.0
                else -> 0.0
            }
            newVelocity = Vec3(
                newVelocity.x,
                (newVelocity.y + (accel * 0.5 * vertInput)),
                newVelocity.z)
        } else {
            if (driver.jumping && !(vehicle.deltaMovement.y > maxVertSpeed && (newVelocity.y.sign.toFloat() > 0.0))) {
                vertInput = 1.0

                // Reset falldistance if upward motion is detected
                vehicle.resetFallDistance()
            } else {
                vertInput = -2.5
            }

            if (state.stamina.get() > 0.0) {
                newVelocity = Vec3(
                    newVelocity.x,
                    (newVelocity.y + (accel * vertInput)),
                    newVelocity.z)
            }
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
        settings: RocketSettings,
        state: RocketState,
        vehicle: PokemonEntity,
        driver: Player,
        deltaTime: Double
    ): Vec3 {
        return Vec3.ZERO
    }

    override fun rotationOnMouseXY(
        settings: RocketSettings,
        state: RocketState,
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
        settings: RocketSettings,
        state: RocketState,
        vehicle: PokemonEntity,
        driver: Player
    ): Boolean {
        return false
    }

    override fun setRideBar(
        settings: RocketSettings,
        state: RocketState,
        vehicle: PokemonEntity,
        driver: Player
    ): Float {
        //Retrieve stamina from state and use it to set the "stamina bar"
        return (state.stamina.get() / 1.0f)
    }

    override fun jumpForce(
        settings: RocketSettings,
        state: RocketState,
        vehicle: PokemonEntity,
        driver: Player,
        jumpStrength: Int
    ): Vec3 {
        return Vec3.ZERO
    }

    override fun gravity(
        settings: RocketSettings,
        state: RocketState,
        vehicle: PokemonEntity,
        regularGravity: Double
    ): Double {
        return 0.0
    }

    override fun rideFovMultiplier(
        settings: RocketSettings,
        state: RocketState,
        vehicle: PokemonEntity,
        driver: Player
    ): Float {
        if (state.boosting.get()) {
            val topSpeed = vehicle.runtime.resolveDouble(settings.speedExpr)
            //val exceededSpeed = max(vehicle.deltaMovement.length() - topSpeed, 0.0)

            //TODO: Remove this magic number and get a better comparison for boost top speed
            val normalizedBoostSpeed = normalizeVal(state.rideVelocity.get().length(), topSpeed, topSpeed * 3)
            return 1.0f + normalizedBoostSpeed.pow(2).toFloat() * 0.2f
        } else {
            return 1.0f
        }
    }

    override fun useAngVelSmoothing(
        settings: RocketSettings,
        state: RocketState,
        vehicle: PokemonEntity
    ): Boolean {
        return false
    }

    override fun useRidingAltPose(
        settings: RocketSettings,
        state: RocketState,
        vehicle: PokemonEntity,
        driver: Player
    ): ResourceLocation {
        return cobblemonResource("no_pose")
    }

    override fun inertia(
        settings: RocketSettings,
        state: RocketState,
        vehicle: PokemonEntity
    ): Double {
        return 1.0
    }

    override fun shouldRoll(
        settings: RocketSettings,
        state: RocketState,
        vehicle: PokemonEntity
    ): Boolean {
        return false
    }

    override fun turnOffOnGround(
        settings: RocketSettings,
        state: RocketState,
        vehicle: PokemonEntity
    ): Boolean {
        return false
    }

    override fun dismountOnShift(
        settings: RocketSettings,
        state: RocketState,
        vehicle: PokemonEntity
    ): Boolean {
        return false
    }

    override fun shouldRotatePokemonHead(
        settings: RocketSettings,
        state: RocketState,
        vehicle: PokemonEntity
    ): Boolean {
        return false
    }

    override fun shouldRotateRiderHead(
        settings: RocketSettings,
        state: RocketState,
        vehicle: PokemonEntity
    ): Boolean {
        return false
    }

    override fun getRideSounds(
        settings: RocketSettings,
        state: RocketState,
        vehicle: PokemonEntity
    ): RideSoundSettingsList {
        return settings.rideSounds
    }

    override fun createDefaultState(settings: RocketSettings) = RocketState()
}

class RocketSettings : RidingBehaviourSettings {
    override val key = RocketBehaviour.KEY

    var speedExpr: Expression = "q.get_ride_stats('SPEED', 'AIR', 0.65, 0.3) * 0.25".asExpression()
        private set

    // Max accel is a whole 1.0 in 1 second. The conversion in the function below is to convert seconds to ticks
    var accelerationExpr: Expression =
        "q.get_ride_stats('ACCELERATION', 'AIR', (1.0 / (20.0 * 1.5)), (1.0 / (20.0 * 3.0))) * 0.5".asExpression()
        private set

    // Between 30 seconds and 10 seconds at the lowest when at full speed.
    var staminaExpr: Expression = "q.get_ride_stats('STAMINA', 'AIR', 30.0, 10.0)".asExpression()
        private set

    //Between a one block jump and a ten block jump
    var jumpExpr: Expression = "q.get_ride_stats('JUMP', 'AIR', 10.0, 1.0)".asExpression()
        private set

    // Controls the acceleration of the turning speed since the max turning speed is locked at 60 degrees/second
    // So this is a range of 60 degrees/second per second to 20. Meaning at max stats when holding a direction it
    // takes a full second to be turning at max speed.
    var handlingExpr: Expression = "q.get_ride_stats('SKILL', 'AIR', 60.0, 20.0)".asExpression()
        private set

    var rideSounds: RideSoundSettingsList = RideSoundSettingsList()

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeResourceLocation(key)
        rideSounds.encode(buffer)
        buffer.writeExpression(speedExpr)
        buffer.writeExpression(accelerationExpr)
        buffer.writeExpression(staminaExpr)
        buffer.writeExpression(jumpExpr)
        buffer.writeExpression(handlingExpr)
    }

    override fun decode(buffer: RegistryFriendlyByteBuf) {
        rideSounds = RideSoundSettingsList.decode(buffer)
        speedExpr = buffer.readExpression()
        accelerationExpr = buffer.readExpression()
        staminaExpr = buffer.readExpression()
        jumpExpr = buffer.readExpression()
        handlingExpr = buffer.readExpression()
    }

}

class RocketState : RidingBehaviourState() {
    var boosting = ridingState(false, Side.BOTH)
    var boostIsToggleable = ridingState(false, Side.BOTH)
    var canSpeedBurst = ridingState(false, Side.BOTH)
    var prevCamYRot: SidedRidingState<Float> = ridingState(0.0f, Side.CLIENT)
    var ticksNoInput: SidedRidingState<Int> = ridingState(0, Side.CLIENT)
    val turnMomentum: SidedRidingState<Float> = ridingState(0.0f, Side.CLIENT)

    override fun reset() {
        super.reset()
        boosting.set(false, forced = true)
        boostIsToggleable.set(false, forced = true)
        canSpeedBurst.set(true, forced = true)
        prevCamYRot.set(0.0f, forced = true)
        ticksNoInput.set(0, forced = true)
        turnMomentum.set(0.0f, forced = true)
    }

    override fun copy() = RocketState().also {
        it.rideVelocity.set(this.rideVelocity.get(), forced = true)
        it.stamina.set(this.stamina.get(), forced = true)
        it.boosting.set(this.boosting.get(), forced = true)
        it.boostIsToggleable.set(this.boosting.get(), forced = true)
        it.canSpeedBurst.set(this.canSpeedBurst.get(), forced = true)
        it.prevCamYRot.set(this.prevCamYRot.get(), forced = true)
        it.ticksNoInput.set(this.ticksNoInput.get(), forced = true)
        it.turnMomentum.set(this.turnMomentum.get(), forced = true)
    }

    override fun encode(buffer: FriendlyByteBuf) {
        super.encode(buffer)
        buffer.writeBoolean(boosting.get())
        buffer.writeBoolean(boostIsToggleable.get())
        buffer.writeBoolean(canSpeedBurst.get())
        buffer.writeFloat(prevCamYRot.get())
        buffer.writeInt(ticksNoInput.get())
        buffer.writeFloat(turnMomentum.get())
    }

    override fun decode(buffer: FriendlyByteBuf) {
        super.decode(buffer)
        boosting.set(buffer.readBoolean(), forced = true)
        boostIsToggleable.set(buffer.readBoolean(), forced = true)
        canSpeedBurst.set(buffer.readBoolean(), forced = true)
        prevCamYRot.set(buffer.readFloat(), forced = true)
        ticksNoInput.set(buffer.readInt(), forced = true)
        turnMomentum.set(buffer.readFloat(), forced = true)
    }

    override fun shouldSync(previous: RidingBehaviourState): Boolean {
        if (previous !is RocketState) return false
        if (previous.boosting.get() != boosting.get()) return true
        if (previous.boostIsToggleable.get() != boostIsToggleable.get()) return true
        if (previous.canSpeedBurst.get() != canSpeedBurst.get()) return true
        if (previous.prevCamYRot.get() != prevCamYRot.get()) return true
        if (previous.ticksNoInput.get() != ticksNoInput.get()) return true
        if (previous.turnMomentum.get() != turnMomentum.get()) return true
        return super.shouldSync(previous)
    }
}
