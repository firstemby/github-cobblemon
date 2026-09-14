package com.cobblemon.mod.common.client.render.generic

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis

internal object BedrockEntityTransform {
    fun apply(stack: PoseStack, yaw: Float, scale: Float, pitch: Float = 0f) {
        stack.mulPose(Axis.YP.rotationDegrees(180f - yaw))
        stack.mulPose(Axis.XP.rotationDegrees(-pitch))
        // Match living entities: reflecting only Y reverses face winding and culls the outside.
        stack.scale(-scale, -scale, scale)
    }
}
