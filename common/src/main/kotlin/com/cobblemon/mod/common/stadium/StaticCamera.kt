package com.cobblemon.mod.common.stadium

import net.minecraft.world.phys.Vec3

class StaticCamera(val position: Vec3, val rotation: Vec3) : StadiumCameraDefenition {

    override fun resolvePosition(): Vec3 {
        return position
    }

    override fun resolveRotation(): Vec3 {
        return rotation
    }
}