package com.cobblemon.mod.common.client.render.generic

import com.mojang.blaze3d.vertex.VertexConsumer
import com.cobblemon.mod.common.client.render.TextureFrameVertexConsumer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TextureFrameVertexConsumerTest {
    @Test fun `four stacked frames keep head UVs inside the selected 32 pixel frame`() {
        for (frame in 0 until 4) {
            val delegate = RecordingVertices()
            val vertices = TextureFrameVertexConsumer(delegate, frame, 4)
            vertices.addVertex(1f, 2f, 3f).setColor(255, 200, 100, 255)
                .setUv(7f / 64f, 7f / 32f).setUv1(0, 10).setUv2(240, 240).setNormal(0f, 0f, -1f)
            assertEquals(listOf(7f / 64f, (frame * 32f + 7f) / 128f), delegate.uv)
            assertEquals(listOf(1f, 2f, 3f), delegate.position)
            assertEquals(listOf(255, 200, 100, 255), delegate.color)
            assertEquals(listOf(0, 10), delegate.overlay)
            assertEquals(listOf(240, 240), delegate.light)
            assertEquals(listOf(0f, 0f, -1f), delegate.normal)
        }
    }

    @Test fun `model bulk vertices use the same frame mapping`() {
        val delegate = RecordingVertices()
        val vertices = TextureFrameVertexConsumer(delegate, 3, 4)
        vertices.addVertex(1f, 2f, 3f, -1, 0.5f, 0.5f, 0, 0, 0f, 1f, 0f)
        assertEquals(listOf(0.5f, 0.875f), delegate.uv)
        assertEquals(listOf(1f, 2f, 3f), delegate.position)
    }

    private class RecordingVertices : VertexConsumer {
        var position = emptyList<Float>()
        var color = emptyList<Int>()
        var uv = emptyList<Float>()
        var overlay = emptyList<Int>()
        var light = emptyList<Int>()
        var normal = emptyList<Float>()

        override fun addVertex(x: Float, y: Float, z: Float): VertexConsumer = apply { position = listOf(x, y, z) }
        override fun setColor(red: Int, green: Int, blue: Int, alpha: Int): VertexConsumer = apply { color = listOf(red, green, blue, alpha) }
        override fun setUv(u: Float, v: Float): VertexConsumer = apply { uv = listOf(u, v) }
        override fun setUv1(u: Int, v: Int): VertexConsumer = apply { overlay = listOf(u, v) }
        override fun setUv2(u: Int, v: Int): VertexConsumer = apply { light = listOf(u, v) }
        override fun setNormal(x: Float, y: Float, z: Float): VertexConsumer = apply { normal = listOf(x, y, z) }
    }
}
