package com.cobblemon.mod.common.entity.stadium

import com.cobblemon.mod.common.entity.generic.GenericBedrockEntity
import com.cobblemon.mod.common.battles.stadium.StadiumDroneTrail
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.util.Mth
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

class StadiumCameraDroneEntity(type: EntityType<StadiumCameraDroneEntity>, world: Level) : GenericBedrockEntity(world, type) {
    val lightningTrail = StadiumDroneTrail()
    private var lerpSteps = 0
    private var lerpPosition = Vec3.ZERO
    private var lerpYaw = 0f
    private var lerpPitch = 0f

    init {
        category = cobblemonResource("rotom_drone")
        noPhysics = true
        setNoGravity(true)
    }

    override fun isPickable() = false
    override fun canBeCollidedWith() = false

    override fun lerpTo(x: Double, y: Double, z: Double, yaw: Float, pitch: Float, steps: Int) {
        val previousPosition = position()
        lightningTrail.beginMove(previousPosition, Vec3(x, y, z), steps, tickCount)
        if (steps <= 0) {
            lerpSteps = 0
            super.lerpTo(x, y, z, yaw, pitch, steps)
            lightningTrail.tick(previousPosition, position(), tickCount)
            return
        }
        lerpPosition = Vec3(x, y, z)
        lerpYaw = yaw
        lerpPitch = pitch
        lerpSteps = steps
    }

    override fun tick() {
        super.tick()
        val previousPosition = position()
        if (level().isClientSide && lerpSteps > 0) {
            val fraction = 1.0 / lerpSteps
            setPos(position().lerp(lerpPosition, fraction))
            yRot += Mth.wrapDegrees(lerpYaw - yRot) * fraction.toFloat()
            xRot = Mth.lerp(fraction.toFloat(), xRot, lerpPitch)
            lerpSteps--
        }
        if (level().isClientSide) lightningTrail.tick(previousPosition, position(), tickCount)
    }
}
