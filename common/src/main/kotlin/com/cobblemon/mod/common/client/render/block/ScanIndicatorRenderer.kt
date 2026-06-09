package com.cobblemon.mod.common.client.render.block

import com.cobblemon.mod.common.client.render.layer.CobblemonRenderLayers
import com.cobblemon.mod.common.util.cobblemonResource
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

object ScanIndicatorRenderer {
    val UNSCANNED: ResourceLocation = cobblemonResource("textures/gui/pokedex/scan/block_scan_indicator.png")
    val SCANNED: ResourceLocation = cobblemonResource("textures/gui/pokedex/scan/block_scan_indicator_scanned.png")

    private val FULL_BRIGHT = LightTexture.pack(15, 15)
    private val FULL_BLOCK_BOUNDS = AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)

    /** Max distance from the node to the first occluder for it to count as "its own block". */
    private const val ONE_BLOCK_REACH = 1.6

    /**
     * The ">= 1 block only" visibility gate shared by all scan indicators/nodes: returns true when at
     * most the target's own block sits between the camera and [center], and false once a real wall is
     * in front of it. Implemented as a single ray + distance check (cheap, no DDA): if nothing solid
     * is hit, line of sight is clear; if the first occluder is within ~one block of the target it's the
     * target's own block; anything farther means a wall is between the viewer and the target.
     */
    fun isWithinOneBlock(level: Level, eye: Vec3, center: Vec3, viewer: Entity): Boolean {
        val hit = level.clip(ClipContext(eye, center, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, viewer))
        if (hit.type != HitResult.Type.BLOCK) return true
        return hit.location.distanceTo(center) <= ONE_BLOCK_REACH
    }

    /**
     * Renders a camera-facing icon at the centre of a block's model bounds.
     * @param matrices the current PoseStack, translated to the block's local origin (0,0,0 = block corner)
     */
    fun render(
        matrices: PoseStack,
        bufferSource: MultiBufferSource,
        be: BlockEntity,
        scanned: Boolean,
        scale: Float = 0.5f
    ) {
        val bounds = be.level?.let { level ->
            val shape = be.blockState.getShape(level, be.blockPos)
            if (!shape.isEmpty) shape.bounds() else null
        } ?: FULL_BLOCK_BOUNDS
        val center = bounds.center

        matrices.pushPose()
        matrices.translate(center.x, center.y, center.z)
        renderAt(matrices, bufferSource, scanned, scale)
        matrices.popPose()
    }

    /**
     * Renders the camera-facing, always-on-top scan icon at the CURRENT PoseStack origin.
     * Callers translate the matrix to the desired centre first. Used by both block-entity indicators
     * and the ScanNode entity renderer.
     */
    fun renderAt(
        matrices: PoseStack,
        bufferSource: MultiBufferSource,
        scanned: Boolean,
        scale: Float = 0.5f
    ) {
        val texture = if (scanned) SCANNED else UNSCANNED
        val camera = Minecraft.getInstance().gameRenderer.mainCamera

        matrices.pushPose()
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
