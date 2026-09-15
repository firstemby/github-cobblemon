package com.cobblemon.mod.common.client.render

import com.mojang.blaze3d.systems.RenderSystem
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL14
import kotlin.math.sqrt

object RenderAlphaScope {
    private class MaterialState(val color: FloatArray, val blendColor: FloatArray?)

    private class Scope {
        val materials = ArrayDeque<MaterialState>()
    }

    private val current = ThreadLocal<Scope>()

    fun render(draw: () -> Unit) {
        val color = RenderSystem.getShaderColor().copyOf()
        if (!color[3].isFinite() || color[3] >= 1f) {
            draw()
            return
        }
        val blend = RenderBlendState.capture()
        val previous = current.get()
        current.set(Scope())
        try {
            draw()
        } finally {
            if (previous == null) current.remove() else current.set(previous)
            RenderSystem.setShaderColor(color[0], color[1], color[2], color[3])
            blend.restore()
        }
    }

    @JvmStatic
    fun afterMaterialSetup() {
        val scope = current.get() ?: return
        val color = RenderSystem.getShaderColor().copyOf()
        val blended = GL11.glIsEnabled(GL11.GL_BLEND)
        scope.materials.addLast(MaterialState(color, if (blended) null else RenderBlendState.readColor()))
        val opacity = color[3].coerceIn(0f, 1f)
        if (!blended) {
            RenderSystem.enableBlend()
            // Cutout texels are opaque above the discard threshold; fade their coverage uniformly.
            GL14.glBlendColor(0f, 0f, 0f, opacity)
            RenderSystem.blendFuncSeparate(GL14.GL_CONSTANT_ALPHA, GL14.GL_ONE_MINUS_CONSTANT_ALPHA, GL11.GL_ONE, GL11.GL_ZERO)
        } else {
            val factor = when (GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB)) {
                GL11.GL_ONE -> opacity
                // SRC_COLOR squares the source RGB, so sqrt(alpha) fades glint linearly.
                GL11.GL_SRC_COLOR -> sqrt(opacity)
                else -> 1f
            }
            if (factor != 1f) RenderSystem.setShaderColor(color[0] * factor, color[1] * factor, color[2] * factor, color[3])
        }
    }

    @JvmStatic
    fun afterMaterialClear() {
        val material = current.get()?.materials?.removeLastOrNull() ?: return
        val color = material.color
        RenderSystem.setShaderColor(color[0], color[1], color[2], color[3])
        material.blendColor?.let { RenderBlendState.restoreColor(it) }
    }
}
