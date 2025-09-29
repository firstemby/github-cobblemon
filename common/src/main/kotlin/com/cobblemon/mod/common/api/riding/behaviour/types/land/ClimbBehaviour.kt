/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.riding.behaviour.types.land

import com.bedrockk.molang.Expression
import com.cobblemon.mod.common.OrientationControllable
import com.cobblemon.mod.common.api.orientation.OrientationController.Companion.UP
import com.cobblemon.mod.common.api.riding.RidingStyle
import com.cobblemon.mod.common.api.riding.behaviour.*
import com.cobblemon.mod.common.api.riding.posing.PoseOption
import com.cobblemon.mod.common.api.riding.posing.PoseProvider
import com.cobblemon.mod.common.api.riding.sound.RideSoundSettingsList
import com.cobblemon.mod.common.api.riding.stats.RidingStat
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.*
import com.cobblemon.mod.common.util.math.geometry.toRadians
import it.unimi.dsi.fastutil.Arrays
import it.unimi.dsi.fastutil.floats.FloatArrays
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import net.minecraft.util.SmoothDouble
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.CollisionGetter
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.border.WorldBorder
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import org.apache.commons.lang3.tuple.Pair
import org.joml.Matrix3f
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.function.Consumer
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
        val controller = (vehicle as? OrientationControllable)?.orientationController
        if (controller == null || controller.orientation == null) return Vec3.ZERO

        //Perform ride velocity update
        state.rideVelocity.set(calculateRideSpaceVel(settings, state, vehicle, driver))

        // Convert the local velocity vector into a world vector
        val localVelVec = Vector3f(
            state.rideVelocity.get().x.toFloat(),
            (state.rideVelocity.get().y).toFloat(),
            state.rideVelocity.get().z.toFloat() * -1.0f // Flip the z axis to make this left handed? orr.. right handed? idk, flip it though
        )

        // Unrotate preemptively as this vector gets rotate later down the line in MC logic.
        var worldVelVec = localVelVec.mul(controller.orientation).toVec3d().yRot(vehicle.yRot.toRadians())

        // Add sticking force to stick to walls
        val stickingForce = vehicle.position().subtract(state.attachmentPoint.get()).normalize().scale(0.2)
        worldVelVec = worldVelVec.add(stickingForce)

        return worldVelVec
    }

    private fun calculateRideSpaceVel(
        settings: ClimbSettings,
        state: ClimbState,
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
        val dmSpeed = vehicle.deltaMovement.length()
        val rvSpeed = state.rideVelocity.get().length()
        var newVelocity = state.rideVelocity.get() //.normalize().scale(vehicle.deltaMovement.horizontalDistance())

        /******************************************************
         * Gather the previous velocity and check for horizontal
         * collisions
         *****************************************************/
//        if (dmSpeed > rvSpeed) {
//            // align the velocity vector to be in local vehicle space
//            newVelocity = vehicle.deltaMovement
//            val yawAligned = Matrix3f().rotateY(-vehicle.yRot.toRadians())
//            newVelocity = (newVelocity.toVector3f().mul(yawAligned)).toVec3d()
//        }

//        if (vehicle.horizontalCollision) {
//            newVelocity = newVelocity.normalize().scale(vehicle.deltaMovement.length())
//        }

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

//        /******************************************************
//         * Gravity logic
//         *****************************************************/
//        if (!vehicle.onGround() && state.jumpTicks.get() <= 0) {
//            val gravity = (9.8 / ( 20.0)) * 0.2 * 0.6
//            val terminalVel = 2.0
//            newVelocity = Vec3(newVelocity.x, max(newVelocity.y - gravity, -terminalVel), newVelocity.z)
//        } else if(vehicle.onGround()) {
//            newVelocity = Vec3(newVelocity.x, 0.0, newVelocity.z)
//        }


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
        settings: ClimbSettings,
        state: ClimbState,
        vehicle: PokemonEntity,
        driver: Player,
        deltaTime: Double
    ): Vec3 {
        return Vec3.ZERO
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
        val vehicleController = (vehicle as OrientationControllable).orientationController

//        vehicleController.rotateRoll(vehicleController.roll * -1.0f)
//        vehicleController.rotatePitch(vehicleController.pitch * -1.0f)
//
        val yawAmount = Mth.wrapDegrees(driver.yRot) * deltaTime.toFloat() * 3
        //vehicleController.rotateYaw(yawAmount)
        driver.yRot -= yawAmount

        //Smooth out mouse input.
        val smoothingSpeed = 4
        val xInput = mouseXSmoother.getNewDeltaValue(mouseX * 0.1, deltaTime * smoothingSpeed);
        val yInput = mouseYSmoother.getNewDeltaValue(mouseY * 0.1, deltaTime * smoothingSpeed);


        val turnAmount =  calcRotAmount(settings, state, vehicle, driver)

        val vehiclePosition = vehicle.position()

        val collisionsInclusionRange = 1.0f;
        val collisionsSmoothingRange = 1.25f;

        // Vehicle center
        val vc = vehiclePosition.add(0.0, (vehicle.bbHeight * 0.5f).toDouble(), 0.0)
        val inclusionBox = AABB(vc.x, vc.y, vc.z, vc.x, vc.y, vc.z).inflate(collisionsInclusionRange.toDouble())

        val attachmentPoint: Pair<Vec3?, Vec3?>? = CollisionSmoothingUtil.findClosestPoint(
            { consumer -> this.forEachCollisonBox(vehicle, inclusionBox, consumer) },
            vc,
            state.attachmentNormal.get().scale(-1.0),
            collisionsSmoothingRange,
            1.0f,
            0.001f,
            20,
            0.05f,
            vc
        )

        val vehicleBox: AABB = vehicle.boundingBox


        if(attachmentPoint != null && attachmentPoint.left != null && attachmentPoint.right != null && vehicleController.orientation != null) {
            state.attachmentPoint.set(attachmentPoint.left!!)

            val targetUp = attachmentPoint.right!!.toVector3f().normalize()
            val currentUp = vehicleController.upVector
            // TODO Move this to be a helper function for all ride controllers
            /************************************************************************/
            var end = vc.add(currentUp.toVec3d().scale(25.0))
            var start = vc
            var density = 5.0

            if (!driver.level().isClientSide) return Vec3.ZERO
            var direction = end.subtract(start)
            var length = direction.length()
            var step = direction.normalize().scale(1.0 / density)
            var particleCount = (length * density).toInt()

            for (i in 0 until particleCount) {
                val pos = start.add(step.scale(i.toDouble()))
                driver.level().addParticle(
                    ParticleTypes.CRIT,
                    pos.x, pos.y, pos.z,
                    0.0, 0.0, 0.0
                )
            }
            /************************************************************************/
            /************************************************************************/
//            end = vc.add(targetUp.toVec3d().scale(25.0))
//            start = vc
//            density = 5.0
//
//            if (!driver.level().isClientSide) return Vec3.ZERO
//            direction = end.subtract(start)
//            length = direction.length()
//            step = direction.normalize().scale(1.0 / density)
//            particleCount = (length * density).toInt()
//
//            for (i in 0 until particleCount) {
//                val pos = start.add(step.scale(i.toDouble()))
//                driver.level().addParticle(
//                    ParticleTypes.FLAME,
//                    pos.x, pos.y, pos.z,
//                    0.0, 0.0, 0.0
//                )
//            }
            /************************************************************************/
            val rotationDifference = Quaternionf().rotationTo(currentUp, targetUp)

            // Grab the current rotation as a Quaternion and rotate about the calculated axis of rotation
            val currQuat = Quaternionf().setFromUnnormalized(vehicleController.orientation)
            val destQuat = rotationDifference.mul(currQuat, Quaternionf())
            currQuat.slerp(destQuat, min(deltaTime.toFloat() * 5.0f, 1.0f))
            //rotQuat.rotateAxis(angle * min(deltaTime.toFloat() * 5.0f, 1.0f), axOfRot.normalize())

            val newRenderOrientation = Matrix3f()
            currQuat.get(newRenderOrientation)
            vehicleController.orientation!!.set(newRenderOrientation)
            state.attachmentNormal.set(vehicleController.upVector.normalize().toVec3d())
        }

        //yaw, pitch, roll
        return Vec3(yawAmount.toDouble(), 0.0, 0.0)
    }

    private fun forEachCollisonBox(vehicle: PokemonEntity, aabb: AABB, action: Shapes.DoubleLineConsumer?) {
        val minChunkX = ((Mth.floor(aabb.minX - 1.0E-7) - 1) shr 4)
        val maxChunkX = ((Mth.floor(aabb.maxX + 1.0E-7) + 1) shr 4)
        val minChunkZ = ((Mth.floor(aabb.minZ - 1.0E-7) - 1) shr 4)
        val maxChunkZ = ((Mth.floor(aabb.maxZ + 1.0E-7) + 1) shr 4)

        val width = maxChunkX - minChunkX + 1
        val depth = maxChunkZ - minChunkZ + 1

        val blockReaderCache = arrayOfNulls<BlockGetter>(width * depth)

        val collisionReader: CollisionGetter = vehicle.level()

        for (cx in minChunkX..maxChunkX) {
            for (cz in minChunkZ..maxChunkZ) {
                blockReaderCache[(cx - minChunkX) + (cz - minChunkZ) * width] =
                    collisionReader.getChunkForCollisions(cx, cz)
            }
        }

        val cachedCollisionReader: CollisionGetter = object : CollisionGetter {
            override fun getHeight(): Int {
                return vehicle.level().getHeight()
            }

            override fun getMinBuildHeight(): Int {
                return vehicle.level().getMinBuildHeight()
            }

            override fun getBlockEntity(pos: BlockPos?): BlockEntity? {
                return collisionReader.getBlockEntity(pos)
            }

            override fun getBlockState(pos: BlockPos?): BlockState {
                return collisionReader.getBlockState(pos)
            }

            override fun getFluidState(pos: BlockPos?): FluidState {
                return collisionReader.getFluidState(pos)
            }

            override fun getWorldBorder(): WorldBorder {
                return collisionReader.getWorldBorder()
            }


            override fun getEntityCollisions(entity: Entity?, aabb: AABB?): MutableList<VoxelShape?> {
                return collisionReader.getEntityCollisions(entity, aabb)
            }

            override fun getChunkForCollisions(chunkX: Int, chunkZ: Int): BlockGetter? {
                return blockReaderCache[(chunkX - minChunkX) + (chunkZ - minChunkZ) * width]
            }
        }

        //todo:figure out;
        val shapes = cachedCollisionReader.getBlockCollisions(vehicle, aabb)

        //		StreamSupport.stream(new CollisionSpliterator(cachedCollisionReader, this, aabb, this::canClimbOnBlock), false);
        shapes.forEach(Consumer { shape: VoxelShape? -> shape!!.forAllBoxes(action) })
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

    override fun useRidingAltPose(
        settings: ClimbSettings,
        state: ClimbState,
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
    var attachmentNormal = ridingState(Vec3(0.0, 1.0, 0.0), Side.CLIENT)
    var attachmentPoint = ridingState(Vec3(0.0, 1.0, 0.0), Side.CLIENT)

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
    }

    override fun copy() = ClimbState().also {
        it.rideVelocity.set(this.rideVelocity.get(), forced = true)
        it.stamina.set(this.stamina.get(), forced = true)
        it.sprinting.set(this.sprinting.get(), forced = true)
        it.inAir.set(this.inAir.get(), forced = true)
        it.jumpTicks.set(this.jumpTicks.get(), forced = true)
        it.attachmentNormal.set(this.attachmentNormal.get(), forced = true)
        it.attachmentPoint.set(this.attachmentNormal.get(), forced = true)
    }

    override fun shouldSync(previous: RidingBehaviourState): Boolean {
        if (previous !is ClimbState) return false
        if (previous.sprinting.get() != sprinting.get()) return true
        if (previous.inAir.get() != inAir.get()) return true
        return super.shouldSync(previous)
    }
}

// Adapted from nyfaria's code
//private class BoxConsumer private constructor(private val p: Vec3, private val boxScale: Float) :
//    Shapes.DoubleLineConsumer {
//    private var capacity = 16
//    private var size = 0
//
//    private var erx = FloatArray(this.capacity)
//    private var ery = FloatArray(this.capacity)
//    private var erz = FloatArray(this.capacity)
//
//    private var ecx = FloatArray(this.capacity)
//    private var ecy = FloatArray(this.capacity)
//    private var ecz = FloatArray(this.capacity)
//
//    override fun consume(minX: Double, minY: Double, minZ: Double, maxX: Double, maxY: Double, maxZ: Double) {
//        if (this.size == this.capacity) {
//            this.capacity = max(
//                min(this.capacity.toLong() + (this.capacity shr 1), Arrays.MAX_ARRAY_SIZE.toLong()),
//                this.capacity.toLong()
//            ).toInt()
//            this.erx = FloatArrays.forceCapacity(this.erx, this.capacity, this.size)
//            this.ery = FloatArrays.forceCapacity(this.ery, this.capacity, this.size)
//            this.erz = FloatArrays.forceCapacity(this.erz, this.capacity, this.size)
//            this.ecx = FloatArrays.forceCapacity(this.ecx, this.capacity, this.size)
//            this.ecy = FloatArrays.forceCapacity(this.ecy, this.capacity, this.size)
//            this.ecz = FloatArrays.forceCapacity(this.ecz, this.capacity, this.size)
//        }
//
//        this.erx[this.size] = 1.0f / ((maxX - minX).toFloat() / 2 * this.boxScale)
//        this.ery[this.size] = 1.0f / ((maxY - minY).toFloat() / 2 * this.boxScale)
//        this.erz[this.size] = 1.0f / ((maxZ - minZ).toFloat() / 2 * this.boxScale)
//
//        this.ecx[this.size] = ((minX + maxX) / 2 - this.p.x).toFloat()
//        this.ecy[this.size] = ((minY + maxY) / 2 - this.p.y).toFloat()
//        this.ecz[this.size] = ((minZ + maxZ) / 2 - this.p.z).toFloat()
//
//        this.size++
//    }
//}


object CollisionSmoothingUtil {
    private fun invSqrt(x: Float): Float {
        var x = x
        val xhalf = 0.5f * x
        var i = java.lang.Float.floatToIntBits(x)
        i = 0x5f3759df - (i shr 1)
        x = java.lang.Float.intBitsToFloat(i)
        x *= (1.5f - xhalf * x * x)
        return x
    }

    private fun sampleSdf(
        erx: FloatArray,
        ery: FloatArray,
        erz: FloatArray,
        ecx: FloatArray,
        ecy: FloatArray,
        ecz: FloatArray,
        count: Int,
        px: Float,
        py: Float,
        pz: Float,
        pnx: Float,
        pny: Float,
        pnz: Float,
        x: Float,
        y: Float,
        z: Float,
        smoothingRange: Float,
        invSmoothingRange: Float
    ): Float {
        var sdfDst = 0.0f

        val planeDst = pnx * (x - px) + pny * (y - py) + pnz * (z - pz)

        for (i in 0..<count) {
            val rsx = x - ecx[i]
            val rsy = y - ecy[i]
            val rsz = z - ecz[i]

            //Ellipsoid SDF - https://www.iquilezles.org/www/articles/ellipsoids/ellipsoids.htm
            val prx = rsx * erx[i]
            val pry = rsy * ery[i]
            val prz = rsz * erz[i]
            //float k1 = MathHelper.sqrt(prx * prx + pry * pry + prz * prz);
            //float ellipsoidDst = (k1 - 1.0f) * Math.min(Math.min(erx[i], ery[i]), erz[i]);
            val k1 = invSqrt(prx * prx + pry * pry + prz * prz)
            var ellipsoidDst = Mth.sqrt(rsx * rsx + rsy * rsy + rsz * rsz) * (1.0f - 1.0f * k1)

            /*float prx2 = rsx * erx[i] * erx[i];
			float pry2 = rsy * ery[i] * ery[i];
			float prz2 = rsz * erz[i] * erz[i];
			float k2 = invSqrt(prx2 * prx2 + pry2 * pry2 + prz2 * prz2);
			float ellipsoidDst = k1 * (k1 - 1.0f) * k2;*/

            //Smooth intersection - https://iquilezles.org/www/articles/distfunctions/distfunctions.htm
            var h = Mth.clamp(0.5f - 0.5f * (ellipsoidDst + planeDst) * invSmoothingRange, 0.0f, 1.0f)
            ellipsoidDst = ellipsoidDst + (-planeDst - ellipsoidDst) * h + smoothingRange * h * (1.0f - h)

            if (i == 0) {
                sdfDst = ellipsoidDst
            } else {
                //Smooth min - https://www.iquilezles.org/www/articles/smin/smin.htm
                h = Mth.clamp(0.5f + 0.5f * (ellipsoidDst - sdfDst) * invSmoothingRange, 0.0f, 1.0f)
                sdfDst = ellipsoidDst + (sdfDst - ellipsoidDst) * h - smoothingRange * h * (1.0f - h)
            }
        }

        //Smooth intersection - https://iquilezles.org/www/articles/distfunctions/distfunctions.htm
        val h = Mth.clamp(0.5f - 0.5f * (sdfDst + planeDst) * invSmoothingRange, 0.0f, 1.0f)
        sdfDst = sdfDst + (-planeDst - sdfDst) * h + smoothingRange * h * (1.0f - h)

        return sdfDst
    }

    fun findClosestPoint(
        consumer: Consumer<Shapes.DoubleLineConsumer?>,
        pp: Vec3,
        pn: Vec3,
        smoothingRange: Float,
        boxScale: Float,
        dx: Float,
        iters: Int,
        threshold: Float,
        p: Vec3
    ): Pair<Vec3?, Vec3?>? {
        val boxConsumer = BoxConsumer(p, boxScale)

        consumer.accept(boxConsumer)

        if (boxConsumer.size == 0) {
            return null
        }

        return findClosestPoint(
            boxConsumer.erx,
            boxConsumer.ery,
            boxConsumer.erz,
            boxConsumer.ecx,
            boxConsumer.ecy,
            boxConsumer.ecz,
            boxConsumer.size,
            pp,
            pn,
            smoothingRange,
            boxScale,
            dx,
            iters,
            threshold,
            p
        )
    }

    fun findClosestPoint(
        boxes: MutableList<AABB>,
        pp: Vec3,
        pn: Vec3,
        smoothingRange: Float,
        boxScale: Float,
        dx: Float,
        iters: Int,
        threshold: Float,
        p: Vec3
    ): Pair<Vec3?, Vec3?>? {
        if (boxes.isEmpty()) {
            return null
        }

        val erx = FloatArray(boxes.size)
        val ery = FloatArray(boxes.size)
        val erz = FloatArray(boxes.size)

        val ecx = FloatArray(boxes.size)
        val ecy = FloatArray(boxes.size)
        val ecz = FloatArray(boxes.size)

        var i = 0
        for (box in boxes) {
            erx[i] = 1.0f / ((box.maxX - box.minX).toFloat() / 2 * boxScale)
            ery[i] = 1.0f / ((box.maxY - box.minY).toFloat() / 2 * boxScale)
            erz[i] = 1.0f / ((box.maxZ - box.minZ).toFloat() / 2 * boxScale)

            ecx[i] = ((box.minX + box.maxX) / 2 - p.x).toFloat()
            ecy[i] = ((box.minY + box.maxY) / 2 - p.y).toFloat()
            ecz[i] = ((box.minZ + box.maxZ) / 2 - p.z).toFloat()

            i++
        }

        return findClosestPoint(
            erx,
            ery,
            erz,
            ecx,
            ecy,
            ecz,
            boxes.size,
            pp,
            pn,
            smoothingRange,
            boxScale,
            dx,
            iters,
            threshold,
            p
        )
    }

    private fun findClosestPoint(
        erx: FloatArray,
        ery: FloatArray,
        erz: FloatArray,
        ecx: FloatArray,
        ecy: FloatArray,
        ecz: FloatArray,
        count: Int,
        pp: Vec3,
        pn: Vec3,
        smoothingRange: Float,
        boxScale: Float,
        dx: Float,
        iters: Int,
        threshold: Float,
        p: Vec3
    ): Pair<Vec3?, Vec3?>? {
        val halfThreshold = threshold * 0.5f

        val plx = (pp.x - p.x).toFloat()
        val ply = (pp.y - p.y).toFloat()
        val plz = (pp.z - p.z).toFloat()
        val pnx = pn.x.toFloat()
        val pny = pn.y.toFloat()
        val pnz = pn.z.toFloat()

        var px = 0.0f
        var py = 0.0f
        var pz = 0.0f

        val invSmoothingRange = 1.0f / smoothingRange

        for (j in 0..<iters) {
            val dst = sampleSdf(
                erx,
                ery,
                erz,
                ecx,
                ecy,
                ecz,
                count,
                plx,
                ply,
                plz,
                pnx,
                pny,
                pnz,
                px,
                py,
                pz,
                smoothingRange,
                invSmoothingRange
            )

            val fx1 = sampleSdf(
                erx,
                ery,
                erz,
                ecx,
                ecy,
                ecz,
                count,
                plx,
                ply,
                plz,
                pnx,
                pny,
                pnz,
                px + dx,
                py,
                pz,
                smoothingRange,
                invSmoothingRange
            )
            val fy1 = sampleSdf(
                erx,
                ery,
                erz,
                ecx,
                ecy,
                ecz,
                count,
                plx,
                ply,
                plz,
                pnx,
                pny,
                pnz,
                px,
                py + dx,
                pz,
                smoothingRange,
                invSmoothingRange
            )
            val fz1 = sampleSdf(
                erx,
                ery,
                erz,
                ecx,
                ecy,
                ecz,
                count,
                plx,
                ply,
                plz,
                pnx,
                pny,
                pnz,
                px,
                py,
                pz + dx,
                smoothingRange,
                invSmoothingRange
            )

            var gx = dst - fx1
            var gy = dst - fy1
            var gz = dst - fz1
            val m = invSqrt(gx * gx + gy * gy + gz * gz)
            gx *= m
            gy *= m
            gz *= m

            if (java.lang.Float.isNaN(gx) || java.lang.Float.isNaN(gy) || java.lang.Float.isNaN(gz) || java.lang.Double.isNaN(
                    px.toDouble()
                ) || java.lang.Double.isNaN(py.toDouble()) || java.lang.Double.isNaN(pz.toDouble())
            ) {
                return null
            }

            val absDst = abs(dst)

            val step = if (absDst >= halfThreshold) dst else sign(dst) * halfThreshold

            px += gx * step
            py += gy * step
            pz += gz * step

            if (absDst < threshold) {
                return Pair.of<Vec3?, Vec3?>(
                    Vec3(p.x + px, p.y + py, p.z + pz),
                    Vec3(-gx.toDouble(), -gy.toDouble(), -gz.toDouble()).normalize()
                )
            }
        }

        return null
    }

    private class BoxConsumer(private val p: Vec3, private val boxScale: Float) : Shapes.DoubleLineConsumer {
        private var capacity = 16
        public var size = 0

        public var erx = FloatArray(this.capacity)
        public var ery = FloatArray(this.capacity)
        public var erz = FloatArray(this.capacity)

        public var ecx = FloatArray(this.capacity)
        public var ecy = FloatArray(this.capacity)
        public var ecz = FloatArray(this.capacity)

        override fun consume(minX: Double, minY: Double, minZ: Double, maxX: Double, maxY: Double, maxZ: Double) {
            if (this.size == this.capacity) {
                this.capacity = max(
                    min(this.capacity.toLong() + (this.capacity shr 1), Arrays.MAX_ARRAY_SIZE.toLong()),
                    this.capacity.toLong()
                ).toInt()
                this.erx = FloatArrays.forceCapacity(this.erx, this.capacity, this.size)
                this.ery = FloatArrays.forceCapacity(this.ery, this.capacity, this.size)
                this.erz = FloatArrays.forceCapacity(this.erz, this.capacity, this.size)
                this.ecx = FloatArrays.forceCapacity(this.ecx, this.capacity, this.size)
                this.ecy = FloatArrays.forceCapacity(this.ecy, this.capacity, this.size)
                this.ecz = FloatArrays.forceCapacity(this.ecz, this.capacity, this.size)
            }

            this.erx[this.size] = 1.0f / ((maxX - minX).toFloat() / 2 * this.boxScale)
            this.ery[this.size] = 1.0f / ((maxY - minY).toFloat() / 2 * this.boxScale)
            this.erz[this.size] = 1.0f / ((maxZ - minZ).toFloat() / 2 * this.boxScale)

            this.ecx[this.size] = ((minX + maxX) / 2 - this.p.x).toFloat()
            this.ecy[this.size] = ((minY + maxY) / 2 - this.p.y).toFloat()
            this.ecz[this.size] = ((minZ + maxZ) / 2 - this.p.z).toFloat()

            this.size++
        }
    }
}


