package com.cobblemon.mod.common.client.render.camera

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.OrientationControllable
import com.cobblemon.mod.common.api.riding.Seat
import com.cobblemon.mod.common.client.MountedPokemonAnimationRenderController
import com.cobblemon.mod.common.client.entity.PokemonClientDelegate
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository.getPoser
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.mixin.accessor.CameraAccessor
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.toVec3d
import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import org.joml.Matrix3f
import org.joml.Quaternionf
import org.joml.Vector3f

object CameraTransformationController {

    private val transformers = mutableMapOf<Int, MutableList<CameraTransformer>>(
        1 to mutableListOf(RidingCameraTransformer()),
    )

    fun register(priority: Int, transformer: CameraTransformer) {
        if (transformers[priority] == null) {
            transformers[priority] = mutableListOf()
        }
        transformers[priority]!!.add(transformer)
    }

    fun tick(current: CameraTransformation, delta: Float, context: CameraContext): CameraTransformation {
        val transformers = transformers.toSortedMap().reversed().values.flatten()
        transformers.forEach {
            if (it.condition(context)) {
                return it.tick(current, delta, context)
            }
        }
        return current
    }

}

interface CameraTransformer {
    val key: ResourceLocation
    val condition: (CameraContext) -> Boolean

    fun tick(current: CameraTransformation, delta: Float, context: CameraContext): CameraTransformation
    fun onStart() {}
    fun onStop() {}
}

data class CameraTransformation(val position: Vec3, val rotation: Quaternionf, val fov: Int)

data class CameraContext(
    val camera: Camera,
    val entity: Entity,
    val eyeHeight: Float,
    val eyeHeightO: Float,
    val perspective: CameraPerspective
)

enum class CameraPerspective {
    FIRST_PERSON,
    THIRD_PERSON_BACK,
    THIRD_PERSON_FRONT
}

class RidingCameraTransformer : CameraTransformer {
    override val key = cobblemonResource("camera/riding")
    override val condition: (CameraContext) -> Boolean = { it.entity.vehicle is PokemonEntity }

    val positionTransformer = RidingCameraPositionTransformer()
    val viewBobPositionTransformer = RidingCameraViewBobPositionTransformer()
    val rotationTransformer = OrientationControllerRotationTransformer()
    val viewBobRotationTransformer = RidingCameraViewBobRotationTransformer()

    override fun tick(current: CameraTransformation, delta: Float, context: CameraContext): CameraTransformation {
        val vehicle = context.entity.vehicle as? PokemonEntity ?: return current
        MountedPokemonAnimationRenderController.setup(vehicle, delta)
        val position = getPosition(delta, context, vehicle)
        val rotation = getRotation(current, context)
        val fov = Minecraft.getInstance().options.fov().get()
        return CameraTransformation(position, rotation, fov)
    }

    private fun getPosition(delta: Float, context: CameraContext, vehicle: PokemonEntity): Vec3 {
        if (context.perspective == CameraPerspective.FIRST_PERSON || Cobblemon.config.viewBobTranslation) {
            return viewBobPositionTransformer.getPosition(delta, context, vehicle)
        } else {
            return positionTransformer.getPosition(delta, context, vehicle)
        }
    }

    private fun getRotation(current: CameraTransformation, context: CameraContext): Quaternionf {
        if (Cobblemon.config.viewBobRotation) {
            return viewBobRotationTransformer.getRotation()
        }
        else {
            return rotationTransformer.getRotation(current.rotation, context)
        }
    }
}

class RidingCameraPositionTransformer {

    fun getPosition(delta: Float, context: CameraContext, vehicle: PokemonEntity): Vec3 {
        val position = Vec3(
            Mth.lerp(delta.toDouble(), vehicle.xOld, vehicle.x),
            Mth.lerp(delta.toDouble(), vehicle.yOld, vehicle.y),
            Mth.lerp(delta.toDouble(), vehicle.zOld, vehicle.z)
        )

        val orientation = getOrientation(context.entity)

        var offset = Vec3.ZERO
        offset = applyThirdPersonToOffset(offset, context, vehicle)
        offset = applyRollingToOffset(offset, orientation)
        offset = applyZoomToOffset(offset, position, context)

        val pivotOffset = applyPivotToOffset(vehicle, context.entity, orientation)
        return position.add(pivotOffset).add(offset)
    }
    fun applyPivotToOffset(vehicle: PokemonEntity, passenger: Entity, orientation: Matrix3f): Vec3 {
        val seatIndex: Int = vehicle.passengers.indexOf(passenger)
        if (seatIndex < 0) return Vec3.ZERO
        val seat: Seat = vehicle.seats[seatIndex]
        val model = getPoser(vehicle.pokemon.species.resourceIdentifier, FloatingState())
        val center = Vec3(0.0, passenger.bbHeight / 2.0, 0.0)

        val pivotOffsets = model.thirdPersonPivotOffset
        if (!pivotOffsets.containsKey(seat.locator)) return Vec3.ZERO

        val pivotOffset = pivotOffsets[seat.locator]!!
        val rotatedOffset = orientation.transform(pivotOffset.subtract(center).toVector3f())
        return rotatedOffset.toVec3d().add(center)
    }

    fun applyThirdPersonToOffset(offset: Vec3, context: CameraContext, vehicle: PokemonEntity): Vec3 {
        if (context.perspective == CameraPerspective.FIRST_PERSON) return offset

        val model = getPoser(vehicle.pokemon.species.resourceIdentifier, FloatingState())
        val cameraOffsets = model.thirdPersonCameraOffset

        val seatIndex: Int = vehicle.passengers.indexOf(context.entity)
        if (seatIndex < 0) return offset
        val seat: Seat = vehicle.seats[seatIndex]

        val thirdPartyOffset = getThirdPartyOffset(seat, cameraOffsets, context)
        return offset.add(thirdPartyOffset)
    }

    private fun getThirdPartyOffset(seat: Seat, offsets: MutableMap<String, Vec3>, context: CameraContext): Vec3 {
        val locatorName = when (context.perspective) {
            CameraPerspective.FIRST_PERSON -> seat.locator
            CameraPerspective.THIRD_PERSON_FRONT -> seat.locator
            CameraPerspective.THIRD_PERSON_BACK -> "${seat.locator}_reverse"
        }
        if (!offsets.containsKey(locatorName)) return Vec3.ZERO

        val offset = offsets[locatorName]!!
        return when (context.perspective) {
            CameraPerspective.FIRST_PERSON -> Vec3.ZERO
            CameraPerspective.THIRD_PERSON_FRONT -> offset
            CameraPerspective.THIRD_PERSON_BACK -> offset.multiply(1.0, 1.0, -1.0)
        }
    }

    fun applyRollingToOffset(offset: Vec3, orientation: Matrix3f): Vec3 {
        return orientation.transform(offset.toVector3f()).toVec3d()
    }

    fun applyZoomToOffset(offset: Vec3, pivot: Vec3, context: CameraContext): Vec3 {
        val offsetDistance = offset.length()
        val offsetDirection = offset.multiply(1 / offsetDistance, 1 / offsetDistance, 1 / offsetDistance)
        val maxZoom = getMaxZoom(offsetDistance.toFloat(), offsetDirection.toVector3f(), pivot.toVector3f(), context.camera).toDouble()
        return offset.multiply(maxZoom, maxZoom, maxZoom)
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

    fun getOrientation(passenger: Entity): Matrix3f {
        val xRot = (-1 * Math.toRadians(passenger.xRot.toDouble())).toFloat()
        val controller = (passenger as? OrientationControllable)?.getOrientationController()
        if (controller == null || !controller.isActive() || controller.orientation == null) {
            return Matrix3f()
                .rotateY(Math.toRadians((180f - passenger.yRot).toDouble()).toFloat())
                .rotateX(xRot)
        }
        return controller.orientation!!
    }

}

class RidingCameraViewBobPositionTransformer {

    fun getPosition(delta: Float, context: CameraContext, vehicle: PokemonEntity): Vec3 {
        val position = Vec3(
            Mth.lerp(delta.toDouble(), vehicle.xOld, vehicle.x),
            Mth.lerp(delta.toDouble(), vehicle.yOld, vehicle.y),
            Mth.lerp(delta.toDouble(), vehicle.zOld, vehicle.z)
        )

        var offset = Vec3.ZERO
        // Add eye level
        offset = applyAABBToOffset(context, delta, offset, context.entity)
        // Add first person offset from poser if found
        offset = applyFirstPersonToOffset(context, offset, vehicle, context.entity)
        // Transform the offset based on the entity's orientation
        offset = applyRollingToOffset(offset, delta, context.entity)
        // Add the offset from the locator
        offset = applyLocatorToOffset(offset, vehicle, context.entity)

        return position.add(offset)
    }

    private fun applyLocatorToOffset(offset: Vec3, vehicle: PokemonEntity, passenger: Entity): Vec3 {
        val seatIndex: Int = vehicle.passengers.indexOf(passenger)
        if (seatIndex < 0) return offset
        val seat: Seat = vehicle.seats[seatIndex]
        val delegate = vehicle.delegate as PokemonClientDelegate
        val locator = delegate.locatorStates[seat.locator] ?: return offset
        return offset.add(Vec3(locator.matrix.getTranslation(Vector3f())))
    }

    private fun applyAABBToOffset(context: CameraContext, delta: Float, offset: Vec3, passenger: Entity): Vec3 {
        val currEyeHeight: Double =
            Mth.lerp(delta.toDouble(), context.eyeHeight.toDouble(), context.eyeHeightO.toDouble())
        return offset.add(0.0, currEyeHeight - (passenger.bbHeight / 2), 0.0)
    }

    private fun applyRollingToOffset(offset: Vec3, delta: Float, passenger: Entity): Vec3 {
        val rollable = passenger as? OrientationControllable ?: return offset
        val controller = rollable.getOrientationController()
        if (!controller.isActive()) return offset

        return controller.getRenderOrientation(delta).transform(offset.toVector3f()).toVec3d()
    }

    private fun applyFirstPersonToOffset(
        context: CameraContext,
        offset: Vec3,
        vehicle: PokemonEntity,
        passenger: Entity
    ): Vec3 {
        if (context.perspective != CameraPerspective.FIRST_PERSON) return offset

        val model = getPoser(vehicle.pokemon.species.resourceIdentifier, FloatingState())
        val seatIndex: Int = vehicle.passengers.indexOf(passenger)
        val seat: Seat = vehicle.seats[seatIndex]
        val cameraOffsets = model.firstPersonCameraOffset

        return when (cameraOffsets.containsKey(seat.locator)) {
            true -> cameraOffsets[seat.locator]!!
            false -> Vec3.ZERO
        }
    }

}

class RidingCameraViewBobRotationTransformer {

    fun getRotation(): Quaternionf {
        TODO()
    }

}

class OrientationControllerRotationTransformer {

    fun getRotation(current: Quaternionf, context: CameraContext): Quaternionf {
        val entity = context.entity
        val controllable = entity as? OrientationControllable ?: return current

        val controller = controllable.getOrientationController()
        val orientation = controller.orientation ?: return current
        return orientation.normal(Matrix3f()).getNormalizedRotation(Quaternionf())
    }

}
