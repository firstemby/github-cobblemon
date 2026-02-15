/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.pokeedit.widgets

import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.api.pokemon.stats.Stat
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.api.text.bold
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.client.gui.pokeedit.PokeEditDraft
import com.cobblemon.mod.common.client.gui.summary.widgets.SoundlessWidget
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.pokemon.EVs
import com.cobblemon.mod.common.pokemon.IVs
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.lang
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.MutableComponent
import net.minecraft.world.phys.Vec2
import org.joml.Vector3f

class PokeEditEVIVTab(
    pX: Int,
    pY: Int,
    private val draft: PokeEditDraft
) : SoundlessWidget(pX, pY, WIDTH, HEIGHT, lang("ui.pokeedit.stats")) {
    companion object {
        private const val WIDTH = 139
        private const val HEIGHT = 163
        private const val CHART_WIDTH = 134
        private const val CHART_HEIGHT = 148
        private const val SCALE = 0.5F
        private const val TAB_MARKER_WIDTH = 8

        private const val TAB_IV = 0
        private const val TAB_EV = 1

        private val statsBaseResource = cobblemonResource("textures/gui/summary/summary_stats_chart_base.png")
        private val statsChartResource = cobblemonResource("textures/gui/summary/summary_stats_chart.png")
        private val tabMarkerResource = cobblemonResource("textures/gui/summary/summary_stats_tab_marker.png")

        private val statLabels = listOf(
            lang("ui.stats.hp").bold() to Stats.HP,
            lang("ui.stats.atk").bold() to Stats.ATTACK,
            lang("ui.stats.def").bold() to Stats.DEFENCE,
            lang("ui.stats.speed").bold() to Stats.SPEED,
            lang("ui.stats.sp_def").bold() to Stats.SPECIAL_DEFENCE,
            lang("ui.stats.sp_atk").bold() to Stats.SPECIAL_ATTACK
        )

        private val hexagonVerticesOffset = listOf(
            Pair(67.0, 10.5), // 12 o'clock
            Pair(122.0, 42.5), // 2 o'clock
            Pair(122.0, 93.5), // 4 o'clock
            Pair(67.0, 124.5), // 6 o'clock
            Pair(12.0, 93.5), // 8 o'clock
            Pair(12.0, 42.5) // 10 o'clock
        )
    }

    private var tabIndex = TAB_IV

    override fun renderWidget(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        val chartX = x + 2
        val chartY = y + 6

        blitk(
            matrixStack = context.pose(),
            texture = statsBaseResource,
            x = chartX,
            y = chartY,
            width = CHART_WIDTH,
            height = CHART_HEIGHT
        )

        blitk(
            matrixStack = context.pose(),
            texture = statsChartResource,
            x = (chartX + 25.5) / SCALE,
            y = (chartY + 22) / SCALE,
            width = 166,
            height = 192,
            scale = SCALE
        )

        if (tabIndex == TAB_IV) {
            drawStatPolygon(
                chartX.toFloat(),
                chartY.toFloat(),
                statLabels.map { (_, stat) -> draft.ivs.getOrDefault(stat).toFloat() / IVs.MAX_VALUE.toFloat() },
                colour = Vector3f(216F / 255F, 100F / 255F, 1F)
            )
        } else {
            drawStatPolygon(
                chartX.toFloat(),
                chartY.toFloat(),
                statLabels.map { (_, stat) -> draft.evs.getOrDefault(stat).toFloat() / EVs.MAX_STAT_VALUE.toFloat() },
                colour = Vector3f(1F, 1F, 100F / 255F)
            )
        }

        val labelsHovered = statLabelsHovered(chartX, chartY, mouseX, mouseY)

        val labels = statLabels.map { (label, stat) ->
            if (labelsHovered) {
                if (tabIndex == TAB_IV) {
                    "${draft.ivs.getOrDefault(stat)}/${IVs.MAX_VALUE}".text()
                } else {
                    "${draft.evs.getOrDefault(stat)}/${EVs.MAX_STAT_VALUE}".text()
                }
            } else {
                label
            }
        }

        renderPolygonLabels(
            context = context,
            chartX = chartX.toDouble(),
            chartY = chartY.toDouble(),
            labels = labels,
            verticesOffset = hexagonVerticesOffset
        )

        val values = statLabels.map { (_, stat) ->
            if (tabIndex == TAB_IV) {
                draft.ivs.getOrDefault(stat).toString().text()
            } else {
                draft.evs.getOrDefault(stat).toString().text()
            }
        }

        renderPolygonLabels(
            context = context,
            chartX = chartX.toDouble(),
            chartY = chartY.toDouble(),
            labels = values,
            verticesOffset = hexagonVerticesOffset,
            offsetY = 5.5
        )

        if (tabIndex == TAB_EV) {
            drawScaledText(
                context = context,
                text = lang("ui.pokeedit.evs.total", draft.evs.total(), EVs.MAX_TOTAL_VALUE),
                x = chartX + 10,
                y = chartY + 126,
                scale = SCALE,
                shadow = true
            )
        }

        renderTabs(context, chartX, chartY)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val tabIndexAtPos = getTabIndexFromPos(mouseX, mouseY)
        if (tabIndexAtPos != -1 && tabIndexAtPos != tabIndex) {
            tabIndex = tabIndexAtPos
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        val chartX = x + 2
        val chartY = y + 6
        val stat = getHoveredStat(chartX, chartY, mouseX.toInt(), mouseY.toInt()) ?: return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
        val step = when {
            Screen.hasControlDown() -> 25
            Screen.hasShiftDown() -> 10
            else -> 1
        }
        val delta = if (verticalAmount > 0) step else -step
        if (tabIndex == TAB_IV) {
            val current = draft.ivs.getOrDefault(stat)
            val next = (current + delta).coerceIn(0, IVs.MAX_VALUE)
            draft.ivs[stat] = next
        } else {
            val current = draft.evs.getOrDefault(stat)
            val totalWithout = draft.evs.total() - current
            val maxForStat = minOf(EVs.MAX_STAT_VALUE, EVs.MAX_TOTAL_VALUE - totalWithout)
            val next = (current + delta).coerceIn(0, maxForStat)
            draft.evs[stat] = next
        }
        return true
    }

    private fun renderTabs(context: GuiGraphics, chartX: Int, chartY: Int) {
        val tabWidth = 40
        val tabLeft = chartX + ((CHART_WIDTH - (tabWidth * 2)) / 2)
        val tabCenters = listOf(
            tabLeft + (tabWidth / 2),
            tabLeft + tabWidth + (tabWidth / 2)
        )
        val tabY = chartY + 137

        drawScaledText(
            context = context,
            text = lang("ui.stats.ivs").bold(),
            x = tabCenters[0],
            y = tabY,
            scale = SCALE,
            colour = if (tabIndex == TAB_IV) 0x00FFFFFF else 0x008F8F8F,
            centered = true
        )
        drawScaledText(
            context = context,
            text = lang("ui.stats.evs").bold(),
            x = tabCenters[1],
            y = tabY,
            scale = SCALE,
            colour = if (tabIndex == TAB_EV) 0x00FFFFFF else 0x008F8F8F,
            centered = true
        )

        blitk(
            matrixStack = context.pose(),
            texture = tabMarkerResource,
            x = (tabCenters[tabIndex] - (TAB_MARKER_WIDTH / 2)) / SCALE,
            y = (tabY - 3) / SCALE,
            width = TAB_MARKER_WIDTH,
            height = 4,
            scale = SCALE
        )
    }

    private fun getTabIndexFromPos(mouseX: Double, mouseY: Double): Int {
        val chartX = x + 2
        val chartY = y + 6
        val tabWidth = 40
        val tabLeft = chartX + ((CHART_WIDTH - (tabWidth * 2)) / 2)
        val tabTop = chartY + 134.0
        val tabBottom = tabTop + 9.0
        if (mouseY !in tabTop..tabBottom) return -1
        if (mouseX in tabLeft.toDouble()..(tabLeft + tabWidth).toDouble()) return TAB_IV
        if (mouseX in (tabLeft + tabWidth).toDouble()..(tabLeft + tabWidth * 2).toDouble()) return TAB_EV
        return -1
    }

    private fun getHoveredStat(chartX: Int, chartY: Int, mouseX: Int, mouseY: Int): Stat? {
        statLabels.forEachIndexed { index, (_, stat) ->
            val pos = hexagonVerticesOffset[index]
            if (mouseX.toDouble() in (chartX + pos.first - 12)..(chartX + pos.first + 12)
                && mouseY.toDouble() in (chartY + pos.second - 1)..(chartY + pos.second + 10)
            ) {
                return stat
            }
        }
        return null
    }

    private fun statLabelsHovered(chartX: Int, chartY: Int, mouseX: Int, mouseY: Int): Boolean {
        return getHoveredStat(chartX, chartY, mouseX, mouseY) != null
    }

    private fun renderPolygonLabels(
        context: GuiGraphics,
        chartX: Double,
        chartY: Double,
        labels: List<MutableComponent>,
        verticesOffset: List<Pair<Double, Double>>,
        offsetY: Double = 0.0
    ) {
        if (labels.size != verticesOffset.size) return
        labels.forEachIndexed { index, label ->
            drawScaledText(
                context = context,
                text = label,
                x = chartX + verticesOffset[index].first,
                y = chartY + verticesOffset[index].second + offsetY,
                scale = SCALE,
                centered = true
            )
        }
    }

    private fun drawTriangle(colour: Vector3f, v1: Vec2, v2: Vec2, v3: Vec2, opacity: Float = 0.6F) {
        RenderSystem.setShaderTexture(0, com.cobblemon.mod.common.client.CobblemonResources.WHITE)
        RenderSystem.setShaderColor(colour.x, colour.y, colour.z, opacity)
        val bufferBuilder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION)
        bufferBuilder.addVertex(v1.x, v1.y, 10F)
        bufferBuilder.addVertex(v2.x, v2.y, 10F)
        bufferBuilder.addVertex(v3.x, v3.y, 10F)
        BufferUploader.drawWithShader(bufferBuilder.buildOrThrow())
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F)
    }

    private fun drawStatPolygon(chartX: Float, chartY: Float, ratios: List<Float>, colour: Vector3f) {
        if (ratios.size != 6) return
        val topVertexY = chartY + 22F
        val centerX = chartX + 67F
        val centerY = topVertexY + 48F
        val radius = centerY - topVertexY

        val startAngleDeg = -90.0
        val angleStepDeg = 360.0 / 6
        val anglesDeg = List(6) { i -> startAngleDeg + i * angleStepDeg }
        val anglesRad = anglesDeg.map { Math.toRadians(it) }

        val maxPoints = anglesRad.map { angle ->
            Vec2(
                centerX + radius * Math.cos(angle).toFloat(),
                centerY + radius * Math.sin(angle).toFloat()
            )
        }

        val coercedRatios = ratios.map { it.coerceIn(5 / radius, 1F) }
        val vertices = maxPoints.mapIndexed { index, maxPoint ->
            Vec2(
                centerX + (maxPoint.x - centerX) * coercedRatios[index],
                centerY + (maxPoint.y - centerY) * coercedRatios[index]
            )
        }

        val centerPoint = Vec2(centerX, centerY)
        RenderSystem.disableDepthTest()
        for (i in vertices.indices) {
            val nextIndex = (i + 1) % vertices.size
            drawTriangle(colour, vertices[i], centerPoint, vertices[nextIndex])
        }
        RenderSystem.enableDepthTest()
    }
}
