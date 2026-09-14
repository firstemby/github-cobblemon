package com.cobblemon.mod.common.client.editor.cinematic.viewport

/** Coordinates are GLFW screen pixels with a top-left origin. */
object EditorViewportMath {

    fun isInside(mouseX: Float, mouseY: Float, rectX: Int, rectY: Int, rectW: Int, rectH: Int): Boolean =
        mouseX >= rectX && mouseX <= rectX + rectW && mouseY >= rectY && mouseY <= rectY + rectH

    fun screenToViewport(mouseX: Float, mouseY: Float, rectX: Int, rectY: Int, rectW: Int, rectH: Int): Pair<Float, Float>? {
        if (!isInside(mouseX, mouseY, rectX, rectY, rectW, rectH)) return null
        return (mouseX - rectX) to (mouseY - rectY)
    }

    fun blitFractions(rectX: Int, rectY: Int, rectW: Int, rectH: Int, winW: Int, winH: Int): FloatArray {
        val w = winW.coerceAtLeast(1).toFloat()
        val h = winH.coerceAtLeast(1).toFloat()
        return floatArrayOf(rectX / w, rectY / h, (rectX + rectW) / w, (rectY + rectH) / h)
    }

    fun overrideSize(rectSize: Int, default: Int): Int = if (rectSize > 1) rectSize else default
}
