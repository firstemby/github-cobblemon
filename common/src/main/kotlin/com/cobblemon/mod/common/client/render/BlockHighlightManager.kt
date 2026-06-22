package com.cobblemon.mod.common.client.render

import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import java.util.concurrent.ConcurrentHashMap

object BlockHighlightManager {

    data class Highlight(val color: FloatArray, val expiryTick: Long)

    private val highlights = ConcurrentHashMap<BlockPos, Highlight>()

    fun highlight(pos: BlockPos, colorHex: String, durationTicks: Int) {
        val level = Minecraft.getInstance().level ?: return
        val expiry = level.gameTime + durationTicks
        highlights[pos.immutable()] = Highlight(parseColor(colorHex), expiry)
    }

    fun getActiveHighlights(): Map<BlockPos, Highlight> {
        val level = Minecraft.getInstance().level ?: return emptyMap()
        val now = level.gameTime
        highlights.entries.removeIf { it.value.expiryTick <= now }
        return highlights
    }

    fun clear() = highlights.clear()

    private fun parseColor(hex: String): FloatArray {
        val clean = hex.removePrefix("#")
        val intVal = clean.toInt(16)
        val r = ((intVal shr 16) and 0xFF) / 255f
        val g = ((intVal shr 8) and 0xFF) / 255f
        val b = (intVal and 0xFF) / 255f
        return floatArrayOf(r, g, b)
    }
}