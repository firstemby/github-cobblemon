package com.cobblemon.mod.common.integration.rei

import com.cobblemon.mod.common.api.gui.blitk
import me.shedaniel.math.Rectangle
import me.shedaniel.rei.api.client.gui.Renderer
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation

class CobblemonReiIcon(val iconResource: ResourceLocation, val width: Int, val height: Int): Renderer {
    override fun render(graphics: GuiGraphics, bounds: Rectangle, mouseX: Int, mouseY: Int, delta: Float) {
        blitk(
            matrixStack = graphics.pose(),
            texture = iconResource,
            x = bounds.minX,
            y = bounds.minY,
            width = width,
            height = height
        )
    }
}