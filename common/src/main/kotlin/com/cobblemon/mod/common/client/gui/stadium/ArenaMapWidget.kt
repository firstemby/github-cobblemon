/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.stadium

import com.cobblemon.mod.common.battles.stadium.StadiumBlockLayout
import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import com.cobblemon.mod.common.util.cobblemonResource

class ArenaMapWidget(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    private val origin: BlockPos,
    private val layout: StadiumBlockLayout?
) : AbstractWidget(x, y, width, height, Component.empty()) {

    companion object {
        private const val MAP_RADIUS = 32
        private const val MAP_SIZE = MAP_RADIUS * 2

        // Marker colors
        private const val ORIGIN_COLOR = 0xFFFFFF00.toInt() // Yellow
        private const val SIDE1_ACTOR_COLOR = 0xFF4488FF.toInt() // Blue
        private const val SIDE1_POKEMON_COLOR = 0xFF44DDFF.toInt() // Cyan
        private const val SIDE2_ACTOR_COLOR = 0xFFFF4444.toInt() // Red
        private const val SIDE2_POKEMON_COLOR = 0xFFFF8844.toInt() // Orange
        private const val UNLOADED_COLOR = 0xFF808080.toInt() // Gray
    }

    private var terrainTextureId: ResourceLocation? = null
    private var terrainGenerated = false
    private var hoveredMarkerTooltip: Component? = null

    var selectedMarkerIndex: Int? = null
    var onMarkerSelected: ((Int) -> Unit)? = null

    private val markerPositions = mutableListOf<MarkerHitArea>()

    private data class MarkerHitArea(val screenX: Int, val screenY: Int, val markerSize: Int, val flatIndex: Int)

    fun generateTerrainMap() {
        val level = Minecraft.getInstance().level ?: return
        if (terrainGenerated) return

        val image = NativeImage(MAP_SIZE, MAP_SIZE, true)
        val centerX = origin.x
        val centerZ = origin.z

        for (px in 0 until MAP_SIZE) {
            for (pz in 0 until MAP_SIZE) {
                val worldX = centerX - MAP_RADIUS + px
                val worldZ = centerZ - MAP_RADIUS + pz
                val blockPos = BlockPos(worldX, 0, worldZ)

                val color = if (level.hasChunk(blockPos.x shr 4, blockPos.z shr 4)) {
                    getTerrainColor(level, worldX, worldZ)
                } else {
                    UNLOADED_COLOR
                }

                image.setPixelRGBA(px, pz, abgrFromArgb(color))
            }
        }

        val dynamicTexture = DynamicTexture(image)
        val textureManager = Minecraft.getInstance().textureManager
        terrainTextureId?.let { textureManager.release(it) }
        terrainTextureId = textureManager.register("stadium_arena_map", dynamicTexture)
        terrainGenerated = true
    }

    private fun getTerrainColor(level: Level, worldX: Int, worldZ: Int): Int {
        val topY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, worldX, worldZ)
        val pos = BlockPos(worldX, topY - 1, worldZ)
        val state = level.getBlockState(pos)

        if (state.isAir || state.`is`(Blocks.VOID_AIR)) {
            return 0xFF333333.toInt()
        }

        val mapColor = state.getMapColor(level, pos)
        val baseColor = mapColor.col
        if (baseColor == 0) return 0xFF333333.toInt()

        // Height-based shading
        val heightDiff = (topY - origin.y).coerceIn(-10, 10)
        val shade = 1.0f + heightDiff * 0.02f
        val r = ((baseColor shr 16) and 0xFF).toFloat() * shade
        val g = ((baseColor shr 8) and 0xFF).toFloat() * shade
        val b = (baseColor and 0xFF).toFloat() * shade

        return (0xFF shl 24) or
                (r.toInt().coerceIn(0, 255) shl 16) or
                (g.toInt().coerceIn(0, 255) shl 8) or
                b.toInt().coerceIn(0, 255)
    }

    override fun renderWidget(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        generateTerrainMap()
        hoveredMarkerTooltip = null

        // Draw terrain texture
        terrainTextureId?.let { texId ->
            guiGraphics.blit(texId, x, y, 0f, 0f, width, height, width, height)
        } ?: run {
            guiGraphics.fill(x, y, x + width, y + height, 0xFF333333.toInt())
        }

        // Draw border
        guiGraphics.renderOutline(x, y, width, height, 0xFF5C5C7A.toInt())

        // Draw origin marker (center diamond)
        val originScreenX = x + width / 2
        val originScreenY = y + height / 2
        drawDiamond(guiGraphics, originScreenX, originScreenY, ORIGIN_COLOR)

        // Draw layout markers
        layout?.let { drawLayoutMarkers(guiGraphics, it, mouseX, mouseY) }

        // Draw tooltip
        hoveredMarkerTooltip?.let {
            guiGraphics.renderTooltip(Minecraft.getInstance().font, it, mouseX, mouseY)
        }
    }

    private fun drawLayoutMarkers(guiGraphics: GuiGraphics, layout: StadiumBlockLayout, mouseX: Int, mouseY: Int) {
        markerPositions.clear()

        var flatIndex = 0

        for ((i, placement) in layout.side1.actors.withIndex()) {
            val label = "Side 1 Actor ${i + 1}"
            drawMarker(guiGraphics, placement.position, SIDE1_ACTOR_COLOR, label, mouseX, mouseY, flatIndex)
            flatIndex++
        }
        for ((i, placement) in layout.side1.pokemon.withIndex()) {
            val label = "Side 1 Pokemon ${i + 1}"
            drawMarker(guiGraphics, placement.position, SIDE1_POKEMON_COLOR, label, mouseX, mouseY, flatIndex)
            flatIndex++
        }
        for ((i, placement) in layout.side2.actors.withIndex()) {
            val label = "Side 2 Actor ${i + 1}"
            drawMarker(guiGraphics, placement.position, SIDE2_ACTOR_COLOR, label, mouseX, mouseY, flatIndex)
            flatIndex++
        }
        for ((i, placement) in layout.side2.pokemon.withIndex()) {
            val label = "Side 2 Pokemon ${i + 1}"
            drawMarker(guiGraphics, placement.position, SIDE2_POKEMON_COLOR, label, mouseX, mouseY, flatIndex)
            flatIndex++
        }
    }

    private fun drawMarker(guiGraphics: GuiGraphics, worldPos: Vec3, color: Int, label: String, mouseX: Int, mouseY: Int, flatIndex: Int) {
        val screenX = worldToScreenX(worldPos.x)
        val screenY = worldToScreenZ(worldPos.z)

        if (screenX < x || screenX >= x + width || screenY < y || screenY >= y + height) return

        // Draw marker (filled square)
        val markerSize = 3
        guiGraphics.fill(screenX - markerSize, screenY - markerSize, screenX + markerSize, screenY + markerSize, color)
        guiGraphics.renderOutline(screenX - markerSize, screenY - markerSize, markerSize * 2, markerSize * 2, 0xFF000000.toInt())

        // Selection highlight
        if (flatIndex == selectedMarkerIndex) {
            val highlightSize = markerSize + 2
            guiGraphics.renderOutline(
                screenX - highlightSize, screenY - highlightSize,
                highlightSize * 2, highlightSize * 2,
                0xFFFFFFFF.toInt()
            )
        }

        // Record hit area for click detection
        markerPositions.add(MarkerHitArea(screenX, screenY, markerSize, flatIndex))

        // Hover detection
        if (mouseX in (screenX - markerSize)..(screenX + markerSize) &&
            mouseY in (screenY - markerSize)..(screenY + markerSize)) {
            hoveredMarkerTooltip = Component.literal("$label: %.1f, %.1f, %.1f".format(worldPos.x, worldPos.y, worldPos.z))
        }
    }

    private fun drawDiamond(guiGraphics: GuiGraphics, cx: Int, cy: Int, color: Int) {
        guiGraphics.fill(cx - 1, cy - 2, cx + 2, cy - 1, color)
        guiGraphics.fill(cx - 2, cy - 1, cx + 3, cy + 2, color)
        guiGraphics.fill(cx - 1, cy + 2, cx + 2, cy + 3, color)
    }

    private fun worldToScreenX(worldX: Double): Int {
        val relativeX = worldX - origin.x
        return x + ((relativeX + MAP_RADIUS) / MAP_SIZE * width).toInt()
    }

    private fun worldToScreenZ(worldZ: Double): Int {
        val relativeZ = worldZ - origin.z
        return y + ((relativeZ + MAP_RADIUS) / MAP_SIZE * height).toInt()
    }

    fun refreshTerrain() {
        terrainTextureId?.let { Minecraft.getInstance().textureManager.release(it) }
        terrainTextureId = null
        terrainGenerated = false
    }

    fun selectMarker(index: Int) {
        selectedMarkerIndex = index
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        for (marker in markerPositions) {
            if (mouseX.toInt() in (marker.screenX - marker.markerSize)..(marker.screenX + marker.markerSize) &&
                mouseY.toInt() in (marker.screenY - marker.markerSize)..(marker.screenY + marker.markerSize)) {
                selectedMarkerIndex = marker.flatIndex
                onMarkerSelected?.invoke(marker.flatIndex)
                return true
            }
        }
        return false
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean = super.mouseReleased(mouseX, mouseY, button)

    override fun updateWidgetNarration(narrationElementOutput: NarrationElementOutput) {
        defaultButtonNarrationText(narrationElementOutput)
    }

    private fun abgrFromArgb(argb: Int): Int {
        val a = (argb shr 24) and 0xFF
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (a shl 24) or (b shl 16) or (g shl 8) or r
    }
}
