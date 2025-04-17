package com.cobblemon.mod.common.stadium

import com.cobblemon.mod.common.util.adapters.Vec3dAdapter
import com.google.gson.GsonBuilder
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f

val gson = GsonBuilder()
    .setPrettyPrinting()
    .disableHtmlEscaping()
    .registerTypeAdapter(Vec3::class.java, Vec3dAdapter)
    .create()

fun deserialize(data: String): CameraAnimation {
    return gson.fromJson(data, CameraAnimation::class.java)
}

data class CameraAnimation(
    val length: Double,
    val origin: CameraOrigin,
    val timeline: Timeline
)

data class Timeline(
    val position: Map<Double, Vec3>,
    val rotation: Map<Double, Vec3>,
)

data class CameraOrigin(
    val position: Vec3,
    val rotation: Vec3,
)
