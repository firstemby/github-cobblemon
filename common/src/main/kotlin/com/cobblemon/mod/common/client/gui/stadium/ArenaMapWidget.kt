/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.stadium

import com.cobblemon.mod.common.battles.stadium.StadiumBlockLayout
import com.cobblemon.mod.common.battles.stadium.StadiumArena
import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.language.I18n
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3

class ArenaMapWidget(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    private val origin: BlockPos,
    private val layout: StadiumBlockLayout?,
    private val arena: StadiumArena? = null
) : AbstractWidget(x, y, width, height, Component.translatable("cobblemon.stadium.ui.arena_terrain_map")) {

    companion object {

        private const val ORIGIN_COLOR = 0xFFFFFF00.toInt()
        private const val SIDE1_ACTOR_COLOR = 0xFF4488FF.toInt()
        private const val SIDE1_POKEMON_COLOR = 0xFF44DDFF.toInt()
        private const val SIDE2_ACTOR_COLOR = 0xFFFF4444.toInt()
        private const val SIDE2_POKEMON_COLOR = 0xFFFF8844.toInt()
        private const val UNLOADED_COLOR = 0xFF808080.toInt()
    }

    private val mapRadius = arena?.let {
        ceil(maxOf(abs(it.minX - origin.x), abs(it.maxX - origin.x),
            abs(it.minZ - origin.z), abs(it.maxZ - origin.z)) + 4).toInt().coerceIn(32, 96)
    } ?: 32
    private val mapSize = mapRadius * 2
    private var firstCorner: Vec3? = null
    var onAreaSelected: ((StadiumArena) -> Unit)? = null

    private var terrainTextureId: ResourceLocation? = null
    private var terrainGenerated = false
    private var hoveredMarkerTooltip: Component? = null

    fun generateTerrainMap() {
        val level = Minecraft.getInstance().level ?: return
        if (terrainGenerated) return

        val image = NativeImage(mapSize, mapSize, true)
        val centerX = origin.x
        val centerZ = origin.z

        try {
            for (px in 0 until mapSize) {
                for (pz in 0 until mapSize) {
                    val worldX = centerX - mapRadius + px
                    val worldZ = centerZ - mapRadius + pz
                    val blockPos = BlockPos(worldX, 0, worldZ)

                    val color = if (level.hasChunk(blockPos.x shr 4, blockPos.z shr 4)) {
                        getTerrainColor(level, worldX, worldZ)
                    } else {
                        UNLOADED_COLOR
                    }

                    image.setPixelRGBA(px, pz, abgrFromArgb(color))
                }
            }
        } catch (exception: Exception) {
            image.close()
            throw exception
        }

        val dynamicTexture = try {
            DynamicTexture(image)
        } catch (exception: Exception) {
            image.close()
            throw exception
        }
        val textureManager = Minecraft.getInstance().textureManager
        try {
            terrainTextureId?.let { textureManager.release(it) }
            terrainTextureId = textureManager.register("stadium_arena_map", dynamicTexture)
            terrainGenerated = true
        } catch (exception: Exception) {
            dynamicTexture.close()
            throw exception
        }
    }

    private fun getTerrainColor(level: Level, worldX: Int, worldZ: Int): Int {
        val topY = level.getHeight(Heightmap.Types.WORLD_SURFACE, worldX, worldZ)
        val pos = BlockPos(worldX, topY - 1, worldZ)
        val state = level.getBlockState(pos)

        if (state.isAir || state.`is`(Blocks.VOID_AIR)) {
            return 0xFF333333.toInt()
        }

        val mapColor = state.getMapColor(level, pos)
        val baseColor = mapColor.col
        if (baseColor == 0) return 0xFF333333.toInt()

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

        terrainTextureId?.let { texId ->
            guiGraphics.blit(texId, x, y, 0f, 0f, width, height, width, height)
        } ?: run {
            guiGraphics.fill(x, y, x + width, y + height, 0xFF333333.toInt())
        }

        guiGraphics.renderOutline(x, y, width, height, 0xFF5C5C7A.toInt())

        val originScreenX = x + width / 2
        val originScreenY = y + height / 2
        drawDiamond(guiGraphics, originScreenX, originScreenY, ORIGIN_COLOR)

        guiGraphics.enableScissor(x, y, x + width, y + height)
        try {
            arena?.let {
                guiGraphics.renderOutline(worldToScreenX(it.minX), worldToScreenZ(it.minZ),
                    worldToScreenX(it.maxX) - worldToScreenX(it.minX), worldToScreenZ(it.maxZ) - worldToScreenZ(it.minZ), ORIGIN_COLOR)
            }
            firstCorner?.let { corner ->
                val cx = worldToScreenX(corner.x); val cz = worldToScreenZ(corner.z)
                guiGraphics.renderOutline(minOf(cx, mouseX), minOf(cz, mouseY), abs(mouseX - cx).coerceAtLeast(1),
                    abs(mouseY - cz).coerceAtLeast(1), 0xFFFFFFFF.toInt())
            }
            layout?.let { drawLayoutMarkers(guiGraphics, it, mouseX, mouseY) }
        } finally {
            guiGraphics.disableScissor()
        }

        hoveredMarkerTooltip?.let {
            guiGraphics.renderTooltip(Minecraft.getInstance().font, it, mouseX, mouseY)
        }
    }

    private fun drawLayoutMarkers(guiGraphics: GuiGraphics, layout: StadiumBlockLayout, mouseX: Int, mouseY: Int) {

        for ((i, placement) in layout.side1.actors.withIndex()) {
            val label = I18n.get("cobblemon.stadium.ui.trainer_slot", 1, i + 1)
            drawMarker(guiGraphics, placement.position, SIDE1_ACTOR_COLOR, label, mouseX, mouseY)
        }
        for ((i, placement) in layout.side1.pokemon.withIndex()) {
            val label = I18n.get("cobblemon.stadium.ui.pokemon_slot", 1, i + 1)
            drawMarker(guiGraphics, placement.position, SIDE1_POKEMON_COLOR, label, mouseX, mouseY)
        }
        for ((i, placement) in layout.side2.actors.withIndex()) {
            val label = I18n.get("cobblemon.stadium.ui.trainer_slot", 2, i + 1)
            drawMarker(guiGraphics, placement.position, SIDE2_ACTOR_COLOR, label, mouseX, mouseY)
        }
        for ((i, placement) in layout.side2.pokemon.withIndex()) {
            val label = I18n.get("cobblemon.stadium.ui.pokemon_slot", 2, i + 1)
            drawMarker(guiGraphics, placement.position, SIDE2_POKEMON_COLOR, label, mouseX, mouseY)
        }
    }

    private fun drawMarker(guiGraphics: GuiGraphics, worldPos: Vec3, color: Int, label: String, mouseX: Int, mouseY: Int) {
        val screenX = worldToScreenX(worldPos.x)
        val screenY = worldToScreenZ(worldPos.z)

        if (screenX < x || screenX >= x + width || screenY < y || screenY >= y + height) return

        val markerSize = 3
        guiGraphics.fill(screenX - markerSize, screenY - markerSize, screenX + markerSize, screenY + markerSize, color)
        guiGraphics.renderOutline(screenX - markerSize, screenY - markerSize, markerSize * 2, markerSize * 2, 0xFF000000.toInt())

        if (mouseX in (screenX - markerSize)..(screenX + markerSize) &&
            mouseY in (screenY - markerSize)..(screenY + markerSize)) {
            hoveredMarkerTooltip = Component.translatable("cobblemon.stadium.map.marker", label,
                "%.1f".format(Locale.ROOT, worldPos.x), "%.1f".format(Locale.ROOT, worldPos.y), "%.1f".format(Locale.ROOT, worldPos.z))
        }
    }

    private fun drawDiamond(guiGraphics: GuiGraphics, cx: Int, cy: Int, color: Int) {
        guiGraphics.fill(cx - 1, cy - 2, cx + 2, cy - 1, color)
        guiGraphics.fill(cx - 2, cy - 1, cx + 3, cy + 2, color)
        guiGraphics.fill(cx - 1, cy + 2, cx + 2, cy + 3, color)
    }

    private fun worldToScreenX(worldX: Double): Int {
        val relativeX = worldX - origin.x
        return x + ((relativeX + mapRadius) / mapSize * width).toInt()
    }

    private fun worldToScreenZ(worldZ: Double): Int {
        val relativeZ = worldZ - origin.z
        return y + ((relativeZ + mapRadius) / mapSize * height).toInt()
    }

    fun refreshTerrain() {
        terrainTextureId?.let { Minecraft.getInstance().textureManager.release(it) }
        terrainTextureId = null
        terrainGenerated = false
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (!visible || !active || !isMouseOver(mouseX, mouseY)) return false
        if (button == 1) { firstCorner = null; return true }
        if (button != 0) return false
        if (onAreaSelected != null) {
            val point = Vec3(floor(origin.x - mapRadius + (mouseX - x) / width * mapSize),
                arena?.center?.y ?: origin.y + 1.0,
                floor(origin.z - mapRadius + (mouseY - y) / height * mapSize))
            val first = firstCorner
            if (first == null) firstCorner = point else {
                firstCorner = null
                onAreaSelected?.invoke(StadiumArena(first.add(point).scale(0.5),
                    abs(point.x - first.x), abs(point.z - first.z)))
            }
            return true
        }
        return false
    }

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
