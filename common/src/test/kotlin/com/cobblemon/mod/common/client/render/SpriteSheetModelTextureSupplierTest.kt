package com.cobblemon.mod.common.client.render

import com.cobblemon.mod.common.client.render.models.blockbench.PosableState
import com.cobblemon.mod.common.util.adapters.ModelTextureSupplierAdapter
import com.google.gson.GsonBuilder
import com.mojang.blaze3d.vertex.VertexConsumer
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SpriteSheetModelTextureSupplierTest {
    private val gson = GsonBuilder().registerTypeAdapter(ModelTextureSupplier::class.java, ModelTextureSupplierAdapter).create()
    private val state = mockk<PosableState>(relaxed = true)

    private fun texture(json: String) = gson.fromJson(json, ModelTextureSupplier::class.java)

    @Test fun `sheet definition selects frames at configured FPS and loops`() {
        val texture = texture("""{"spriteSheet":"test:sheet.png","frameCount":4,"fps":10}""")
        assertEquals("test:sheet.png", texture(state).toString())
        for ((seconds, frame) in listOf(0f to 0, 0.1f to 1, 0.2f to 2, 0.3f to 3, 0.4f to 0)) {
            every { state.animationSeconds } returns seconds
            val vertices = mockk<VertexConsumer>(relaxed = true)
            texture.vertexConsumer(state, vertices).setUv(0.5f, 0.5f)
            verify { vertices.setUv(0.5f, (frame + 0.5f) / 4) }
        }
    }

    @Test fun `non looping sheet holds final frame`() {
        val texture = texture("""{"spriteSheet":"test:sheet.png","frameCount":4,"fps":10,"loop":false}""")
        every { state.animationSeconds } returns 5f
        val vertices = mockk<VertexConsumer>(relaxed = true)
        texture.vertexConsumer(state, vertices).setUv(0f, 0f)
        verify { vertices.setUv(0f, 0.75f) }
    }

    @Test fun `existing static and separate frame textures preserve their UVs`() {
        val vertices = mockk<VertexConsumer>()
        val static = texture("\"test:static.png\"")
        assertEquals("test:static.png", static(state).toString())
        assertSame(vertices, static.vertexConsumer(state, vertices))
        val animated = texture("""{"frames":["test:one.png","test:two.png"],"fps":2}""")
        every { state.animationSeconds } returns 0.5f
        assertEquals("test:two.png", animated(state).toString())
        assertSame(vertices, animated.vertexConsumer(state, vertices))
    }

    @Test fun `invalid frame counts and playback speeds are rejected`() {
        for (count in listOf("0", "-1", "1.5", "4294967297", "null", "true", "\"4\"")) {
            assertThrows(RuntimeException::class.java) {
                texture("""{"spriteSheet":"test:sheet.png","frameCount":$count}""")
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            texture("""{"spriteSheet":"test:sheet.png","frameCount":4,"fps":0}""")
        }
    }
}
