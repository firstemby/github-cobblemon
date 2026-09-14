package com.cobblemon.mod.common.client.render.camera.compositor.layout

import com.cobblemon.mod.common.client.render.camera.CameraFeedHandler
import com.cobblemon.mod.common.client.render.camera.compositor.layout.SplitLayoutGeometry.Point
import com.cobblemon.mod.common.client.render.camera.compositor.layout.SplitLayoutGeometry.Regions
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.renderer.GameRenderer
import org.joml.Matrix4f
import java.util.UUID

internal object SplitLayoutRenderer {
    fun render(matrix: Matrix4f, width: Float, height: Float, primary: UUID, secondary: UUID, regions: Regions) {
        drawFeed(matrix, width, height, primary, regions.primary)
        drawFeed(matrix, width, height, secondary, regions.secondary)
        if (regions.gap.size < 3) return
        RenderSystem.setShader { GameRenderer.getPositionColorShader() }
        val buffer = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR)
        for (index in 1 until regions.gap.lastIndex) {
            for (point in listOf(regions.gap[0], regions.gap[index], regions.gap[index + 1])) {
                buffer.addVertex(matrix, point.x.toFloat(), point.y.toFloat(), 0f).setColor(0, 0, 0, 255)
            }
        }
        BufferUploader.drawWithShader(buffer.buildOrThrow())
    }

    private fun drawFeed(matrix: Matrix4f, width: Float, height: Float, id: UUID, polygon: List<Point>) {
        if (polygon.size < 3) return
        val target = CameraFeedHandler.getRenderTarget(id) ?: return
        val uv = SplitLayoutGeometry.textureCoordinates(polygon, width, height)
        RenderSystem.setShader { GameRenderer.getPositionTexShader() }
        RenderSystem.setShaderTexture(0, target.colorTextureId)
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
        val buffer = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX)
        for (index in 1 until polygon.lastIndex) {
            for (vertex in intArrayOf(0, index, index + 1)) {
                val point = polygon[vertex]
                buffer.addVertex(matrix, point.x.toFloat(), point.y.toFloat(), 0f)
                    .setUv(uv[vertex].x.toFloat(), uv[vertex].y.toFloat())
            }
        }
        BufferUploader.drawWithShader(buffer.buildOrThrow())
    }
}
