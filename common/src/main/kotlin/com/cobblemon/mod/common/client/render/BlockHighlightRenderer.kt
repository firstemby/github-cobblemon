package com.cobblemon.mod.common.client.render

import com.cobblemon.mod.common.client.render.layer.CobblemonRenderLayers.BLOCK_HIGHLIGHT
import com.cobblemon.mod.common.platform.events.RenderEvent
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB

object BlockHighlightRenderer {

    fun render(event: RenderEvent) {
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level ?: return
        val poseStack = event.poseStack
        val camera = event.camera
        val cameraPos = camera.position

        val bufferSource = minecraft.renderBuffers().bufferSource()
        val lineBuffer = bufferSource.getBuffer(BLOCK_HIGHLIGHT)

        val highlightedBlocks = BlockHighlightManager.getActiveHighlights()

        for ((pos, highlight) in highlightedBlocks) {
            val state = level.getBlockState(pos)
            if (state.isAir) continue

            poseStack.pushPose()
            poseStack.translate(
                pos.x - cameraPos.x,
                pos.y - cameraPos.y,
                pos.z - cameraPos.z
            )

            LevelRenderer.renderLineBox(
                poseStack,
                lineBuffer,
                AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0),
                highlight.color[0],
                highlight.color[1],
                highlight.color[2],
                1.0f,
            )

            poseStack.popPose()
        }

        bufferSource.endBatch(BLOCK_HIGHLIGHT)
    }
}