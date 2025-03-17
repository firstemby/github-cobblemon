package com.cobblemon.mod.common.stadium

import net.minecraft.world.phys.Vec3

interface StadiumCameraDefenition {
    fun resolvePosition(): Vec3

    fun resolveRotation(): Vec3
}