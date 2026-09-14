package com.cobblemon.mod.common.client.render

import com.cobblemon.mod.common.client.render.models.blockbench.PosableState
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.resources.ResourceLocation

class SpriteSheetModelTextureSupplier(
    val texture: ResourceLocation,
    val frameCount: Int,
    val fps: Float,
    val loop: Boolean
) : ModelTextureSupplier {
    init {
        require(frameCount > 0) { "Sprite sheets require at least one frame" }
        require(fps.isFinite() && fps > 0f) { "Sprite sheet FPS must be finite and positive" }
    }

    override fun invoke(state: PosableState) = texture

    override fun vertexConsumer(state: PosableState, vertices: VertexConsumer): VertexConsumer {
        val elapsedFrames = (state.animationSeconds * fps).toInt().coerceAtLeast(0)
        val frame = if (loop) elapsedFrames % frameCount else elapsedFrames.coerceAtMost(frameCount - 1)
        return TextureFrameVertexConsumer(vertices, frame, frameCount)
    }
}
