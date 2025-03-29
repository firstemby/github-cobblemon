package com.cobblemon.mod.common.util

import com.cobblemon.mod.common.util.math.toEulerXYZ
import net.minecraft.client.Camera
import org.joml.Quaternionf

fun Camera.setRotation(yaw: Float, pitch: Float, roll: Float) {
    this.xRot = yaw
    this.yRot = pitch
    this.rotation.rotationYXZ(Math.PI.toFloat() - pitch * (Math.PI.toFloat() / 180F),-yaw * (Math.PI.toFloat() / 180F),roll * (Math.PI.toFloat() / 180F))
    Camera.FORWARDS.rotate(this.rotation, this.forwards)
    Camera.UP.rotate(this.rotation, this.up)
    Camera.LEFT.rotate(this.rotation, this.left)
}