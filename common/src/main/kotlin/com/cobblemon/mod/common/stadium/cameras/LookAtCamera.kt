package com.cobblemon.mod.common.stadium.cameras

import com.cobblemon.mod.common.stadium.StadiumCameraEntry
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import kotlin.math.atan
import kotlin.math.atan2

class LookAtCamera(val position: Vec3, val target: Vec3): StadiumCameraEntry() {
    override fun resolvePosition(): Vec3 {
        return position
    }

    override fun resolveRotation(): Vector3f {
        val offset = target.subtract(position)
        val xRot = -atan(offset.y.toFloat()/offset.horizontalDistance().toFloat()) * (180F / Math.PI.toFloat())
        val yRot = -atan2(offset.x.toFloat(),offset.z.toFloat()) * (180F / Math.PI.toFloat())
        return Vector3f(xRot, yRot, 0.0F)
    }
}