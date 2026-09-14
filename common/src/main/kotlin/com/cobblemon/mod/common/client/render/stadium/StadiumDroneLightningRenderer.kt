package com.cobblemon.mod.common.client.render.stadium

import com.cobblemon.mod.common.entity.stadium.StadiumCameraDroneEntity
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.world.phys.Vec3
import kotlin.math.ceil

internal object StadiumDroneLightningRenderer {
    fun render(entity: StadiumCameraDroneEntity, partialTicks: Float, stack: PoseStack, buffers: MultiBufferSource) {
        val segments = entity.lightningTrail.segments
        if (segments.isEmpty()) return
        val vertices = buffers.getBuffer(RenderType.lightning())
        val matrix = stack.last().pose()
        val origin = entity.getPosition(partialTicks)
        val camera = Minecraft.getInstance().gameRenderer.mainCamera.position
        for (segment in segments) {
            val opacity = segment.opacity(entity.tickCount + partialTicks)
            if (opacity <= 0f) continue
            val points = if (segment.tick == entity.tickCount) {
                segment.points.take(ceil((segment.points.size - 1) * partialTicks).toInt().coerceAtLeast(1)) + origin
            } else segment.points
            for ((start, end) in points.zipWithNext()) {
                if (start.distanceToSqr(end) < 0.000001) continue
                val direction = end.subtract(start).normalize()
                var side = direction.cross(camera.subtract(start)).normalize()
                if (side.lengthSqr() < 0.01) side = direction.cross(Vec3(0.0, 1.0, 0.0)).normalize()
                if (side.lengthSqr() < 0.01) side = direction.cross(Vec3(1.0, 0.0, 0.0)).normalize()
                val localStart = start.subtract(origin)
                val localEnd = end.subtract(origin)
                for (glow in listOf(true, false)) {
                    val width = if (glow) 0.075 else 0.022
                    val edge = side.scale(width * opacity)
                    val red = if (glow) 0.18f else 0.85f
                    val green = if (glow) 0.65f else 0.95f
                    val alpha = opacity * if (glow) 0.3f else 0.9f
                    for (point in listOf(localStart.subtract(edge), localStart.add(edge), localEnd.add(edge), localEnd.subtract(edge))) {
                        vertices.addVertex(matrix, point.x.toFloat(), point.y.toFloat(), point.z.toFloat())
                            .setColor(red, green, 1f, alpha)
                    }
                }
            }
        }
    }
}
