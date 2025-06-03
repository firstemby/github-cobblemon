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
import com.cobblemon.mod.common.api.riding.Seat
import com.cobblemon.mod.common.client.MountedPokemonAnimationRenderController.setup
import com.cobblemon.mod.common.client.entity.PokemonClientDelegate
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository.getPoser
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.mixin.accessor.CameraAccessor
import net.minecraft.client.Camera
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import org.joml.Matrix3f
import org.joml.Vector3f

object MountedCameraRenderer
{
    fun getCameraPosition(
        instance: Camera,
        entity: Entity,
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
        val entityPos = Vec3(
            Mth.lerp(instance.partialTickTime.toDouble(), vehicle.xOld, vehicle.x),
            Mth.lerp(instance.partialTickTime.toDouble(), vehicle.yOld, vehicle.y),
            Mth.lerp(instance.partialTickTime.toDouble(), vehicle.zOld, vehicle.z)
        )
        setup(vehicle, instance.partialTickTime)
        val seatIndex: Int = vehicle.passengers.indexOf(entity)
        val seat: Seat = vehicle.seats[seatIndex]

        val rollable = entity as OrientationControllable
        val controller = rollable.getOrientationController()

        val model = getPoser(vehicle.pokemon.species.resourceIdentifier, FloatingState())


        // First person and view bobbing are handled the same way.
        if (!instance.isDetached || Cobblemon.config.thirdPersonViewBobbing) {
            val delegate = vehicle.delegate as PokemonClientDelegate
            val locator = delegate.locatorStates[seat.locator]

            if (locator == null) {
                return null
            }

            val locatorOffset = Vec3(locator.matrix.getTranslation(Vector3f()))

            // Get Eye height
            val currEyeHeight: Double = Mth.lerp(instance.partialTickTime.toDouble(), eyeHeight, eyeHeightOld)
            var offset = Vector3f(0f, (currEyeHeight - (entity.bbHeight / 2)).toFloat(), 0f)

            // Get additional offset from poser and add to the eyeHeight offset when first person
            if (!instance.isDetached) {
                offset.add(getFirstPersonOffset(model, seat))
            }

            // Rotate Offset if needed
            if (controller.isActive()) {
                offset = controller.getRenderOrientation(instance.partialTickTime).transform(offset)
            }

            val position = locatorOffset.add(entityPos).add(Vec3(offset))
            return position
        } else {
            val xRot = (-1 * Math.toRadians(instance.xRot.toDouble())).toFloat()

            // get orientation based on OrientationController or normal rotations
            val orientation =
                if (controller.isActive() && controller.orientation != null)
                    controller.orientation!!
                else
                    Matrix3f()
                        .rotateY(Math.toRadians((180f - instance.yRot).toDouble()).toFloat())
                        .rotateX(xRot)

            // Get pivot from poser
            val pivotOffsets = model.thirdPersonPivotOffset
            val pokemonCenter = Vector3f(0f, entity.bbHeight/2, 0f)

            var pivot = Vector3f(pokemonCenter)
            if (pivotOffsets.containsKey(seat.locator)) {
                pivot = pivotOffsets[seat.locator]!!.toVector3f()
                pivot.sub(pokemonCenter)
                orientation.transform(pivot)
                pivot.add(pokemonCenter)
            }

            val pos = entityPos.toVector3f().add(pivot)

            // Get offset from poser
            val offset = getThirdPersonOffset(thirdPersonReverse, model, seat)

            if (thirdPersonReverse) offset.z *= -1f
            val rotatedOffset = orientation.transform(offset)
            val offsetDistance = rotatedOffset.length()
            val offsetDirection = rotatedOffset.mul(1 / offsetDistance)

            // Use getMaxZoom to calculate clipping
            val maxZoom: Float = getMaxZoom(offsetDistance, offsetDirection, pos, instance)

            return Vec3(offsetDirection.mul(maxZoom).add(pos))
        }
    }


    private fun getThirdPersonOffset(
        thirdPersonReverse: Boolean,
        model: PosableModel,
        seat: Seat
    ): Vector3f {
        val cameraOffsets = model.thirdPersonCameraOffset

        return if (thirdPersonReverse && cameraOffsets.containsKey(seat.locator + "_reverse")) {
            cameraOffsets[seat.locator + "_reverse"]!!.toVector3f()
        } else if (cameraOffsets.containsKey(seat.locator)) {
            cameraOffsets[seat.locator]!!.toVector3f()
        } else {
            Vector3f(0f, 2f, 4f)
        }
    }

    private fun getFirstPersonOffset(model: PosableModel, seat: Seat): Vector3f {
        val cameraOffsets = model.firstPersonCameraOffset

        return if (cameraOffsets.containsKey(seat.locator)) {
            cameraOffsets[seat.locator]!!.toVector3f()
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