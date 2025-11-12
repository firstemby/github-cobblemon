/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.camera

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.OrientationControllable
import com.cobblemon.mod.common.client.MountedPokemonAnimationRenderController.setup
import com.cobblemon.mod.common.client.entity.PokemonClientDelegate
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository.getPoser
import com.cobblemon.mod.common.duck.RidePassenger
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.mixin.accessor.CameraAccessor
import com.cobblemon.mod.common.util.math.geometry.toRadians
import net.minecraft.client.Camera
import net.minecraft.client.CameraType
import net.minecraft.client.Minecraft
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import org.joml.Vector3f

object MountedCameraRenderer
{
    fun getCameraPosition(
        instance: Camera,
        driver: Entity,
        vehicle: Entity,
        x: Double,
        y: Double,
        z: Double,
        thirdPersonReverse: Boolean,
        eyeHeightOld: Double,
        eyeHeight: Double
    ): Vec3? {
        if (vehicle !is PokemonEntity) {
            return null
        }
        val delegate = vehicle.delegate as? PokemonClientDelegate ?: return null

        val entityPos = Vec3(
            Mth.lerp(instance.partialTickTime.toDouble(), vehicle.xOld, vehicle.x),
            Mth.lerp(instance.partialTickTime.toDouble(), vehicle.yOld, vehicle.y),
            Mth.lerp(instance.partialTickTime.toDouble(), vehicle.zOld, vehicle.z)
        )
        setup(vehicle, instance.partialTickTime)
        val rollable = vehicle as OrientationControllable
        val vehicleController = rollable.getOrientationController()

        val model = getPoser(vehicle.pokemon.species.resourceIdentifier, FloatingState())

        val locatorName = delegate.getSeatLocator(driver)
        val locator = delegate.locatorStates[locatorName] ?: return null

        val currEyeHeight: Double = Mth.lerp(instance.partialTickTime.toDouble(), eyeHeight, eyeHeightOld)
        val offset = Vector3f(0f, (currEyeHeight - (driver.bbHeight / 2)).toFloat(), 0f)
        val eyeOffset = Vector3f(0f, (currEyeHeight - (driver.bbHeight / 2)).toFloat(), 0f)

        // Get additional offset from poser and add to the eyeHeight offset
        val shouldFlip = !(vehicleController.active && vehicleController.orientation != null) // Do not flip the offset for 3rd person reverse unless we are using normal mc camera rotation.
        val isFirstPerson = Minecraft.getInstance().options.getCameraType() == CameraType.FIRST_PERSON

        val rotMatrix = if (vehicleController.isActive()) vehicleController.getRenderOrientation(instance.partialTickTime)
            else Quaternionf()
                .rotateY((180f - Mth.lerp(instance.partialTickTime, vehicle.yBodyRotO, vehicle.yBodyRot).toRadians()))
                .rotateX(-1 * Mth.lerp(instance.partialTickTime, vehicle.xRotO, vehicle.xRot).toRadians())

        eyeOffset.add(getFirstPersonOffset(model, locatorName))
        if (isFirstPerson) {
            offset.add(
                getFirstPersonOffset(model, locatorName)
            )
        } else if (Cobblemon.config.thirdPersonViewBobbing) {
            offset.add(
                getThirdPersonOffset(thirdPersonReverse, model.thirdPersonCameraOffset, locatorName, shouldFlip)
            )
        } else {
            offset.add(
                getThirdPersonOffset(thirdPersonReverse, model.thirdPersonCameraOffsetNoViewBobbing, locatorName, shouldFlip)
            )
        }

        // Grab the pokemon's orientation to use as the offset rotation.
        rotMatrix.transform(offset)
        rotMatrix.transform(eyeOffset)

        val eyeLocatorOffset = Vec3(locator.matrix.getTranslation(Vector3f()))
        val eyePos = eyeLocatorOffset.add(entityPos).toVector3f()

        // Get the camera position. If 3rd person viewbobbing is enabled or the player is
        // in first person then base the camera position off the seat locator offset
        val pos = if (!isFirstPerson || Cobblemon.config.thirdPersonViewBobbing) {
                val locatorOffset = Vec3(locator.matrix.getTranslation(Vector3f()))
                locatorOffset.add(entityPos).toVector3f()
            } else {
                val pokemonCenter = Vector3f(0f, driver.bbHeight/2, 0f)

                val pivotOffsets = model.thirdPersonPivotOffset
                val pivot = Vector3f(pokemonCenter)
                val locatorName = delegate.getSeatLocator(driver)
                if (pivotOffsets.containsKey(locatorName)) {
                    val orientation = vehicleController.getRenderOrientation(instance.partialTickTime)
                    pivot.add(orientation.transform(pivotOffsets[locatorName]!!.toVector3f()))
                }
                entityPos.toVector3f().add(pivot)
            }

        val offsetDistance = offset.length()
        val offsetDirection = offset.mul(1 / offsetDistance)

        val eyeOffsetDistance = eyeOffset.length()
        val eyeOffsetDirection = eyeOffset.mul(1 / eyeOffsetDistance)

        // Use getMaxZoom to calculate clipping
        val maxZoom: Float = getMaxZoom(offsetDistance, offsetDirection, pos, instance)
        val eyeMaxZoom: Float = getMaxZoom(eyeOffsetDistance, eyeOffsetDirection, eyePos, instance)

        val playerRotater = driver as RidePassenger
        playerRotater.`cobblemon$setRideEyePos`(Vec3(eyeOffsetDirection.mul(eyeMaxZoom).add(eyePos)))
        return Vec3(offsetDirection.mul(maxZoom).add(pos))
    }

    fun resetDriverRotations(
        instance: Camera,
        driver: Entity,
    ) {
        val eulerAngs = Quaternionf(instance.rotation()).getEulerAnglesYXZ(Vector3f())
        val playerRotater = driver as RidePassenger
        // Backticks because kotlin explodes otherwise due to syntax
        playerRotater.`cobblemon$setRideXRot`(-eulerAngs.x() * Mth.RAD_TO_DEG)
        playerRotater.`cobblemon$setRideYRot`(180.0f - (eulerAngs.y() * Mth.RAD_TO_DEG))
    }


    private fun getThirdPersonOffset(
        thirdPersonReverse: Boolean,
        cameraOffsets: Map<String, Vec3>,
        locatorName: String,
        shouldFlip: Boolean = true
    ): Vector3f {
        val offset = if (thirdPersonReverse && cameraOffsets.containsKey(locatorName + "_reverse")) {
            cameraOffsets[locatorName + "_reverse"]!!.toVector3f()
        } else if (cameraOffsets.containsKey(locatorName)) {
            cameraOffsets[locatorName]!!.toVector3f()
        } else {
            Vector3f(0f, 2f, 4f)
        }

        // Don't need to account for this since orientation is derived from camera rotation and that z is already flipped.
        // Flip only when not on a rollable.
        if (thirdPersonReverse && shouldFlip) offset.z *= -1
        return offset
    }

    private fun getFirstPersonOffset(model: PosableModel, locatorName: String): Vector3f {
        val cameraOffsets = model.firstPersonCameraOffset

        return if (cameraOffsets.containsKey(locatorName)) {
            cameraOffsets[locatorName]!!.toVector3f()
        } else {
            Vector3f(0f, 0f, 0f)
        }
    }


    private fun getMaxZoom(maxZoom: Float, directionVector: Vector3f, positionVector: Vector3f, instance: Camera): Float {
        var maxZoom = maxZoom
        val pos = Vec3(positionVector)
        for (i in 0 .. 7) {
            val g = ((i and 1) * 2 - 1).toFloat()
            val h = ((i shr 1 and 1) * 2 - 1).toFloat()
            val j = ((i shr 2 and 1) * 2 - 1).toFloat()
            val vec3 = pos.add((g * 0.1f).toDouble(), (h * 0.1f).toDouble(), (j * 0.1f).toDouble())
            val vec32 = vec3.add(Vec3(directionVector).scale(maxZoom.toDouble()))
            val level = (instance as CameraAccessor).level
            val hitResult: HitResult =
                level.clip(ClipContext(vec3, vec32, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, instance.entity))
            if (hitResult.type != HitResult.Type.MISS) {
                val k = hitResult.getLocation().distanceToSqr(pos).toFloat()
                if (k < Mth.square(maxZoom)) {
                    maxZoom = Mth.sqrt(k)
                }
            }
        }

        return maxZoom
    }
}