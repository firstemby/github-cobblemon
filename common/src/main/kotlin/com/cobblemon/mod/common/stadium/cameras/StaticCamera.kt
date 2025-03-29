package com.cobblemon.mod.common.stadium.cameras

import com.cobblemon.mod.common.stadium.StadiumCameraEntry
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f

class StaticCamera(val position: Vec3, val rotation: Vector3f) : StadiumCameraEntry() {

    override fun resolvePosition(): Vec3 {
        return position
    }

    override fun resolveRotation(): Vector3f {
        return rotation
    }
}