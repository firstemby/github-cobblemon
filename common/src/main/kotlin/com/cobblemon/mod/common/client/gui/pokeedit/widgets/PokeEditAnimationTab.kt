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
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUIConstants.HALF_OVERLAY_HEIGHT
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUIConstants.HALF_OVERLAY_WIDTH
import com.cobblemon.mod.common.client.gui.pokeedit.PokeEditDraft
import com.cobblemon.mod.common.client.gui.pokeedit.widgets.PokeEditAspectsTab.Companion.choiceAspectId
import com.cobblemon.mod.common.client.gui.summary.widgets.SoundlessWidget
import com.cobblemon.mod.common.client.render.item.HeldItemRenderer
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext
import com.cobblemon.mod.common.client.render.models.blockbench.animation.ActiveAnimation
import com.cobblemon.mod.common.client.render.models.blockbench.animation.PrimaryAnimation
import com.cobblemon.mod.common.client.render.models.blockbench.pose.Pose
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.pokemon.Gender
import com.cobblemon.mod.common.pokemon.RenderablePokemon
import com.cobblemon.mod.common.pokemon.aspects.SHINY_ASPECT
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.math.fromEulerXYZDegrees
import com.cobblemon.mod.common.util.toHex
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.platform.Lighting
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.client.Minecraft
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import org.joml.Quaternionf
import org.joml.Vector3f

class PokeEditAnimationTab(
    pX: Int,
    pY: Int,
    private val draft: PokeEditDraft,
    private val onAnimationsChanged: (List<String>) -> Unit
) : SoundlessWidget(pX, pY, HALF_OVERLAY_WIDTH, HALF_OVERLAY_HEIGHT, Component.literal("Animation")) {
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
    private var poseList: Array<PoseType> = arrayOf(PoseType.PROFILE, PoseType.STAND, PoseType.WALK, PoseType.SLEEP)
    private var selectedPoseIndex: Int = 0
    private var oldDragPosX = 0.0
    private var canDragRender = false
    private var isDraggingRender = false

    private var availableAnimations: List<String> = emptyList()
    private val activeAnimationsByName = mutableMapOf<String, ActiveAnimation>()
    private val loopingAnimations = mutableMapOf<String, Boolean>()
    private val animationStartTimes = mutableMapOf<String, Float>()
    private val animationDurations = mutableMapOf<String, Float>()

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

        renderPokemon(context, delta)
        refreshAnimationList()
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

    fun getAnimations(): List<String> = availableAnimations

    fun isAnimationPlaying(name: String): Boolean = activeAnimationsByName.containsKey(name)

    fun isAnimationLooping(name: String): Boolean = loopingAnimations[name] == true

    fun getAnimationDuration(name: String): Float {
        return animationDurations[name] ?: resolveDuration(name).also { animationDurations[name] = it }
    }

    fun getAnimationProgress(name: String): Float {
        val duration = getAnimationDuration(name)
        if (duration <= 0F) return 0F
        val start = animationStartTimes[name] ?: return 0F
        val elapsed = state.animationSeconds - start
        val raw = if (isAnimationLooping(name)) {
            (elapsed % duration) / duration
        } else {
            elapsed / duration
        }
        return raw.coerceIn(0F, 1F)
    }

    fun playAnimation(name: String) {
        if (activeAnimationsByName.containsKey(name)) return
        val model = VaryingModelRepository.getPoser(draft.species.resourceIdentifier, state)
        val animation = model.getAnimation(state, name, state.runtime)
        val active = if (animation != null) {
            if (animation is PrimaryAnimation) animation.animation else animation
        } else {
            val pose = resolvePoseAnimation(model, name) ?: return
            PoseActiveAnimation(pose)
        }
        state.addActiveAnimation(active)
        activeAnimationsByName[name] = active
        animationStartTimes[name] = state.animationSeconds
        animationDurations[name] = active.duration
    }

    fun pauseAnimation(name: String) {
        val active = activeAnimationsByName.remove(name) ?: return
        state.activeAnimations.remove(active)
        animationStartTimes.remove(name)
    }

    fun setAnimationLooping(name: String, enabled: Boolean) {
        loopingAnimations[name] = enabled
    }

    private fun refreshAnimationList() {
        val model = VaryingModelRepository.getPoser(draft.species.resourceIdentifier, state)
        val names = mutableSetOf<String>()
        names.addAll(model.animations.keys)
        model.poses.values.forEach { pose ->
            names.addAll(pose.namedAnimations.keys)
            names.add(pose.poseName)
        }
        val sorted = names.sorted()
        if (sorted != availableAnimations) {
            availableAnimations = sorted
            loopingAnimations.keys.retainAll(sorted.toSet())
            animationDurations.keys.retainAll(sorted.toSet())
            animationStartTimes.keys.retainAll(sorted.toSet())
            onAnimationsChanged(sorted)
        }
    }

    private fun renderPokemon(context: GuiGraphics, delta: Float) {
        buildRenderable()?.let { renderable ->
            state.currentAspects = renderable.aspects
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

            renderAnimationModel(context, renderable, delta)

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

    private fun renderAnimationModel(context: GuiGraphics, renderable: RenderablePokemon, delta: Float) {
        val speciesId = renderable.species.resourceIdentifier
        val model = VaryingModelRepository.getPoser(speciesId, state)
        val texture = VaryingModelRepository.getTexture(speciesId, state)

        val renderContext = RenderContext()
        model.context = renderContext
        VaryingModelRepository.getTextureNoSubstitute(speciesId, state).let { renderContext.put(RenderContext.TEXTURE, it) }
        val baseScale = PokemonSpecies.getByIdentifier(speciesId)!!.getForm(state.currentAspects).baseScale
        renderContext.put(RenderContext.SCALE, baseScale)
        renderContext.put(RenderContext.SPECIES, speciesId)
        renderContext.put(RenderContext.ASPECTS, state.currentAspects)
        renderContext.put(RenderContext.RENDER_STATE, RenderContext.RenderState.PROFILE)
        renderContext.put(RenderContext.POSABLE_STATE, state)
        renderContext.put(RenderContext.DO_QUIRKS, false)

        state.currentModel = model
        if (state.currentPose == null) {
            state.currentPose = model.getPose(PoseType.PROFILE)?.poseName
        }
        state.updatePartialTicks(delta)

        RenderSystem.applyModelViewMatrix()
        val matrices = context.pose()
        val rotationVector = Vector3f(13F, rotationY, 0F)
        val rotation = Quaternionf().fromEulerXYZDegrees(rotationVector)

        matrices.pushPose()
        matrices.scale(MODEL_DRAW_SCALE, MODEL_DRAW_SCALE, -MODEL_DRAW_SCALE)
        matrices.translate(
            model.profileTranslation.x,
            model.profileTranslation.y + 1.5 * model.profileScale,
            model.profileTranslation.z - 4.0
        )
        matrices.scale(model.profileScale, model.profileScale, 1 / model.profileScale)
        matrices.mulPose(rotation)

        model.setDefault()
        state.preRender()
        state.renderMarkers.clear()
        runActiveAnimations(renderContext, model)
        model.updateLocators(null, state)

        Lighting.setupForEntityInInventory()
        val bufferSource = Minecraft.getInstance().renderBuffers().bufferSource()
        val buffer = bufferSource.getBuffer(RenderType.entityCutout(texture))
        val light1 = Vector3f(-1F, 1F, 1.0F)
        val light2 = Vector3f(1.3F, -1F, 1.0F)
        RenderSystem.setShaderLights(light1, light2)
        val packedLight = LightTexture.pack(11, 7)

        val colour = toHex(1F, 1F, 1F, 1F)
        model.withLayerContext(bufferSource, state, VaryingModelRepository.getLayers(speciesId, state)) {
            model.render(renderContext, matrices, buffer, packedLight, OverlayTexture.NO_OVERLAY, colour)
            bufferSource.endBatch()
        }
        model.setDefault()
        Lighting.setupFor3DItems()
        matrices.popPose()
    }

    private fun runActiveAnimations(context: RenderContext, model: com.cobblemon.mod.common.client.render.models.blockbench.PosableModel) {
        val toRestart = mutableListOf<String>()
        val iterator = activeAnimationsByName.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val animation = entry.value
            val stillRunning = animation.run(
                context,
                model,
                state,
                0F,
                0F,
                state.animationSeconds,
                0F,
                0F,
                1F
            )
            if (!stillRunning) {
                state.activeAnimations.remove(animation)
                iterator.remove()
                animationStartTimes.remove(entry.key)
                if (loopingAnimations[entry.key] == true) {
                    toRestart.add(entry.key)
                }
            }
        }

        if (toRestart.isNotEmpty()) {
            toRestart.forEach { name ->
                val animation = model.getAnimation(state, name, state.runtime)
                val active = if (animation != null) {
                    if (animation is PrimaryAnimation) animation.animation else animation
                } else {
                    val pose = resolvePoseAnimation(model, name) ?: return@forEach
                    PoseActiveAnimation(pose)
                }
                state.addActiveAnimation(active)
                activeAnimationsByName[name] = active
                animationStartTimes[name] = state.animationSeconds
                animationDurations[name] = active.duration
            }
        }
    }

    private fun resolvePoseAnimation(
        model: com.cobblemon.mod.common.client.render.models.blockbench.PosableModel,
        name: String
    ): Pose? {
        return model.poses[name] ?: model.poses.values.firstOrNull { it.poseName.equals(name, ignoreCase = true) }
    }

    private fun resolveDuration(name: String): Float {
        val model = VaryingModelRepository.getPoser(draft.species.resourceIdentifier, state)
        val animation = model.getAnimation(state, name, state.runtime)
        val active = if (animation != null) {
            if (animation is PrimaryAnimation) animation.animation else animation
        } else {
            return 0F
        }
        return active.duration
    }

    private class PoseActiveAnimation(private val pose: Pose) : ActiveAnimation {
        override val isTransition: Boolean = false
        override val duration: Float = 0F
        override val enduresPrimaryAnimations: Boolean = true

        override fun run(
            context: RenderContext,
            model: com.cobblemon.mod.common.client.render.models.blockbench.PosableModel,
            state: com.cobblemon.mod.common.client.render.models.blockbench.PosableState,
            limbSwing: Float,
            limbSwingAmount: Float,
            ageInTicks: Float,
            headYaw: Float,
            headPitch: Float,
            intensity: Float
        ): Boolean {
            if (!pose.isSuitable(state)) return true
            pose.apply(context, model, state, limbSwing, limbSwingAmount, ageInTicks, headYaw, headPitch)
            return true
        }
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

        val prev = renderablePokemon
        if (prev == null || prev.species != species || prev.aspects != aspects) {
            activeAnimationsByName.clear()
            state.activeAnimations.clear()
        }
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
