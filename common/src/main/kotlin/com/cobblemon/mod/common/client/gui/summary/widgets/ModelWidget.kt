/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.summary.widgets

import com.bedrockk.molang.runtime.value.DoubleValue
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.tags.CobblemonItemTags
import com.cobblemon.mod.common.client.gui.calculateHeadYawAndPitch
import com.cobblemon.mod.common.client.gui.drawLegacyLitProfilePokemon
import com.cobblemon.mod.common.client.render.item.HeldItemRenderer
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.pokemon.RenderablePokemon
import com.cobblemon.mod.common.util.math.fromEulerXYZDegrees
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

class ModelWidget(
    pX: Int, pY: Int,
    pWidth: Int, pHeight: Int,
    pokemon: RenderablePokemon,
    val baseScale: Float = 2.7F,
    var rotationY: Float = 35F,
    var offsetY: Double = 0.0,
    val playCryOnClick: Boolean = false,
    val shouldFollowCursor: Boolean = false,
    val blockLight: Int = 13
): SoundlessWidget(pX, pY, pWidth, pHeight, Component.literal("Summary - ModelWidget")) {

    companion object {
        var render = true
        const val MAX_SECONDS_LOOKING = 6L
        private const val SLOT_COMPARE_BOUNDS_X_PADDING = 4
        private const val SLOT_COMPARE_BOUNDS_TOP_PADDING = 8
        private const val SLOT_COMPARE_BOUNDS_BOTTOM_PADDING = 4
        private const val SLOT_COMPARE_ANCHOR_Y_OFFSET = -3F
        private const val SLOT_COMPARE_BASE_SCALE = 2.5F * 4.5F
        private const val SLOT_COMPARE_BASE_PORTRAIT_DIAMETER = 25F
        private const val LARGE_PREVIEW_ZOOM_MULTIPLIER = 1.0F
    }

    private data class PreviewGeometry(
        val mode: String,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val anchorX: Float,
        val anchorY: Float,
        val scale: Float
    )

    var pokemon: RenderablePokemon = pokemon
        set (value) {
            field = value
            currentYawAndPitch = Pair(0f, 0f)
            state = FloatingState()
            state.runtime.environment.query.addFunction("is_holding_item") { return@addFunction DoubleValue(field.heldItem.let {
               !it.isEmpty && !it.`is`(CobblemonItemTags.WEARABLE_HAT_ITEMS) && !it.`is`(CobblemonItemTags.WEARABLE_FACE_ITEMS)
            }) }
            state.runtime.environment.query.addFunction("is_wearing_hat") { return@addFunction DoubleValue(field.heldItem.`is`(CobblemonItemTags.WEARABLE_HAT_ITEMS)) }
            state.runtime.environment.query.addFunction("is_wearing_face") { return@addFunction DoubleValue(field.heldItem.`is`(CobblemonItemTags.WEARABLE_FACE_ITEMS)) }
        }

    var state = FloatingState()
    var lookStartTime: Long? = null
    var currentYawAndPitch: Pair<Float, Float> = Pair(0f, 0f)
    val rotationVector = Vector3f(13F, rotationY, 0F)

    override fun extractWidgetRenderState(context: GuiGraphicsExtractor, pMouseX: Int, pMouseY: Int, partialTicks: Float) {
        if (!render) {
            return
        }
        renderPKM(context, partialTicks, pMouseX, pMouseY)
    }

    private fun renderPKM(context: GuiGraphicsExtractor, partialTicks: Float, mouseX: Int, mouseY: Int) {
        context.enableScissor(x, y, x + width, y + height)

        if (isHovered) {
            lookStartTime = System.currentTimeMillis()
        }

        val timeLooking = lookStartTime?.let { (System.currentTimeMillis() - it) / 1000 } ?: MAX_SECONDS_LOOKING
        val lookedTooMuch = timeLooking >= MAX_SECONDS_LOOKING
        val resetToCenter = (!isHovered && lookedTooMuch) || !shouldFollowCursor || !Cobblemon.config.summaryPokemonFollowCursor

        if (resetToCenter) {
            lookStartTime = null
        }

        val rotation = Quaternionf().fromEulerXYZDegrees(rotationVector)

        if (shouldFollowCursor) {
            currentYawAndPitch = calculateHeadYawAndPitch(
                x + width / 2f,
                y + height / 2f,
                rotation,
                mouseX,
                mouseY,
                currentYawAndPitch.first,
                currentYawAndPitch.second,
                resetToCenter
            )
        }

        state.currentAspects = pokemon.aspects
        state.displayedHeldItem = pokemon.heldItem

        val geometry = chooseActiveGeometry(createExpandedLargeGeometry(portraitLargePadding()), createSlotComparisonGeometry())

        drawLegacyLitProfilePokemon(
            species = pokemon.species.resourceIdentifier,
            context = context,
            left = geometry.left,
            top = geometry.top,
            right = geometry.right,
            bottom = geometry.bottom,
            anchorX = geometry.anchorX,
            anchorY = geometry.anchorY,
            rotation = rotation,
            state = state.also {
                it.currentAspects = pokemon.aspects
                it.displayedHeldItem = pokemon.heldItem
            },
            partialTicks = partialTicks,
            scale = geometry.scale,
            headYaw = currentYawAndPitch.first,
            headPitch = currentYawAndPitch.second,
            blockLight = blockLight
        )

        context.disableScissor()
    }

    override fun mouseClicked(mouseButtonEvent: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (this.isHovered) {
            playCry()
        }

        return super.mouseClicked(mouseButtonEvent, doubleClick)
    }

    private fun playCry() {
        if (playCryOnClick) {
            state.activeAnimations.clear()
            state.addFirstAnimation(setOf("cry"))
        }
    }

    private fun createExpandedLargeGeometry(extraPadding: Int = 0): PreviewGeometry {
        val renderScale = 20F * baseScale
        val renderRadius = ceil((renderScale * 2.5F).toDouble()).toInt().coerceAtLeast(max(width, height) / 2)
        val anchorX = x + width / 2F
        val anchorY = y + offsetY.toFloat()
        return PreviewGeometry(
            mode = "expanded-large",
            left = floor(anchorX - renderRadius - extraPadding).toInt(),
            top = floor(anchorY - renderRadius - extraPadding).toInt(),
            right = ceil(anchorX + renderRadius + extraPadding).toInt(),
            bottom = ceil(anchorY + renderRadius + extraPadding).toInt(),
            anchorX = anchorX,
            anchorY = anchorY,
            scale = renderScale
        )
    }

    private fun createSlotComparisonGeometry(): PreviewGeometry {
        val compareScale = SLOT_COMPARE_BASE_SCALE * (width / SLOT_COMPARE_BASE_PORTRAIT_DIAMETER) * LARGE_PREVIEW_ZOOM_MULTIPLIER
        return PreviewGeometry(
            mode = "slot-compare",
            left = x - SLOT_COMPARE_BOUNDS_X_PADDING,
            top = y - SLOT_COMPARE_BOUNDS_TOP_PADDING,
            right = x + width + SLOT_COMPARE_BOUNDS_X_PADDING,
            bottom = y + height + SLOT_COMPARE_BOUNDS_BOTTOM_PADDING,
            anchorX = x + width / 2F,
            anchorY = y + SLOT_COMPARE_ANCHOR_Y_OFFSET,
            scale = compareScale
        )
    }

    private fun chooseActiveGeometry(defaultGeometry: PreviewGeometry, slotComparisonGeometry: PreviewGeometry): PreviewGeometry {
        return defaultGeometry
    }

    private fun portraitLargePadding(): Int {
        return 0
    }
}
