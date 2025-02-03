package com.cobblemon.mod.common.client.render.layer.appendage

import com.google.gson.annotations.SerializedName
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3

data class ModelAppendage(
    @SerializedName("target_model") val targetModel: ResourceLocation?,
    @SerializedName("model") val model: ResourceLocation,
    @SerializedName("target_locator") val targetLocator: String,
    @SerializedName("scale") val scale: Vec3 = Vec3(1.0, 1.0, 1.0),
    @SerializedName("rotation") val rotation: Vec3 = Vec3(0.0, 0.0, 0.0),
    @SerializedName("offset") val offset: Vec3 = Vec3(0.0, 0.0, 0.0)
)