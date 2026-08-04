/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.riding.behaviour.types.land

import com.bedrockk.molang.Expression
import com.cobblemon.mod.common.CobblemonRideSettings
import com.cobblemon.mod.common.OrientationControllable
import com.cobblemon.mod.common.api.orientation.OrientationController
import com.cobblemon.mod.common.api.orientation.OrientationController.Companion.UP
import com.cobblemon.mod.common.api.riding.RidingStyle
import com.cobblemon.mod.common.api.riding.behaviour.*
import com.cobblemon.mod.common.api.riding.posing.PoseOption
import com.cobblemon.mod.common.api.riding.posing.PoseProvider
import com.cobblemon.mod.common.api.riding.util.DistanceFieldLayer
import com.cobblemon.mod.common.api.riding.util.FieldPrimitive
import com.cobblemon.mod.common.api.riding.util.PrimitiveShape
import com.cobblemon.mod.common.api.riding.util.collectPrimitivesFromBlocks
import com.cobblemon.mod.common.api.riding.util.estimateGradient
import com.cobblemon.mod.common.api.riding.util.sampleField
import com.cobblemon.mod.common.api.riding.util.visualizeDistanceField
import com.cobblemon.mod.common.api.riding.sound.RideSoundSettingsList
import com.cobblemon.mod.common.api.riding.stats.RidingStat
import com.cobblemon.mod.common.api.riding.util.DistanceFieldTuning
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.*
import com.cobblemon.mod.common.util.math.geometry.toRadians
import net.minecraft.core.Direction
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import net.minecraft.util.SmoothDouble
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.Shapes
import org.joml.Matrix3f
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.*

class ClimbBehaviour : RidingBehaviour<ClimbSettings, ClimbState> {
    companion object {
        val KEY = cobblemonResource("land/climb")
    }

    override val key = KEY

    override fun getRidingStyle(settings: ClimbSettings, state: ClimbState): RidingStyle {
        return RidingStyle.LAND
    }

    val poseProvider = PoseProvider<ClimbSettings, ClimbState>(PoseType.STAND)
        .with(PoseOption(PoseType.WALK) { _, state, vehicle ->
            return@PoseOption abs(state.rideVelocity.get().horizontalDistance()) > 0.0
        })

    override fun isActive(
        settings: ClimbSettings,
        state: ClimbState,
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
        settings: ClimbSettings,
        state: ClimbState,
        vehicle: PokemonEntity
    ): PoseType {
        return this.poseProvider.select(settings, state, vehicle)
    }

    override fun speed(
        settings: ClimbSettings,
        state: ClimbState,
        vehicle: PokemonEntity,
        driver: Player
    ): Float {
        return state.rideVelocity.get().length().toFloat()
    }

    override fun tick(
        settings: ClimbSettings,
        state: ClimbState,
        vehicle: PokemonEntity,
        driver: Player,
        input: Vec3
    ) {
        handleSprinting(state, driver)
        inAirCheck(state, vehicle)
        tickStamina(settings, state, vehicle)

        // attachmentNormal: discrete, physics-facing - which face are we touching, used
        //   for the sticking force (newDownForce) in velocity().
        // renderSurfaceNormal: smoothed field-gradient - purely cosmetic, used to slowly
        //   rotate the model's up vector in angRollVel(). The two are intentionally
        //   separate and should not be conflated.
        updateAttachment(state, vehicle)
        updateRenderSurfaceNormal(state, vehicle)

        //TODO: should be a config somewhere
        val debugVisualizeField = true;

        if (debugVisualizeField && !vehicle.level().isClientSide && vehicle.tickCount % 5 == 0) {
            visualizeDistanceField(
                referenceEntity = vehicle,
                center = vehicle.position().add(0.0, (vehicle.bbHeight * 0.5f).toDouble(), 0.0),
                layers = listOf(
                    DistanceFieldLayer(isoValue = 0.0f, color = Vector3f(1f, 0.2f, 0.2f)),
                    DistanceFieldLayer(isoValue = 1.0f, color = Vector3f(0.2f, 0.6f, 1f))
                ),
                horizontalRadius = 2.0,
                verticalRange = 2.0,
                sampleSpacing = 0.35,
                marchStep = 0.15,
                tuning = DistanceFieldTuning(smoothingRange = 0.5f)
            )
        }
    }

    /**
     * Computes the smoothed field-gradient normal used for rendering only (up-vector
     * alignment in angRollVel). Cached here at tick rate since collecting primitives
     * and sampling the field is real work - actually applying the rotation happens
     * separately at frame rate, same pattern as HorseBehaviour's surfDist/BoardBehaviour's
     * surfaceNormal.
     */
    fun updateRenderSurfaceNormal(state: ClimbState, vehicle: PokemonEntity) {
        val level = vehicle.level()
        val center = vehicle.position().add(0.0, (vehicle.bbHeight * 0.5f).toDouble(), 0.0)
        val horizontalRadius = 1.5
        val verticalRange = 1.5

        val searchBox = AABB(
            center.x - horizontalRadius, center.y - verticalRange, center.z - horizontalRadius,
            center.x + horizontalRadius, center.y + verticalRange, center.z + horizontalRadius
        )

        val primitives: List<FieldPrimitive> = collectPrimitivesFromBlocks(level, searchBox, PrimitiveShape.ELLIPSOID)
        if (primitives.isEmpty()) return

        val tuning = DistanceFieldTuning(smoothingRange = 0.5f)
        val gradient = estimateGradient(primitives, center, tuning)
        state.renderSurfaceNormal.set(gradient)
    }

    /** Debug viz: draws a short line of particles from the vehicle out along the current attachment normal. */
    fun spawnAttachmentNormalParticles(vehicle: PokemonEntity, normal: Vec3) {
        val level = vehicle.level()
        if (!level.isClientSide) return
        if (normal.lengthSqr() < 1e-6) return

        val origin = vehicle.position().add(0.0, (vehicle.bbHeight * 0.5f).toDouble(), 0.0)
        val length = 1.5
        val steps = 15
        for (i in 0..steps) {
            val t = i.toDouble() / steps
            val point = origin.add(normal.scale(length * t))
            level.addParticle(ParticleTypes.END_ROD, point.x, point.y, point.z, 0.0, 0.0, 0.0)
        }
    }

    fun inAirCheck(
        state: ClimbState,
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
        state: ClimbState,
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
        settings: ClimbSettings,
        state: ClimbState,
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
        settings: ClimbSettings,
        state: ClimbState,
        vehicle: PokemonEntity,
        driver: LivingEntity
    ) { return }

    override fun clampPassengerRotation(
        settings: ClimbSettings,
        state: ClimbState,
        vehicle: PokemonEntity,
        driver: LivingEntity
    ) { }


    override fun rotation(
        settings: ClimbSettings,
        state: ClimbState,
        vehicle: PokemonEntity,
        driver: LivingEntity
    ): Vec2 {
        return Vec2(vehicle.xRot, vehicle.yRot )

    }

    /*
   *  Calculates the rotation amount given the difference between
   *  the current player y rot and entity y rot. This gives the
   *  affect of influencing a rides rotation through the mouse
   *  without it being instant.
   *
   * THIS IS ADAPTED FOR 3D ORIENTATION
   */
    fun calcRotAmount(
        settings: ClimbSettings,
        state: ClimbState,
        vehicle: PokemonEntity,
        driver: LivingEntity
    ): Float {
        val topSpeed = vehicle.runtime.resolveDouble(settings.speedExpr)
        val handling = vehicle.runtime.resolveDouble(settings.handlingExpr)
        val walkHandlingBoost = 5
        val maxYawDiff = vehicle.runtime.resolveFloat(settings.lookYawLimit)

        // Normalize the current rotation diff
        val controller = (vehicle as? OrientationControllable)?.orientationController
        if (controller == null || controller.orientation == null) return 0.0f

        val rotDiff = Mth.wrapDegrees(Mth.wrapDegrees(driver.yRot) - controller.yaw).coerceIn(-maxYawDiff,maxYawDiff)
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
        settings: ClimbSettings,
        state: ClimbState,
        vehicle: PokemonEntity,
        driver: Player,
        input: Vec3
    ): Vec3 {
        state.rideVelocity.set(calculateRideSpaceVel(settings, state, vehicle, driver))
        return state.rideVelocity.get()
    }

    private fun calculateRideSpaceVel(
        settings: ClimbSettings,
        state: ClimbState,
        vehicle: PokemonEntity,
        driver: Player
    ): Vec3 {
        val globalHorse: HorseSettings = CobblemonRideSettings.horse
        val canSprint = vehicle.runtime.resolveBoolean(settings.canSprint ?: globalHorse.canSprint!!)
        val canJump = vehicle.runtime.resolveBoolean(settings.canJump ?: globalHorse.canJump!!)
        val jumpForce = vehicle.runtime.resolveDouble(settings.jumpExpr ?: globalHorse.jumpExpr!!) * 0.75
        val rideTopSpeed = vehicle.runtime.resolveDouble(settings.speedExpr ?: globalHorse.speedExpr!!)
        val walkSpeed = getWalkSpeed(vehicle)
        val topSpeed = if(canSprint && state.sprinting.get()) rideTopSpeed else walkSpeed
        val accelTime = if(state.sprinting.get()) (vehicle.runtime.resolveDouble(settings.accelerationExpr ?: globalHorse.accelerationExpr!!) * 20.0) else 0.5 * 20.0
        val accel = topSpeed / accelTime
        var activeInput = false

        /******************************************************
         * Gather the previous velocity and check for horizontal
         * collisions
         *****************************************************/
        var newVelocity = state.rideVelocity.get() //.normalize().scale(vehicle.deltaMovement.horizontalDistance())

        /******************************************************
         * Gather the previous velocity and check for horizontal
         * collisions
         *****************************************************/
        if (vehicle.horizontalCollision) {
            var horizontalVec = Vec3(newVelocity.x, 0.0, newVelocity.z)
            horizontalVec = horizontalVec.normalize().scale(min(horizontalVec.length(), 0.3))
            newVelocity = Vec3(horizontalVec.x, newVelocity.y, horizontalVec.z)
        }

        /******************************************************
         * Speed up and slow down based on input
         *****************************************************/
        if (driver.zza != 0.0f) {

            // If on a tight turn then do not speed up past half of top speed in order to turn quicker
            // Also determine how fast to be slowing down based on how far turned you are
            val lookYawLimit = vehicle.runtime.resolveFloat(settings.lookYawLimit ?: globalHorse.lookYawLimit!!)
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
        val isOnTopSurface = state.attachmentNormal.get() == Vec3(0.0, 1.0, 0.0)
        if (isOnTopSurface) {
            if (!vehicle.onGround() && state.jumpTicks.get() <= 0) {
                val gravity = (9.8 / ( 20.0)) * 0.2 * 0.6
                val terminalVel = 2.0
                newVelocity = Vec3(newVelocity.x, max(newVelocity.y - gravity, -terminalVel), newVelocity.z)
            } else if(vehicle.onGround()) {
                newVelocity = Vec3(newVelocity.x, 0.0, newVelocity.z)
            }
        } else {
            // Wall/ceiling - small constant pull to stay stuck, not real gravity.
            val stickForce = 0.08
            newVelocity = Vec3(newVelocity.x, newVelocity.y - stickForce, newVelocity.z)
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

        // Zero out lateral velocity possibly picked up from a controller transition
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
        settings: ClimbSettings,
        state: ClimbState,
        vehicle: PokemonEntity,
        driver: Player,
        deltaTime: Double
    ): Vec3 {
        if (vehicle !is OrientationControllable) return Vec3.ZERO
        val controller = vehicle.orientationController

        // Smoothly rotate the model's up vector toward the (separately computed,
        // smoothed) field-gradient normal. Purely cosmetic - does not affect attachment
        // or movement, which are driven by state.attachmentNormal instead.
        val upAlignmentRatePerTick = 0.2
        val frameBlend = 1.0 - (1.0 - upAlignmentRatePerTick).pow(deltaTime * 20.0)
        alignUpToSurfaceNormal(controller, state.renderSurfaceNormal.get(), frameBlend.toFloat())

        return Vec3.ZERO
    }

    /**
     * Rotates [controller]'s orientation so its up vector moves a fraction of the way
     * toward [targetUp] - partial per call, not instant, so alignment settles smoothly
     * rather than snapping every time the target changes.
     */
    private fun alignUpToSurfaceNormal(controller: OrientationController, targetUp: Vec3, alignmentBlend: Float) {
        if (targetUp.lengthSqr() < 1e-6) return
        val targetUpVec = Vector3f(targetUp.x.toFloat(), targetUp.y.toFloat(), targetUp.z.toFloat()).normalize()

        controller.updateOrientation { current ->
            val currentUp = current.transform(Vector3f(UP), Vector3f())
            if (currentUp.lengthSquared() < 1e-6f) return@updateOrientation current

            // Full rotation that would exactly align currentUp to targetUp...
            val fullCorrection = Quaternionf().rotationTo(currentUp, targetUpVec)
            // ...but only take a partial step toward it, so alignment is smooth, not instant.
            val steppedCorrection = Quaternionf().slerp(fullCorrection, alignmentBlend)

            Matrix3f().set(steppedCorrection).mul(current, Matrix3f())
        }
    }

    override fun rotationOnMouseXY(
        settings: ClimbSettings,
        state: ClimbState,
        vehicle: PokemonEntity,
        driver: Player,
        mouseY: Double,
        mouseX: Double,
        mouseYSmoother: SmoothDouble,
        mouseXSmoother: SmoothDouble,
        sensitivity: Double,
        deltaTime: Double
    ): Vec3 {
        if (vehicle !is OrientationControllable) return Vec3.ZERO
        val vehicleController = vehicle.orientationController

        val smoothingSpeed = 4
        val xInput = mouseXSmoother.getNewDeltaValue(mouseX * 0.1, deltaTime * smoothingSpeed)
        val yInput = mouseYSmoother.getNewDeltaValue(mouseY * 0.1, deltaTime * smoothingSpeed)

        //yaw, pitch, roll
        return Vec3(xInput, 0.0, 0.0)
    }


    override fun canJump(
        settings: ClimbSettings,
        state: ClimbState,
        vehicle: PokemonEntity,
        driver: Player
    ): Boolean {
        return false
    }

    override fun setRideBar(
        settings: ClimbSettings,
        state: ClimbState,
        vehicle: PokemonEntity,
        driver: Player
    ): Float {
        return (state.stamina.get() / 1.0f)
    }

    override fun jumpForce(
        settings: ClimbSettings,
        state: ClimbState,
        vehicle: PokemonEntity,
        driver: Player,
        jumpStrength: Int
    ): Vec3 {
        return Vec3.ZERO
    }

    override fun gravity(
        settings: ClimbSettings,
        state: ClimbState,
        vehicle: PokemonEntity,
        regularGravity: Double
    ): Double {
        return 0.0
    }

    override fun rideFovMultiplier(
        settings: ClimbSettings,
        state: ClimbState,
        vehicle: PokemonEntity,
        driver: Player
    ): Float {
        return 1.0f
    }

    override fun useAngVelSmoothing(
        settings: ClimbSettings,
        state: ClimbState,
        vehicle: PokemonEntity
    ): Boolean {
        return false
    }

    override fun inertia(
        settings: ClimbSettings,
        state: ClimbState,
        vehicle: PokemonEntity
    ): Double {
        return 1.0
    }

    override fun shouldRoll(
        settings: ClimbSettings,
        state: ClimbState,
        vehicle: PokemonEntity
    ): Boolean {
        return true
    }

    override fun turnOffOnGround(
        settings: ClimbSettings,
        state: ClimbState,
        vehicle: PokemonEntity
    ): Boolean {
        return false
    }

    override fun dismountOnShift(
        settings: ClimbSettings,
        state: ClimbState,
        vehicle: PokemonEntity
    ): Boolean {
        return false
    }

    override fun shouldRotatePokemonHead(
        settings: ClimbSettings,
        state: ClimbState,
        vehicle: PokemonEntity
    ): Boolean {
        return false
    }

    override fun shouldRotateRiderHead(
        settings: ClimbSettings,
        state: ClimbState,
        vehicle: PokemonEntity
    ): Boolean {
        return true
    }

    override fun getRideSounds(
        settings: ClimbSettings,
        state: ClimbState,
        vehicle: PokemonEntity
    ): RideSoundSettingsList {
        return settings.rideSounds
    }

    override fun createDefaultState(settings: ClimbSettings) = ClimbState()
}

class ClimbSettings : RidingBehaviourSettings {
    override val key = ClimbBehaviour.KEY
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
    var jumpExpr: Expression = "q.get_ride_stats('JUMP', 'LAND', 1.0, 0.38)".asExpression()
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

class ClimbState : RidingBehaviourState() {
    var sprinting = ridingState(false, Side.CLIENT)
    var inAir = ridingState(false, Side.CLIENT)
    var jumpTicks = ridingState(0, Side.CLIENT)

    // Discrete, physics-facing: which of the 6 faces we're attached to right now.
    // Drives newDownForce in velocity(). Set by updateAttachment().
    var attachmentNormal = ridingState(Vec3(0.0, 1.0, 0.0), Side.CLIENT)
    var attachmentPoint = ridingState(Vec3(0.0, 1.0, 0.0), Side.CLIENT)

    // Smoothed, rendering-facing: the field-gradient normal used only to slowly rotate
    // the model's visual up vector in angRollVel(). Deliberately separate from
    // attachmentNormal - see comments in tick(). Set by updateRenderSurfaceNormal().
    var renderSurfaceNormal = ridingState(Vec3(0.0, 1.0, 0.0), Side.CLIENT)

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
        attachmentNormal.set(Vec3(0.0, 1.0, 0.0), forced = true)
        attachmentPoint.set(Vec3(0.0, 1.0, 0.0), forced = true)
        renderSurfaceNormal.set(Vec3(0.0, 1.0, 0.0), forced = true)
    }

    override fun copy() = ClimbState().also {
        it.rideVelocity.set(this.rideVelocity.get(), forced = true)
        it.stamina.set(this.stamina.get(), forced = true)
        it.sprinting.set(this.sprinting.get(), forced = true)
        it.inAir.set(this.inAir.get(), forced = true)
        it.jumpTicks.set(this.jumpTicks.get(), forced = true)
        it.attachmentNormal.set(this.attachmentNormal.get(), forced = true)
        it.attachmentPoint.set(this.attachmentNormal.get(), forced = true)
        it.renderSurfaceNormal.set(this.renderSurfaceNormal.get(), forced = true)
    }

    override fun shouldSync(previous: RidingBehaviourState): Boolean {
        if (previous !is ClimbState) return false
        if (previous.sprinting.get() != sprinting.get()) return true
        if (previous.inAir.get() != inAir.get()) return true
        return super.shouldSync(previous)
    }
}

fun updateAttachment(state: ClimbState, vehicle: PokemonEntity) {
    val probeMargin = 0.15
    val currentFace = AttachmentFace.entries.firstOrNull { it.normal == state.attachmentNormal.get() }
    val face = determineAttachmentFace(vehicle, currentFace, probeMargin)
    if (face != null) {
        state.attachmentNormal.set(face.normal)
    }
    // face == null means nothing's currently in reach - leave attachmentNormal as
    // whatever it last was until the jump/detach state takes over.
}

enum class AttachmentFace(val normal: Vec3) {
    UP(Vec3(0.0, 1.0, 0.0)),
    DOWN(Vec3(0.0, -1.0, 0.0)),
    NORTH(Vec3(0.0, 0.0, -1.0)),
    SOUTH(Vec3(0.0, 0.0, 1.0)),
    EAST(Vec3(1.0, 0.0, 0.0)),
    WEST(Vec3(-1.0, 0.0, 0.0))
}

/** Thin slab test on one face of the vehicle's actual bounding box - true if anything solid is touching that side. */
fun probeFaceContact(vehicle: PokemonEntity, face: AttachmentFace, probeMargin: Double): Boolean {
    val box = vehicle.boundingBox
    val testBox = when (face) {
        AttachmentFace.UP    -> AABB(box.minX, box.maxY, box.minZ, box.maxX, box.maxY + probeMargin, box.maxZ)
        AttachmentFace.DOWN  -> AABB(box.minX, box.minY - probeMargin, box.minZ, box.maxX, box.minY, box.maxZ)
        AttachmentFace.NORTH -> AABB(box.minX, box.minY, box.minZ - probeMargin, box.maxX, box.maxY, box.minZ)
        AttachmentFace.SOUTH -> AABB(box.minX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ + probeMargin)
        AttachmentFace.EAST  -> AABB(box.maxX, box.minY, box.minZ, box.maxX + probeMargin, box.maxY, box.maxZ)
        AttachmentFace.WEST  -> AABB(box.minX - probeMargin, box.minY, box.minZ, box.minX, box.maxY, box.maxZ)
    }
    return !vehicle.level().noCollision(vehicle, testBox)
}

/** Stays on the current face if it's still touching, otherwise scans for a new one. */
fun determineAttachmentFace(vehicle: PokemonEntity, currentFace: AttachmentFace?, probeMargin: Double): AttachmentFace? {
    if (currentFace != null && probeFaceContact(vehicle, currentFace, probeMargin)) {
        return currentFace
    }
    return AttachmentFace.entries.firstOrNull { probeFaceContact(vehicle, it, probeMargin) }
}