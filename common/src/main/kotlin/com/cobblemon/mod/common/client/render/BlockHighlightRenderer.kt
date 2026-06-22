package com.cobblemon.mod.common.client.render

import com.cobblemon.mod.common.client.render.layer.CobblemonRenderLayers.BLOCK_HIGHLIGHT_CUTOUT
import com.cobblemon.mod.common.client.render.layer.CobblemonRenderLayers.BLOCK_HIGHLIGHT_SOLID
import com.cobblemon.mod.common.platform.events.RenderEvent
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.texture.OverlayTexture

class XRayBufferSource(private val delegate: MultiBufferSource.BufferSource) : MultiBufferSource {
    override fun getBuffer(renderType: RenderType): VertexConsumer {
        val name = renderType.toString()
        val redirected = when {
            name.contains("entity_cutout") || name.contains("cutout") ->
                BLOCK_HIGHLIGHT_CUTOUT

            name.contains("entity_solid") || name.contains("solid") ->
                BLOCK_HIGHLIGHT_SOLID

            else -> renderType
        }
        return delegate.getBuffer(redirected)
    }
}

object BlockHighlightRenderer {

    fun render(event: RenderEvent) {
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level ?: return
        val poseStack = event.poseStack
        val cameraPos = event.camera.position

        val realBufferSource = minecraft.renderBuffers().bufferSource()
        val xrayBufferSource = XRayBufferSource(realBufferSource)

        val highlightedBlocks = BlockHighlightManager.getActiveHighlights()
        if (highlightedBlocks.isEmpty()) return

        val sortedEntries = highlightedBlocks.entries.sortedByDescending { (pos, _) ->
            val dx = pos.x + 0.5 - cameraPos.x
            val dy = pos.y + 0.5 - cameraPos.y
            val dz = pos.z + 0.5 - cameraPos.z
            dx * dx + dy * dy + dz * dz
        }


        for ((pos, _) in sortedEntries) {
            val state = level.getBlockState(pos)
            if (state.isAir) continue

            poseStack.pushPose()
            poseStack.translate(
                pos.x - cameraPos.x,
                pos.y - cameraPos.y,
                pos.z - cameraPos.z
            )

            minecraft.blockRenderer.renderSingleBlock(
                state,
                poseStack,
                xrayBufferSource,
                LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY
            )

            poseStack.popPose()
        }

        val brightness = 0.5f
        val alpha = 1f

        RenderSystem.setShaderColor(brightness, brightness, brightness, alpha)
        realBufferSource.endBatch(BLOCK_HIGHLIGHT_SOLID)
        realBufferSource.endBatch(BLOCK_HIGHLIGHT_CUTOUT)
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
    }
}