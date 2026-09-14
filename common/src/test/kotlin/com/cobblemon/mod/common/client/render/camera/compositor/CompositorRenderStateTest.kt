package com.cobblemon.mod.common.client.render.camera.compositor

import com.cobblemon.mod.common.junit.BootstrapMinecraft
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.VertexSorting
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.joml.Matrix4f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.lwjgl.opengl.ARBImaging.GL_BLEND_COLOR
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL14
import org.lwjgl.opengl.GL30

@BootstrapMinecraft
class CompositorRenderStateTest {
    @Test
    fun `target and layout failures preserve caller matrices and render state`() {
        val projection = Matrix4f().perspective(1f, 1.5f, 0.1f, 100f)
        val modelView = Matrix4f().rotationZ(0.2f)
        val projectionField = RenderSystem::class.java.getDeclaredField("projectionMatrix").apply { isAccessible = true }
        val modelViewField = RenderSystem::class.java.getDeclaredField("modelViewMatrix").apply { isAccessible = true }
        val savedProjection = projectionField.get(null)
        val savedModelView = modelViewField.get(null)
        val stack = RenderSystem.getModelViewStack()
        stack.pushMatrix().identity().translate(6f, 4f, 2f)
        projectionField.set(null, projection)
        modelViewField.set(null, modelView)
        val originalProjection = Matrix4f(projection)
        val originalModelView = Matrix4f(modelView)
        val originalStack = Matrix4f(stack)
        var sorting = VertexSorting.DISTANCE_TO_ORIGIN
        var color = floatArrayOf(0.2f, 0.3f, 0.4f, 0.5f)
        var clearColor = floatArrayOf(0.5f, 0.4f, 0.3f, 0.2f)
        var blendColor = floatArrayOf(0.6f, 0.7f, 0.8f, 0.9f)
        var clearDepth = 0.25
        var depthMask = false
        var activeTexture = GL13.GL_TEXTURE3
        var fogStart = 3f
        var fogEnd = 60f
        val textures = mutableMapOf(GL13.GL_TEXTURE0 to 51, GL13.GL_TEXTURE3 to 83)
        val flags = mutableMapOf(GL11.GL_DEPTH_TEST to false, GL11.GL_CULL_FACE to false,
            GL11.GL_BLEND to false, GL11.GL_SCISSOR_TEST to true)
        val integers = mutableMapOf(GL30.GL_READ_FRAMEBUFFER_BINDING to 9, GL30.GL_DRAW_FRAMEBUFFER_BINDING to 7,
            GL14.GL_BLEND_SRC_RGB to GL11.GL_ONE, GL14.GL_BLEND_DST_RGB to GL11.GL_ZERO,
            GL14.GL_BLEND_SRC_ALPHA to GL11.GL_ZERO, GL14.GL_BLEND_DST_ALPHA to GL11.GL_ONE)
        val arrays = mutableMapOf(GL11.GL_SCISSOR_BOX to intArrayOf(10, 20, 30, 40),
            GL11.GL_VIEWPORT to intArrayOf(2, 3, 800, 600), GL11.GL_COLOR_WRITEMASK to intArrayOf(1, 0, 1, 0))
        val originalFlags = flags.toMap()
        val originalIntegers = integers.toMap()
        val originalArrays = arrays.mapValues { it.value.toList() }
        val target = mockk<RenderTarget>(relaxed = true)
        mockkStatic(RenderSystem::class, GL11::class, GL14::class, GL30::class)
        try {
            every { RenderSystem.assertOnRenderThread() } just Runs
            every { RenderSystem.getVertexSorting() } answers { sorting }
            every { RenderSystem.setProjectionMatrix(any(), any()) } answers { projection.set(firstArg<Matrix4f>()); sorting = secondArg(); Unit }
            every { RenderSystem.applyModelViewMatrix() } answers { modelView.set(stack); Unit }
            every { RenderSystem.getShader() } returns null
            every { RenderSystem.setShader(any()) } just Runs
            every { RenderSystem.getShaderTexture(0) } returns 123
            every { RenderSystem.setShaderTexture(any<Int>(), any<Int>()) } just Runs
            every { RenderSystem.getShaderColor() } answers { color }
            every { RenderSystem.setShaderColor(any(), any(), any(), any()) } answers { color = floatArrayOf(arg(0), arg(1), arg(2), arg(3)) }
            every { RenderSystem.getShaderFogStart() } answers { fogStart }
            every { RenderSystem.getShaderFogEnd() } answers { fogEnd }
            every { RenderSystem.setShaderFogStart(any()) } answers { fogStart = firstArg() }
            every { RenderSystem.setShaderFogEnd(any()) } answers { fogEnd = firstArg() }
            every { GL11.glGetInteger(any<Int>()) } answers {
                when (val key = firstArg<Int>()) {
                    GL13.GL_ACTIVE_TEXTURE -> activeTexture
                    GL11.GL_TEXTURE_BINDING_2D -> textures.getValue(activeTexture)
                    else -> integers.getValue(key)
                }
            }
            every { GL11.glGetIntegerv(any<Int>(), any<IntArray>()) } answers { arrays.getValue(firstArg()).copyInto(secondArg()); Unit }
            every { GL11.glGetFloatv(any<Int>(), any<FloatArray>()) } answers {
                (if (firstArg<Int>() == GL_BLEND_COLOR) blendColor else clearColor).copyInto(secondArg()); Unit
            }
            every { GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK) } answers { depthMask }
            every { GL11.glGetDouble(GL11.GL_DEPTH_CLEAR_VALUE) } answers { clearDepth }
            every { GL11.glClearDepth(any()) } answers { clearDepth = firstArg() }
            every { GL11.glIsEnabled(any()) } answers { flags.getValue(firstArg()) }
            every { GL14.glBlendColor(any(), any(), any(), any()) } answers { blendColor = floatArrayOf(arg(0), arg(1), arg(2), arg(3)) }
            every { GL30.glBindFramebuffer(any(), any()) } answers {
                integers[if (firstArg<Int>() == GL30.GL_READ_FRAMEBUFFER) GL30.GL_READ_FRAMEBUFFER_BINDING else GL30.GL_DRAW_FRAMEBUFFER_BINDING] = secondArg()
            }
            every { RenderSystem.activeTexture(any()) } answers { activeTexture = firstArg() }
            every { RenderSystem.bindTexture(any()) } answers { textures[activeTexture] = firstArg() }
            every { RenderSystem.clearColor(any(), any(), any(), any()) } answers { clearColor = floatArrayOf(arg(0), arg(1), arg(2), arg(3)) }
            every { RenderSystem.colorMask(any(), any(), any(), any()) } answers { arrays[GL11.GL_COLOR_WRITEMASK] = IntArray(4) { if (arg<Boolean>(it)) 1 else 0 } }
            every { RenderSystem.viewport(any(), any(), any(), any()) } answers { arrays[GL11.GL_VIEWPORT] = intArrayOf(arg(0), arg(1), arg(2), arg(3)) }
            every { RenderSystem.enableScissor(any(), any(), any(), any()) } answers {
                flags[GL11.GL_SCISSOR_TEST] = true
                arrays[GL11.GL_SCISSOR_BOX] = intArrayOf(arg(0), arg(1), arg(2), arg(3))
            }
            every { RenderSystem.disableScissor() } answers { flags[GL11.GL_SCISSOR_TEST] = false }
            every { RenderSystem.depthMask(any()) } answers { depthMask = firstArg() }
            every { RenderSystem.enableDepthTest() } answers { flags[GL11.GL_DEPTH_TEST] = true }
            every { RenderSystem.disableDepthTest() } answers { flags[GL11.GL_DEPTH_TEST] = false }
            every { RenderSystem.enableCull() } answers { flags[GL11.GL_CULL_FACE] = true }
            every { RenderSystem.disableCull() } answers { flags[GL11.GL_CULL_FACE] = false }
            every { RenderSystem.enableBlend() } answers { flags[GL11.GL_BLEND] = true }
            every { RenderSystem.disableBlend() } answers { flags[GL11.GL_BLEND] = false }
            every { RenderSystem.defaultBlendFunc() } answers {
                integers[GL14.GL_BLEND_SRC_RGB] = GL11.GL_SRC_ALPHA
                integers[GL14.GL_BLEND_DST_RGB] = GL11.GL_ONE_MINUS_SRC_ALPHA
            }
            every { RenderSystem.blendFuncSeparate(any<Int>(), any<Int>(), any<Int>(), any<Int>()) } answers {
                listOf(GL14.GL_BLEND_SRC_RGB, GL14.GL_BLEND_DST_RGB, GL14.GL_BLEND_SRC_ALPHA, GL14.GL_BLEND_DST_ALPHA)
                    .forEachIndexed { i, key -> integers[key] = arg(i) }
            }
            for (failOnClear in listOf(true, false)) {
                every { target.clear(false) } answers {
                    clearColor = floatArrayOf(0f, 0f, 0f, 0f)
                    clearDepth = 1.0
                    if (failOnClear) throw IllegalStateException("clear failed")
                }
                assertThrows(IllegalStateException::class.java) {
                    CompositorRenderState.render(target, 320, 180) { throw IllegalStateException("layout failed") }
                }
                assertEquals(originalProjection, projection)
                assertEquals(originalModelView, modelView)
                assertEquals(originalStack, Matrix4f(stack))
                assertEquals(VertexSorting.DISTANCE_TO_ORIGIN, sorting)
                assertEquals(originalFlags, flags)
                assertEquals(originalIntegers, integers)
                assertEquals(originalArrays, arrays.mapValues { it.value.toList() })
                assertEquals(mapOf(GL13.GL_TEXTURE0 to 51, GL13.GL_TEXTURE3 to 83), textures)
                assertEquals(GL13.GL_TEXTURE3, activeTexture)
                assertEquals(false, depthMask)
                assertEquals(0.25, clearDepth)
                assertEquals(listOf(0.2f, 0.3f, 0.4f, 0.5f), color.toList())
                assertEquals(listOf(0.5f, 0.4f, 0.3f, 0.2f), clearColor.toList())
                assertEquals(listOf(0.6f, 0.7f, 0.8f, 0.9f), blendColor.toList())
                assertEquals(3f, fogStart)
                assertEquals(60f, fogEnd)
            }
        } finally {
            unmockkStatic(RenderSystem::class, GL11::class, GL14::class, GL30::class)
            projectionField.set(null, savedProjection)
            modelViewField.set(null, savedModelView)
            stack.popMatrix()
        }
    }
}
