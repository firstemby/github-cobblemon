package com.cobblemon.mod.common.client.render.block

import com.cobblemon.mod.common.client.render.layer.CobblemonRenderLayers
import com.cobblemon.mod.common.util.cobblemonResource
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation

object ScanIndicatorRenderer {
    val UNSCANNED: ResourceLocation = cobblemonResource("textures/gui/pokedex/scan/block_scan_indicator.png")
    val SCANNED: ResourceLocation = cobblemonResource("textures/gui/pokedex/scan/block_scan_indicator_scanned.png")

    private val FULL_BRIGHT = LightTexture.pack(15, 15)

    /**
     * Renders a camera-facing icon above a block to indicate it is scannable. The icon billboards
     * toward the camera and ignores depth testing, so it is always visible regardless of viewing
     * angle or whether the block model would otherwise occlude it.
     *
     * @param matrices the current PoseStack, translated to the block's local origin (0,0,0 = block corner)
     * @param bufferSource the buffer source for rendering
     * @param scanned whether this block type has already been scanned by the player
     * @param offsetX horizontal offset from block centre (default 0)
     * @param offsetY vertical offset above block base (default 1.5 — floats above most blocks)
     * @param offsetZ depth offset from block centre (default 0)
     * @param scale size of the icon in world units (default 0.5)
     */
    fun render(
        matrices: PoseStack,
        bufferSource: MultiBufferSource,
        scanned: Boolean,
        offsetX: Float = 0f,
        offsetY: Float = 1.5f,
        offsetZ: Float = 0f,
        scale: Float = 0.5f
    ) {
        val texture = if (scanned) SCANNED else UNSCANNED
        val camera = Minecraft.getInstance().gameRenderer.mainCamera

        matrices.pushPose()
        matrices.translate(0.5 + offsetX, offsetY.toDouble(), 0.5 + offsetZ)
        matrices.mulPose(camera.rotation())
        matrices.scale(scale, scale, scale)

        val pose = matrices.last()
        val buffer = bufferSource.getBuffer(CobblemonRenderLayers.SCAN_INDICATOR.apply(texture))
        val h = 0.5f

        buffer.addVertex(pose.pose(), -h, -h, 0f)
            .setColor(255, 255, 255, 255).setUv(0f, 1f)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(pose, 0f, 0f, 1f)
        buffer.addVertex(pose.pose(), h, -h, 0f)
            .setColor(255, 255, 255, 255).setUv(1f, 1f)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(pose, 0f, 0f, 1f)
        buffer.addVertex(pose.pose(), h, h, 0f)
            .setColor(255, 255, 255, 255).setUv(1f, 0f)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(pose, 0f, 0f, 1f)
        buffer.addVertex(pose.pose(), -h, h, 0f)
            .setColor(255, 255, 255, 255).setUv(0f, 0f)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(pose, 0f, 0f, 1f)

        matrices.popPose()
    }
}
