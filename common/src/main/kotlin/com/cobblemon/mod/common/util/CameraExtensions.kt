package com.cobblemon.mod.common.util

import com.cobblemon.mod.common.util.math.geometry.toDegrees
import com.cobblemon.mod.common.util.math.toEulerXYZ
import net.minecraft.client.Camera
import org.joml.Quaternionf

fun Camera.setRotation(pitch: Float, yaw: Float, roll: Float) {
    this.xRot = pitch.toDegrees()
    this.yRot = yaw.toDegrees()
    this.rotation.rotationYXZ(yaw, pitch, roll)
    Camera.FORWARDS.rotate(this.rotation, this.forwards)
    Camera.UP.rotate(this.rotation, this.up)
    Camera.LEFT.rotate(this.rotation, this.left)
}
