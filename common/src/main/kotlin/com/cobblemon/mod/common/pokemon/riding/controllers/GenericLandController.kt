/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.riding.controllers

import com.bedrockk.molang.Expression
import com.bedrockk.molang.runtime.MoLangMath.lerp
import com.bedrockk.molang.runtime.value.DoubleValue
import com.cobblemon.mod.common.api.riding.controller.RideController
import com.cobblemon.mod.common.api.riding.controller.posing.PoseOption
import com.cobblemon.mod.common.api.riding.controller.posing.PoseProvider
import com.cobblemon.mod.common.api.riding.controller.posing.RidingAltPose
import com.cobblemon.mod.common.api.riding.stats.RidingStat
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.riding.states.GenericLandState
import com.cobblemon.mod.common.util.*
import net.minecraft.client.Minecraft
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import net.minecraft.util.SmoothDouble
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.Shapes
import kotlin.math.*

class GenericLandController : RideController {
    companion object {
        val KEY: ResourceLocation = cobblemonResource("land/generic")
    }

//    private var previousVelocity = Vec3d.ZERO

    var canJump = "true".asExpression()
        private set
    var jumpVector = listOf("0".asExpression(), "0.3".asExpression(), "0".asExpression())
        private set
    var speed = "1.0".asExpression()
        private set
    var driveFactor = "1.0".asExpression()
        private set
    var reverseDriveFactor = "0.25".asExpression()
        private set
    var strafeFactor = "0.2".asExpression()
        private set
    var sprintMod = "1.5".asExpression()
        private set

    private val statExpressions: Map<RidingStat, Expression> = mapOf(
        //Degrees per second
        RidingStat.SKILL to "q.get_ride_stats('SKILL', 'LAND', 135.0, 90.0)".asExpression(),
        RidingStat.SPEED to "q.get_ride_stats('SPEED', 'LAND', 0.65, 0.3)".asExpression(),
        // In seconds to max speed
        RidingStat.ACCELERATION to "q.get_ride_stats('ACCELERATION', 'LAND', (1.0 / (20.0 * 0.5)), (1.0 / (20.0 * 5.0)))".asExpression(),
        // In Seconds
        RidingStat.STAMINA to "q.get_ride_stats('STAMINA', 'LAND', 30.0, 10.0)".asExpression(),
        // Block Jump Height
        RidingStat.JUMP to "q.get_ride_stats('JUMP', 'LAND', 10.0, 1.0)".asExpression()
    )

    @Transient
    override val key: ResourceLocation = KEY
    @Transient
    override val poseProvider: PoseProvider = PoseProvider(PoseType.STAND).with(PoseOption(PoseType.WALK) { it.deltaMovement.horizontalDistance() > 0.1 })
    @Transient
    override val condition: (PokemonEntity) -> Boolean = { entity ->
        // Are there any blocks under the mon that aren't air or fluid
        // Cant just check one block since some mons may be more than one block big
        // This should be changed so that the any predicate is only ran on blocks under the mon
        Shapes.create(entity.boundingBox).blockPositionsAsListRounded().any {
            //Need to check other fluids
            if (entity.isInWater || entity.isUnderWater) {
                return@any false
            }
            //This might not actually work, depending on what the yPos actually is. yPos of the middle of the entity? the feet?
            if (it.y.toDouble() == (entity.position().y)) {
                val blockState = entity.level().getBlockState(it.below())
                return@any !blockState.isAir && blockState.fluidState.isEmpty
            }
            true
        }
    }

    override fun getStat(entity: PokemonEntity, stat: RidingStat): Double {
        val expr = statExpressions[stat] ?: return 0.0
        return getRuntime(entity).resolveDouble(expr)
    }

    override fun speed(entity: PokemonEntity, driver: Player): Float {
        val state = getState(entity, ::GenericLandState)
        return state.rideVel.length().toFloat()
    }

    override fun updatePassengerRotation(entity: PokemonEntity, driver: LivingEntity) {

        //Take the inverse so that it cancels out how
        //much the entity rotates.
        val turnAmount = calcRotAmount(entity, driver)

        val topSpeed = this.getStat(entity, RidingStat.SPEED)
        val state = getState(entity, ::GenericLandState)
        val maxYawDiff = 90.0f

        //Normalize the current rotation diff
        val rotMod = Mth.wrapDegrees(driver.yRot - entity.yRot) / maxYawDiff

        val rotAmount = 10.0f

        //Take the inverse so that you turn more at higher speeds
        val normSpeed = 1.0f - 0.5f*normalizeVal(state.rideVel.length(), 0.0, topSpeed).toFloat()

        //driver.yRot += (entity.riding.deltaRotation.y - turnAmount)
        //driver.setYHeadRot(driver.yHeadRot + (entity.riding.deltaRotation.y) - turnAmount)
    }

    override fun clampPassengerRotation(entity: PokemonEntity, driver: LivingEntity) {
        val f = Mth.wrapDegrees(driver.yRot - entity.yRot)
        val runtime = getRuntime(entity)
        val lookYawLimit = 90.0f
        val g = Mth.clamp(f, -lookYawLimit, lookYawLimit)
        driver.yRotO += g - f
        driver.yRot = driver.yRot + g - f
        driver.setYHeadRot(driver.yRot)
    }

    override fun rotation(entity: PokemonEntity, driver: LivingEntity): Vec2 {

        val turnAmount =  calcRotAmount(entity, driver)

        return Vec2(driver.xRot, entity.yRot + turnAmount )
    }

    override fun velocity(entity: PokemonEntity, driver: Player, input: Vec3): Vec3 {
        val currSpeed = 1.0

        val runtime = getRuntime(entity)
        val driveFactor = runtime.resolveFloat(this.driveFactor)
        val strafeFactor = runtime.resolveFloat(this.strafeFactor)
        val f = driver.xxa * strafeFactor
        var g = driver.zza * driveFactor
        if (g <= 0.0f) {
            g *= runtime.resolveFloat(this.reverseDriveFactor)
        }
        val gravity = -1.0

        val state = getState(entity, ::GenericLandState)


        calculateRideSpaceVel(entity, driver, state)

        //Jump the thang!
        if (Minecraft.getInstance().options.keyJump.isDown() && entity.onGround()) {
            state.rideVel = Vec3(state.rideVel.x, 1.0, state.rideVel.z)
        }

        //This is cheap.
        //Also make it stop quicker the slower it is.
        val maxSpeed = 1.0
        //state.currVel = state.currVel.lerp( Vec3(0.0, state.currVel.y, 0.0), 1.0/20.0 )


        //entity.deltaMovement = entity.deltaMovement.lerp(velocity, 1.0)
        //state.currVel = state.currVel.add( f.toDouble() / 20.0, gravity / 20.0 , g.toDouble() / 20.0)

        println("Z speed: %.2f".format(state.rideVel.z) + "->" + "Z speed: %.2f".format(entity.deltaMovement.horizontalDistance()))
        return state.rideVel
    }

    override fun shouldRoll(entity: PokemonEntity): Boolean {
        return false
    }

    override fun rotationOnMouseXY(
        entity: PokemonEntity,
        driver: Player,
        yMouse: Double,
        xMouse: Double,
        yMouseSmoother: SmoothDouble,
        xMouseSmoother: SmoothDouble,
        sensitivity: Double,
        deltaTime: Double
    ): Vec3 {

        //Smooth out mouse input.
        val smoothingSpeed = 4
        val xInput = xMouseSmoother.getNewDeltaValue(xMouse * 0.1, deltaTime * smoothingSpeed);
        val yInput = yMouseSmoother.getNewDeltaValue(yMouse * 0.1, deltaTime * smoothingSpeed);

        //yaw, pitch, roll
        return Vec3(0.0, yInput, xInput)
    }

    override fun canJump(entity: PokemonEntity, driver: Player) = false

    //TODO: bring in stamina stats
    override fun setRideBar(entity: PokemonEntity, driver: Player): Float {

        //Retrieve stamina from state and tick up at a rate of 0.1 a second
        val state = getState(entity, ::GenericLandState)
        val staminaGain = (1.0 / 20.0)*0.1
        state.stamina = min(state.stamina + staminaGain,1.0).toFloat()
        return (state.stamina / 1.0f)
    }

    override fun jumpForce(entity: PokemonEntity, driver: Player, jumpStrength: Int): Vec3 {
        val runtime = getRuntime(entity)
        runtime.environment.query.addFunction("jump_strength") { DoubleValue(jumpStrength.toDouble()) }
        val jumpVector = jumpVector.map { runtime.resolveFloat(it) }
        return Vec3(jumpVector[0].toDouble(), jumpVector[1].toDouble(), jumpVector[2].toDouble())
    }

    override fun gravity(entity: PokemonEntity, regularGravity: Double): Double {
        return 0.0
    }

    override fun rideFovMult(entity: PokemonEntity, driver: Player): Float {
        val topSpeed = this.getStat(entity, RidingStat.SPEED)
        val sprintTopSpeed = topSpeed * getRuntime(entity).resolveDouble(sprintMod) //TODO: make it so sprint mod is global and likely molang json configurable
        val state = getState(entity, ::GenericLandState)

        //Must I ensure that topspeed is greater than minimum?
        val normalizedSprintSpeed = normalizeVal(state.rideVel.z, topSpeed, sprintTopSpeed)

        //Only ever want the fov change to be a max of 0.2 and for it to have non linear scaling.
        val fovChange = 1.0f + normalizedSprintSpeed.pow(2).toFloat() * 0.2f
        return fovChange

    }

    override fun getActiveRidingAltPose(entity: PokemonEntity, driver: Player): RidingAltPose {
        //TODO: Should this be converted to a sprint flag?
        val isSprinting = Minecraft.getInstance().options.keySprint.isDown
        val pose = if (isSprinting) RidingAltPose.SPRINTING else RidingAltPose.NONE
        return pose
    }

    override fun inertia(entity: PokemonEntity ): Double = 0.7

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        super.encode(buffer)
        buffer.writeString(this.speed.getString())
        buffer.writeString(this.canJump.getString())
        buffer.writeString(this.jumpVector[0].getString())
        buffer.writeString(this.jumpVector[1].getString())
        buffer.writeString(this.jumpVector[2].getString())
        buffer.writeString(this.driveFactor.getString())
        buffer.writeString(this.reverseDriveFactor.getString())
        buffer.writeString(this.strafeFactor.getString())
    }

    override fun decode(buffer: RegistryFriendlyByteBuf) {
        this.speed = buffer.readString().asExpression()
        this.canJump = buffer.readString().asExpression()
        this.jumpVector = listOf(
            buffer.readString().asExpression(),
            buffer.readString().asExpression(),
            buffer.readString().asExpression()
        )
        this.driveFactor = buffer.readString().asExpression()
        this.reverseDriveFactor = buffer.readString().asExpression()
        this.strafeFactor = buffer.readString().asExpression()
    }

    /*
    *  Normalizes the given value between a min and a max.
    *  The result is clamped between 0.0 and 1.0, where 0.0 represents x is at or below min
    *  and 1.0 represents x is at or above it.
    */
    fun normalizeVal(x: Double, min: Double, max: Double): Double {
        require(max > min) { "max must be greater than min" }
        return ((x - min) / (max - min)).coerceIn(0.0, 1.0)
    }

    /*
    *  Calculates the rotation amount given the difference between
    *  the current player y rot and entity y rot. This gives the
    *  affect of influencing a rides rotation through the mouse
    *  without it being instant.
    */
    fun calcRotAmount(entity: PokemonEntity, driver: LivingEntity): Float {
        val state = getState(entity, ::GenericLandState)
        val topSpeed = this.getStat(entity, RidingStat.SPEED)
        //In degrees per second
        val handling = this.getStat(entity, RidingStat.SKILL)
        val maxYawDiff = 90.0f

        //Normalize the current rotation diff
        val rotDiff = Mth.wrapDegrees(driver.yRot - entity.yRot)
        val rotDiffNorm = rotDiff / maxYawDiff

        //Take the square root so that the ride levels out quicker when at lower differences between entity
        //y and driver y
        //This influences the speed of the turn based on how far in one direction you're looking
        val rotDiffMod = (sqrt(abs(rotDiffNorm)) * rotDiffNorm.sign)

        //Take the inverse so that you turn more at higher speeds
        val normSpeed = 1.0f // = 1.0f - 0.5f*normalizeVal(state.rideVel.length(), 0.0, topSpeed).toFloat()

        //Ensure you only ever rotate as much difference as there is between the angles.
        val turnSpeed = handling.toFloat() / 20.0f  * rotDiffMod * normSpeed
        val rotAmount = turnSpeed.coerceIn(-abs(rotDiff), abs(rotDiff))

        return rotAmount
    }

    fun calculateRideSpaceVel( entity: PokemonEntity, driver: Player, state: GenericLandState){

        val topSpeed = this.getStat(entity, RidingStat.SPEED)
        val accel = this.getStat(entity, RidingStat.ACCELERATION)
        val speed = state.rideVel.length()

        val reverseMod = 1.0 / 3.0
        val isSprinting = Minecraft.getInstance().options.keySprint.isDown
        val sprintMod = getRuntime(entity).resolveDouble(sprintMod)

        //speed up and slow down based on input
        if ((driver.zza != 0.0f && state.rideVel.z < topSpeed && !isSprinting) ||
            (driver.zza != 0.0f && state.rideVel.z < topSpeed * sprintMod && isSprinting)) {
            //make sure it can't exceed top speed
            val forwardInput = when {
                driver.zza > 0 && state.rideVel.z > topSpeed && !isSprinting -> 0.0
                driver.zza < 0 && state.rideVel.z < (-topSpeed * reverseMod) && !isSprinting -> 0.0
                driver.zza > 0 && state.rideVel.z > topSpeed * sprintMod && isSprinting -> 0.0
                driver.zza < 0 && state.rideVel.z < (-topSpeed * sprintMod * reverseMod) && isSprinting -> 0.0
                else -> driver.zza.sign
            }

            state.rideVel = Vec3(
                state.rideVel.x,
                state.rideVel.y,
                (state.rideVel.z + (accel * forwardInput.toDouble())))
        }
        //Otherwise if there is no input then apply deceleration
        else if (driver.zza == 0.0f) {
            //Make deceleration constant no matter the stats
            val decelRate = 1.0 / 20.0
            state.rideVel = Vec3(
                state.rideVel.x,
                state.rideVel.y,
                lerp( state.rideVel.z, 0.0, decelRate )
            )
        }

        //If on the ground then stop the increase of rideVel's gravity velocity
        if (!entity.onGround()) {
            state.rideVel = Vec3(state.rideVel.x, max(state.rideVel.y - 0.1, -1.0) , state.rideVel.z)
        } else {
            state.rideVel = Vec3(state.rideVel.x, 0.0 , state.rideVel.z)
        }

    }
}