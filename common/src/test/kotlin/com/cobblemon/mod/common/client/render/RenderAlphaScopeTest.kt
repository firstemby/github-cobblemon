package com.cobblemon.mod.common.client.render

import com.cobblemon.mod.common.junit.BootstrapMinecraft
import com.mojang.blaze3d.systems.RenderSystem
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.lwjgl.opengl.ARBImaging.GL_BLEND_COLOR
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL14

@BootstrapMinecraft
class RenderAlphaScopeTest {
    private var enabled = true
    private var color = floatArrayOf(1f, .8f, .4f, .25f)
    private var blendColor = floatArrayOf(.2f, .3f, .4f, .6f)
    private var factors = listOf(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE)

    @BeforeEach
    fun setup() {
        mockkStatic(RenderSystem::class, GL11::class, GL14::class)
        every { GL11.glIsEnabled(GL11.GL_BLEND) } answers { enabled }
        every { GL11.glGetInteger(any<Int>()) } answers {
            factors[listOf(GL14.GL_BLEND_SRC_RGB, GL14.GL_BLEND_DST_RGB, GL14.GL_BLEND_SRC_ALPHA, GL14.GL_BLEND_DST_ALPHA).indexOf(firstArg())]
        }
        every { RenderSystem.getShaderColor() } answers { color }
        every { GL11.glGetFloatv(GL_BLEND_COLOR, any<FloatArray>()) } answers { blendColor.copyInto(secondArg()); Unit }
        every { GL14.glBlendColor(any(), any(), any(), any()) } answers { blendColor = floatArrayOf(arg(0), arg(1), arg(2), arg(3)) }
        every { RenderSystem.setShaderColor(any(), any(), any(), any()) } answers { color = floatArrayOf(arg(0), arg(1), arg(2), arg(3)) }
        every { RenderSystem.enableBlend() } answers { enabled = true }
        every { RenderSystem.disableBlend() } answers { enabled = false }
        every { RenderSystem.defaultBlendFunc() } answers { factors = listOf(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO) }
        every { RenderSystem.blendFuncSeparate(any<Int>(), any<Int>(), any<Int>(), any<Int>()) } answers { factors = listOf(arg(0), arg(1), arg(2), arg(3)) }
    }

    @AfterEach
    fun cleanup() = unmockkStatic(RenderSystem::class, GL11::class, GL14::class)

    @Test
    fun `material hooks have no effect outside an explicit alpha scope`() {
        RenderAlphaScope.afterMaterialSetup()
        RenderAlphaScope.afterMaterialClear()
        verify(exactly = 0) { RenderSystem.getShaderColor() }
        verify(exactly = 0) { GL11.glIsEnabled(any()) }
    }

    @Test
    fun `cutout material setup cannot disable fading and the caller blend state is restored`() {
        val originalFactors = factors
        val originalColor = color.toList()
        val originalBlendColor = blendColor.toList()
        RenderAlphaScope.render {
            enabled = false
            RenderAlphaScope.afterMaterialSetup()
            assertTrue(enabled)
            assertEquals(GL14.GL_CONSTANT_ALPHA, factors[0])
            assertEquals(GL14.GL_ONE_MINUS_CONSTANT_ALPHA, factors[1])
            assertEquals(.25f, blendColor[3])
            assertEquals(originalColor, color.toList())
            RenderAlphaScope.afterMaterialClear()
            assertEquals(originalBlendColor, blendColor.toList())
        }
        assertTrue(enabled)
        assertEquals(originalFactors, factors)
        assertEquals(originalColor, color.toList())
    }

    @Test
    fun `additive and glint contributions fade linearly without dimming the next material`() {
        val originalColor = color.copyOf()
        for ((source, expectedFactor) in listOf(GL11.GL_ONE to .25f, GL11.GL_SRC_COLOR to .5f, GL11.GL_SRC_ALPHA to 1f)) {
            factors = listOf(source, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE)
            RenderAlphaScope.render {
                RenderAlphaScope.afterMaterialSetup()
                assertEquals(originalColor[0] * expectedFactor, color[0])
                assertEquals(originalColor[1] * expectedFactor, color[1])
                assertEquals(originalColor[3], color[3])
                RenderAlphaScope.afterMaterialClear()
                assertEquals(originalColor.toList(), color.toList())
            }
        }
    }

    @Test
    fun `nested draw failure restores both scopes even without material clear`() {
        val originalFactors = factors
        val originalColor = color.toList()
        val originalBlendColor = blendColor.toList()
        assertThrows(IllegalStateException::class.java) {
            RenderAlphaScope.render {
                enabled = false
                RenderAlphaScope.afterMaterialSetup()
                val outerFactors = factors
                val outerColor = color.toList()
                assertThrows(IllegalArgumentException::class.java) {
                    RenderAlphaScope.render {
                        enabled = false
                        RenderAlphaScope.afterMaterialSetup()
                        throw IllegalArgumentException("nested draw failed")
                    }
                }
                assertTrue(enabled)
                assertEquals(outerFactors, factors)
                assertEquals(outerColor, color.toList())
                throw IllegalStateException("outer draw failed")
            }
        }
        assertEquals(originalFactors, factors)
        assertEquals(originalColor, color.toList())
        assertEquals(originalBlendColor, blendColor.toList())
        enabled = false
        RenderAlphaScope.afterMaterialSetup()
        assertFalse(enabled)
    }
}
