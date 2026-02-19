/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.pokeedit.widgets

import com.bedrockk.molang.runtime.value.DoubleValue
import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.api.tags.CobblemonItemTags
import com.cobblemon.mod.common.client.gui.drawProfilePokemon
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUIConstants.HALF_OVERLAY_HEIGHT
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUIConstants.HALF_OVERLAY_WIDTH
import com.cobblemon.mod.common.client.gui.pokeedit.PokeEditDraft
import com.cobblemon.mod.common.client.gui.pokeedit.widgets.PokeEditAspectsTab.Companion.choiceAspectId
import com.cobblemon.mod.common.client.gui.summary.widgets.SoundlessWidget
import com.cobblemon.mod.common.client.render.item.HeldItemRenderer
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.pokemon.Gender
import com.cobblemon.mod.common.pokemon.RenderablePokemon
import com.cobblemon.mod.common.pokemon.aspects.SHINY_ASPECT
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.math.fromEulerXYZDegrees
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import org.joml.Quaternionf
import org.joml.Vector3f

class PokeEditImplementationBlankTab(
    pX: Int,
    pY: Int,
    private val draft: PokeEditDraft,
    title: Component
) : SoundlessWidget(pX, pY, HALF_OVERLAY_WIDTH, HALF_OVERLAY_HEIGHT, title) {
    companion object {
        private val backgroundOverlay = cobblemonResource("textures/gui/pokedex/pokedex_screen_info_overlay.png")
        private val pokeBallOverlay = cobblemonResource("textures/gui/pokedex/pokedex_screen_poke_ball.png")
        private val platformBase = cobblemonResource("textures/gui/pokedex/platform_base.png")
        private val platformShadow = cobblemonResource("textures/gui/pokedex/platform_shadow.png")
        private const val POKEBALL_OVERLAY_Y = 25
        private const val PLATFORM_Y = 69
        private const val SHADOW_X = 25
        private const val SHADOW_Y = 76
        private const val VIEW_SCALE = 1.2F
        private const val VIEW_SHIFT_X = 0F
        private const val VIEW_SHIFT_Y = 36F
        private const val MODEL_VIEW_SCALE = 2.4F
        private const val MODEL_DRAW_SCALE = 24F
        private const val MODEL_OFFSET_Y = 20.0
    }

    private val state = FloatingState().also { state ->
        state.runtime.environment.query.addFunction("is_holding_item") {
            val held = currentHeldStack()
            DoubleValue(!held.isEmpty && !held.`is`(CobblemonItemTags.WEARABLE_HAT_ITEMS) && !held.`is`(CobblemonItemTags.WEARABLE_FACE_ITEMS))
        }
        state.runtime.environment.query.addFunction("is_wearing_hat") {
            DoubleValue(currentHeldStack().`is`(CobblemonItemTags.WEARABLE_HAT_ITEMS))
        }
        state.runtime.environment.query.addFunction("is_wearing_face") {
            DoubleValue(currentHeldStack().`is`(CobblemonItemTags.WEARABLE_FACE_ITEMS))
        }
    }
    private val heldItemRenderer = HeldItemRenderer()
    private var renderablePokemon: RenderablePokemon? = null
    private var rotationY = 30F
    private var poseList: Array<PoseType> = arrayOf(PoseType.PROFILE)
    private var selectedPoseIndex: Int = 0
    private var oldDragPosX = 0.0
    private var canDragRender = false
    private var isDraggingRender = false

    override fun renderWidget(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        context.enableScissor(x, y, x + HALF_OVERLAY_WIDTH, y + HALF_OVERLAY_HEIGHT)

        val viewWidth = HALF_OVERLAY_WIDTH * VIEW_SCALE
        val viewHeight = HALF_OVERLAY_HEIGHT * VIEW_SCALE
        val viewX = x + (HALF_OVERLAY_WIDTH - viewWidth) / 2F + VIEW_SHIFT_X
        val viewY = y + (HALF_OVERLAY_HEIGHT - viewHeight) / 2F + VIEW_SHIFT_Y
        val scaledBaseX = viewX / VIEW_SCALE
        val scaledBaseY = viewY / VIEW_SCALE

        blitk(
            matrixStack = context.pose(),
            texture = backgroundOverlay,
            x = scaledBaseX,
            y = scaledBaseY,
            width = HALF_OVERLAY_WIDTH,
            height = HALF_OVERLAY_HEIGHT,
            scale = VIEW_SCALE
        )

        blitk(
            matrixStack = context.pose(),
            texture = pokeBallOverlay,
            x = scaledBaseX + 15,
            y = scaledBaseY + POKEBALL_OVERLAY_Y,
            width = 109,
            height = 68,
            vOffset = 0,
            textureHeight = 1744,
            scale = VIEW_SCALE
        )

        blitk(
            matrixStack = context.pose(),
            texture = platformBase,
            x = scaledBaseX + 13,
            y = scaledBaseY + PLATFORM_Y,
            width = 113,
            height = 24,
            textureHeight = 30,
            scale = VIEW_SCALE
        )

        blitk(
            matrixStack = context.pose(),
            texture = platformShadow,
            x = scaledBaseX + SHADOW_X,
            y = scaledBaseY + SHADOW_Y,
            width = 90,
            height = 20,
            scale = VIEW_SCALE
        )

        val fillTop = (viewY + 114F * VIEW_SCALE).toInt()
        val fillBottom = (viewY + (HALF_OVERLAY_HEIGHT - 6) * VIEW_SCALE).toInt()
        context.fill(viewX.toInt(), fillTop, (viewX + viewWidth).toInt(), fillBottom, 0xFF7AEEF1.toInt())

        context.disableScissor()

        buildRenderable()?.let { renderable ->
            context.enableScissor(
                x + 1,
                y + 1,
                x + HALF_OVERLAY_WIDTH - 1,
                y + HALF_OVERLAY_HEIGHT - 1
            )

            val matrices = context.pose()
            matrices.pushPose()
            matrices.translate(
                x.toDouble() + (HALF_OVERLAY_WIDTH.toDouble()) / 2,
                y.toDouble() + MODEL_OFFSET_Y,
                1000.0
            )
            matrices.scale(MODEL_VIEW_SCALE, MODEL_VIEW_SCALE, MODEL_VIEW_SCALE)
            val rotationVector = Vector3f(13F, rotationY, 0F)

            drawProfilePokemon(
                renderablePokemon = renderable,
                poseType = poseList[selectedPoseIndex],
                matrixStack = matrices,
                partialTicks = delta,
                rotation = Quaternionf().fromEulerXYZDegrees(rotationVector),
                state = state,
                scale = MODEL_DRAW_SCALE
            )
            if (!renderable.heldItem.isEmpty) {
                heldItemRenderer.renderOnModel(
                    renderable.heldItem,
                    state,
                    matrices,
                    context.bufferSource(),
                    light = 0xF000F0,
                    frontLight = true
                )
            }
            matrices.popPose()
            context.disableScissor()
        }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (isWithinViewport(mouseX, mouseY)) {
            canDragRender = true
            isDraggingRender = true
            oldDragPosX = mouseX
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseReleased(pMouseX: Double, pMouseY: Double, pButton: Int): Boolean {
        if (canDragRender) {
            canDragRender = false
        }
        if (isDraggingRender) {
            isDraggingRender = false
        }
        return super.mouseReleased(pMouseX, pMouseY, pButton)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
        val handledDrag = isDraggingRender && canDragRender
        if (handledDrag) {
            val dragOffsetY = (oldDragPosX - mouseX).toFloat()
            rotationY = (((rotationY + dragOffsetY) % 360 + 360) % 360)
        }
        oldDragPosX = mouseX
        return handledDrag || super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
    }

    private fun buildRenderable(): RenderablePokemon? {
        val species = draft.species
        val form = draft.form
        val aspects = mutableSetOf<String>()
        if (draft.shiny) aspects.add(SHINY_ASPECT.aspect)
        aspects.addAll(form.aspects)
        aspects.addAll(draft.forcedAspects)
        aspects.addAll(draft.flagFeatures)
        aspects.addAll(draft.cosmeticAspects)
        draft.choiceFeatures.forEach { (key, choice) ->
            choiceAspectId(key, choice)?.let { aspects.add(it) }
        }
        when (draft.gender) {
            Gender.MALE -> aspects.add("male")
            Gender.FEMALE -> aspects.add("female")
            Gender.GENDERLESS -> aspects.add("genderless")
        }
        val heldStack = draft.heldItem
            ?.let { ResourceLocation.tryParse(it) }
            ?.let { id -> BuiltInRegistries.ITEM.getOptional(id).orElse(null) }
            ?.let { item -> ItemStack(item) }
            ?: ItemStack.EMPTY
        renderablePokemon = RenderablePokemon(species, aspects, heldStack).also { recalculatePoses(it) }
        return renderablePokemon
    }

    private fun recalculatePoses(renderablePokemon: RenderablePokemon) {
        val poseState = FloatingState().also { it.currentAspects = renderablePokemon.aspects }
        val poser = VaryingModelRepository.getPoser(renderablePokemon.species.resourceIdentifier, poseState)
        poseList = poser.poses
            .map { it.value.poseTypes.minBy { it.ordinal } }
            .toSet()
            .filterNot { it in PoseType.SHOULDER_POSES }
            .sortedBy { it.ordinal }
            .toTypedArray()
        if (selectedPoseIndex !in poseList.indices) {
            selectedPoseIndex = 0
        }
    }

    private fun currentHeldStack(): ItemStack {
        return renderablePokemon?.heldItem ?: ItemStack.EMPTY
    }

    private fun isWithinViewport(mouseX: Double, mouseY: Double): Boolean =
        mouseX.toInt() in x..(x + HALF_OVERLAY_WIDTH)
            && mouseY.toInt() in y..(y + HALF_OVERLAY_HEIGHT)
            && children.none { child ->
                child is AbstractWidget && child.visible && child.isMouseOver(mouseX, mouseY)
            }
}
