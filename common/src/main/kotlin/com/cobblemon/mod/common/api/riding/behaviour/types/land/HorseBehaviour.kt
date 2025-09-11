/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.riding.behaviour.types.land

import com.bedrockk.molang.Expression
import com.cobblemon.mod.common.api.riding.RidingStyle
import com.cobblemon.mod.common.api.riding.behaviour.*
import com.cobblemon.mod.common.api.riding.posing.PoseOption
import com.cobblemon.mod.common.api.riding.posing.PoseProvider
import com.cobblemon.mod.common.api.riding.sound.RideSoundSettingsList
import com.cobblemon.mod.common.api.riding.stats.RidingStat
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.*
import net.minecraft.core.Direction
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import net.minecraft.util.SmoothDouble
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.Shapes
import kotlin.math.*

class HorseBehaviour : RidingBehaviour<HorseSettings, HorseState> {
    companion object {
        val KEY = cobblemonResource("land/horse")
    }

    override val key = KEY

    override fun getRidingStyle(settings: HorseSettings, state: HorseState): RidingStyle {
        return RidingStyle.LAND
    }

    val poseProvider = PoseProvider<HorseSettings, HorseState>(PoseType.STAND)
        .with(PoseOption(PoseType.WALK) { _, state, vehicle ->
            return@PoseOption abs(state.rideVelocity.get().horizontalDistance()) > 0.0
        })

    override fun isActive(
        settings: HorseSettings,
        state: HorseState,
        vehicle: PokemonEntity
    ): Boolean {
        return Shapes.create(vehicle.boundingBox).blockPositionsAsListRounded().any {
            // Need to check other fluids
            if (vehicle.isInWater || vehicle.isUnderWater) {
                return@any false
            }
            // This might not actually work, depending on what the yPos actually is. yPos of the middle of the entity? the feet?
            if (it.y.toDouble() == (vehicle.position().y)) {
                val blockState = vehicle.level().getBlockState(it.below())
                return@any !blockState.isAir && blockState.fluidState.isEmpty
            }
            true
        }
    }

    override fun pose(
        settings: HorseSettings,
        state: HorseState,
        vehicle: PokemonEntity
    ): PoseType {
        return this.poseProvider.select(settings, state, vehicle)
    }

    override fun speed(
        settings: HorseSettings,
        state: HorseState,
        vehicle: PokemonEntity,
        driver: Player
    ): Float {
        return state.rideVelocity.get().length().toFloat()
    }

    override fun tick(
        settings: HorseSettings,
        state: HorseState,
        vehicle: PokemonEntity,
        driver: Player,
        input: Vec3
    ) {
        handleSprinting(state, driver)
        inAirCheck(state, vehicle)
        tickStamina(settings, state, vehicle)
    }

    fun inAirCheck(
        state: HorseState,
        vehicle: PokemonEntity,
    ) {
        // Check both vertical movement and if there are blocks below.
        val posBelow = vehicle.blockPosition().below()
        val blockStateBelow = vehicle.level().getBlockState(posBelow)
        val isAirOrLiquid = blockStateBelow.isAir || !blockStateBelow.fluidState.isEmpty

        val canSupportEntity = blockStateBelow.isFaceSturdy(vehicle.level(), posBelow, Direction.UP)
        val standingOnSolid = canSupportEntity && !isAirOrLiquid

        // inAir if not on the ground
        val inAir = !(vehicle.deltaMovement.y == 0.0 || standingOnSolid)
        state.inAir.set(inAir)
    }

    fun handleSprinting(
        state: HorseState,
        driver: Player
    ) {
        if (state.sprinting.get()) {
            state.sprinting.set(driver.isSprinting && state.stamina.get() > 0.0f)
        } else {
            // Only allow sprinting to start if over x percentage of stamina left
            val stamSprintPerc = 25.0f
            state.sprinting.set(driver.isSprinting && state.stamina.get() > stamSprintPerc / 100.0f)
        }
    }

    fun tickStamina(
        settings: HorseSettings,
        state: HorseState,
        vehicle: PokemonEntity,
    ) {
        val stam = state.stamina.get()
        var newStam = stam
        val stamDrainRate = (1.0f / vehicle.runtime.resolveDouble(settings.staminaExpr)).toFloat() / 20.0f

        if (state.sprinting.get()) {
            newStam = max(0.0f,stam - stamDrainRate)

        } else {
            newStam = min(1.0f,stam + stamDrainRate * 4)
        }

        state.stamina.set(newStam)
    }

    override fun updatePassengerRotation(
        settings: HorseSettings,
        state: HorseState,
        vehicle: PokemonEntity,
        driver: LivingEntity
    ) { return }

    override fun clampPassengerRotation(
        settings: HorseSettings,
        state: HorseState,
        vehicle: PokemonEntity,
        driver: LivingEntity
    ) { }


    override fun rotation(
        settings: HorseSettings,
        state: HorseState,
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
        settings: HorseSettings,
        state: HorseState,
        vehicle: PokemonEntity,
        driver: LivingEntity
    ): Float {
        val topSpeed = vehicle.runtime.resolveDouble(settings.speedExpr)
        val handling = vehicle.runtime.resolveDouble(settings.handlingExpr)
        val walkHandlingBoost = 5
        val maxYawDiff = vehicle.runtime.resolveFloat(settings.lookYawLimit)

        // Normalize the current rotation diff
        val rotDiff = Mth.wrapDegrees(driver.yRot - vehicle.yRot).coerceIn(-maxYawDiff,maxYawDiff)
        val rotDiffNorm = rotDiff / maxYawDiff

        // Take the square root so that the ride levels out quicker when at lower differences between entity
        // y and driver y
        // This influences the speed of the turn based on how far in one direction you're looking
        val minRotMod = 0.4f // Min speed that the rotation modulation converges towards
        val rotDiffMod = ((sqrt(abs(rotDiffNorm)) * (1.0f - minRotMod)) + minRotMod) * rotDiffNorm.sign

        // Turn rate should be quick when walking and slower when sprinting.
        // Smoothly move between these based on speed
        val walkSpeed = getWalkSpeed(vehicle)
        val w = max(walkSpeed, vehicle.deltaMovement.horizontalDistance())
        val invRelSpeed = (RidingBehaviour.scaleToRange(w, walkSpeed, topSpeed ) - 1.0f) * -1.0f
        val turnRate = ((handling.toFloat() / 20.0f) * max(walkHandlingBoost * invRelSpeed, 1.0)).toFloat()

        // Ensure you only ever rotate as much difference as there is between the angles.
        val turnSpeed = turnRate * rotDiffMod
        val rotAmount = turnSpeed.coerceIn(-abs(rotDiff), abs(rotDiff))

        return rotAmount
    }


    override fun velocity(
        settings: HorseSettings,
        state: HorseState,
        vehicle: PokemonEntity,
        driver: Player,
        input: Vec3
    ): Vec3 {
            state.rideVelocity.set(calculateRideSpaceVel(settings, state, vehicle, driver))
            return state.rideVelocity.get()
    }

    private fun calculateRideSpaceVel(
        settings: HorseSettings,
        state: HorseState,
        vehicle: PokemonEntity,
        driver: Player
    ): Vec3 {
        val canSprint = vehicle.runtime.resolveBoolean(settings.canSprint)
        val canJump = vehicle.runtime.resolveBoolean(settings.canJump)
        val jumpForce = vehicle.runtime.resolveDouble(settings.jumpExpr) * 0.75
        val rideTopSpeed = vehicle.runtime.resolveDouble(settings.speedExpr)
        val walkSpeed = getWalkSpeed(vehicle)
        val topSpeed = if(canSprint && state.sprinting.get()) rideTopSpeed else walkSpeed
        val accel = topSpeed / (vehicle.runtime.resolveDouble(settings.accelerationExpr) * 20.0)
        var activeInput = false

        /******************************************************
         * Gather the previous velocity and check for horizontal
         * collisions
         *****************************************************/
        var newVelocity = state.rideVelocity.get() //.normalize().scale(vehicle.deltaMovement.horizontalDistance())

        if (vehicle.horizontalCollision) {
            newVelocity = newVelocity.normalize().scale(vehicle.deltaMovement.length())
        }

        /******************************************************
         * Speed up and slow down based on input
         *****************************************************/
        if (driver.zza != 0.0f) {

            // If on a tight turn then do not speed up past half of top speed in order to turn quicker
            // Also determine how fast to be slowing down based on how far turned you are
            val lookYawLimit = vehicle.runtime.resolveFloat(settings.lookYawLimit)
            val percOfMaxTurnSpeed = abs(Mth.wrapDegrees(driver.yRot - vehicle.yRot) / lookYawLimit) * 100.0f
            val turnPercThresh = 0.0f
            val s = min(((percOfMaxTurnSpeed - turnPercThresh) / (100.0f - turnPercThresh)).pow(1),1.0f)
            val effectiveTopSpeed = if (percOfMaxTurnSpeed > turnPercThresh) topSpeed / max(2.0f*s,1.0f) else topSpeed
            val turningSlowDown = s * 0.1

            //make sure it can't exceed top speed
            val forwardInput = when {
                driver.zza > 0 && newVelocity.z > effectiveTopSpeed -> 0.0
                driver.zza < 0 && newVelocity.z < (-effectiveTopSpeed / 3.0) -> 0.0
                else -> driver.zza.sign
            }

            // Add extra friction if trying to slow down to turn faster
            if (newVelocity.z > effectiveTopSpeed) {
                newVelocity = newVelocity.subtract(0.0, 0.0, min(turningSlowDown * newVelocity.z.sign, newVelocity.z))
            }

            newVelocity = Vec3(
                newVelocity.x,
                newVelocity.y,
                (newVelocity.z + (accel * forwardInput.toDouble())))

            activeInput = true
        }

        /******************************************************
         * Gravity logic
         *****************************************************/
        if (!vehicle.onGround() && state.jumpTicks.get() <= 0) {
            val gravity = (9.8 / ( 20.0)) * 0.2 * 0.6
            val terminalVel = 2.0
            newVelocity = Vec3(newVelocity.x, max(newVelocity.y - gravity, -terminalVel), newVelocity.z)
        } else if(vehicle.onGround()) {
            newVelocity = Vec3(newVelocity.x, 0.0, newVelocity.z)
        }


        /******************************************************
         * Ground Friction
         *****************************************************/
        if( (newVelocity.horizontalDistance() > 0 && vehicle.onGround() && !activeInput) || newVelocity.horizontalDistance() > topSpeed) {
            newVelocity = newVelocity.subtract(0.0, 0.0, min(0.03 * newVelocity.z.sign, newVelocity.z))
        }

        /******************************************************
         * Jump Logic
         *****************************************************/
        if (state.jumpTicks.get() > 0 || (state.jumpTicks.get() >= 0 && driver.jumping && vehicle.onGround() && canJump && driver.deltaMovement.y <= 0.1)) {
            // Spread out jumpforce so that variable height jumps are possible
            val jumpInputTicks = 6
            if (driver.jumping && (state.jumpTicks.get() >= 0 && state.jumpTicks.get() < jumpInputTicks)) {
                val appliedJumpForce = ((jumpForce*1.5) / jumpInputTicks) //* (1 - state.jumpTicks.get() / jumpInputTicks)
                newVelocity = Vec3(newVelocity.x, newVelocity.y + appliedJumpForce, newVelocity.z)
                state.jumpTicks.set(state.jumpTicks.get() + 1)
            } else {
                // Set delay before next jump is possible after hitting ground
                val tickJumpDelay = 3
                state.jumpTicks.set(-tickJumpDelay)
            }
        } else if (vehicle.onGround() && state.jumpTicks.get() < 0) {
            // Tick off the delay once reaching the ground
            state.jumpTicks.set(state.jumpTicks.get() + 1)
        }

        //Zero out lateral velocity possibly picked up from a controller transition
        newVelocity = Vec3(0.0, newVelocity.y, newVelocity.z)


        return newVelocity
    }

    private fun getWalkSpeed( vehicle: PokemonEntity ): Double {
        val walkspeed = vehicle.runtime.resolveDouble(vehicle.behaviour.moving.walk.walkSpeed)
        val movementSpeed = vehicle.attributes.getValue(Attributes.MOVEMENT_SPEED)
        val speedModifier = 1.2 * 0.35
        return walkspeed * movementSpeed * speedModifier

    }

    override fun angRollVel(
        settings: HorseSettings,
        state: HorseState,
        vehicle: PokemonEntity,
        driver: Player,
        deltaTime: Double
    ): Vec3 {
        return Vec3.ZERO
    }

    override fun rotationOnMouseXY(
        settings: HorseSettings,
        state: HorseState,
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
        settings: HorseSettings,
        state: HorseState,
        vehicle: PokemonEntity,
        driver: Player
    ): Boolean {
        return false
    }

    override fun setRideBar(
        settings: HorseSettings,
        state: HorseState,
        vehicle: PokemonEntity,
        driver: Player
    ): Float {
        return (state.stamina.get() / 1.0f)
    }

    override fun jumpForce(
        settings: HorseSettings,
        state: HorseState,
        vehicle: PokemonEntity,
        driver: Player,
        jumpStrength: Int
    ): Vec3 {
        return Vec3.ZERO
    }

    override fun gravity(
        settings: HorseSettings,
        state: HorseState,
        vehicle: PokemonEntity,
        regularGravity: Double
    ): Double {
        return 0.0
    }

    override fun rideFovMultiplier(
        settings: HorseSettings,
        state: HorseState,
        vehicle: PokemonEntity,
        driver: Player
    ): Float {
        return 1.0f
    }

    override fun useAngVelSmoothing(
        settings: HorseSettings,
        state: HorseState,
        vehicle: PokemonEntity
    ): Boolean {
        return false
    }

    override fun useRidingAltPose(
        settings: HorseSettings,
        state: HorseState,
        vehicle: PokemonEntity,
        driver: Player
    ): ResourceLocation {
        when {
            state.inAir.get() -> return cobblemonResource("in_air")
            state.sprinting.get() -> return cobblemonResource("sprinting")
        }
        return cobblemonResource("no_pose")
    }

    override fun inertia(
        settings: HorseSettings,
        state: HorseState,
        vehicle: PokemonEntity
    ): Double {
            return 1.0
    }

    override fun shouldRoll(
        settings: HorseSettings,
        state: HorseState,
        vehicle: PokemonEntity
    ): Boolean {
        return false
    }

    override fun turnOffOnGround(
        settings: HorseSettings,
        state: HorseState,
        vehicle: PokemonEntity
    ): Boolean {
        return false
    }

    override fun dismountOnShift(
        settings: HorseSettings,
        state: HorseState,
        vehicle: PokemonEntity
    ): Boolean {
        return false
    }

    override fun shouldRotatePokemonHead(
        settings: HorseSettings,
        state: HorseState,
        vehicle: PokemonEntity
    ): Boolean {
        return false
    }

    override fun shouldRotateRiderHead(
        settings: HorseSettings,
        state: HorseState,
        vehicle: PokemonEntity
    ): Boolean {
        return true
    }

    override fun getRideSounds(
        settings: HorseSettings,
        state: HorseState,
        vehicle: PokemonEntity
    ): RideSoundSettingsList {
        return settings.rideSounds
    }

    override fun createDefaultState(settings: HorseSettings) = HorseState()
}

class HorseSettings : RidingBehaviourSettings {
    override val key = HorseBehaviour.KEY
    override val stats = mutableMapOf<RidingStat, IntRange>()

    var canJump = "true".asExpression()
        private set

    var canSprint = "true".asExpression()
        private set

    var lookYawLimit = "90.0f".asExpression()
        private set

    var speedExpr: Expression = "q.get_ride_stats('SPEED', 'LAND', 1.0, 0.4)".asExpression()
        private set

    // Max accel is a whole 1.0 in 1 second. The conversion in the function below is to convert seconds to ticks
    var accelerationExpr: Expression =
        "q.get_ride_stats('ACCELERATION', 'LAND', 0.5, 2.0)".asExpression()
        private set

    // Between 30 seconds and 10 seconds at the lowest when at full speed.
    var staminaExpr: Expression = "q.get_ride_stats('STAMINA', 'LAND', 30.0, 10.0)".asExpression()
        private set

    //Between a one block jump and a six block jump
    var jumpExpr: Expression = "q.get_ride_stats('JUMP', 'LAND', 1.2, 0.45)".asExpression()
        private set

    var handlingExpr: Expression = "q.get_ride_stats('SKILL', 'LAND', 180.0, 40.0)".asExpression()
        private set

    var rideSounds: RideSoundSettingsList = RideSoundSettingsList()

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeResourceLocation(key)
        buffer.writeRidingStats(stats)
        rideSounds.encode(buffer)
        buffer.writeExpression(speedExpr)
        buffer.writeExpression(accelerationExpr)
        buffer.writeExpression(staminaExpr)
        buffer.writeExpression(jumpExpr)
        buffer.writeExpression(handlingExpr)
        buffer.writeExpression(canJump)
        buffer.writeExpression(canSprint)
    }

    override fun decode(buffer: RegistryFriendlyByteBuf) {
        stats.putAll(buffer.readRidingStats())
        rideSounds = RideSoundSettingsList.decode(buffer)
        speedExpr = buffer.readExpression()
        accelerationExpr = buffer.readExpression()
        staminaExpr = buffer.readExpression()
        jumpExpr = buffer.readExpression()
        handlingExpr = buffer.readExpression()
        canJump = buffer.readExpression()
        canSprint = buffer.readExpression()
    }
}

class HorseState : RidingBehaviourState() {
    var sprinting = ridingState(false, Side.CLIENT)
    var inAir = ridingState(false, Side.CLIENT)
    var jumpTicks = ridingState(0, Side.CLIENT)

    override fun encode(buffer: FriendlyByteBuf) {
        super.encode(buffer)
        buffer.writeBoolean(sprinting.get())
        buffer.writeBoolean(inAir.get())
        buffer.writeInt(jumpTicks.get())
    }

    override fun decode(buffer: FriendlyByteBuf) {
        super.decode(buffer)
        sprinting.set(buffer.readBoolean(), forced = true)
        inAir.set(buffer.readBoolean(), forced = true)
        jumpTicks.set(buffer.readInt(), forced = true)
    }

    override fun reset() {
        super.reset()
        sprinting.set(false, forced = true)
        inAir.set(false, forced = true)
        jumpTicks.set(0, forced = true)
    }

    override fun copy() = HorseState().also {
        it.rideVelocity.set(this.rideVelocity.get(), forced = true)
        it.stamina.set(this.stamina.get(), forced = true)
        it.sprinting.set(this.sprinting.get(), forced = true)
        it.inAir.set(this.inAir.get(), forced = true)
        it.jumpTicks.set(this.jumpTicks.get(), forced = true)
    }

    override fun shouldSync(previous: RidingBehaviourState): Boolean {
        if (previous !is HorseState) return false
        if (previous.sprinting.get() != sprinting.get()) return true
        if (previous.inAir.get() != inAir.get()) return true
        return super.shouldSync(previous)
    }
}
