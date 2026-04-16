/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui

import com.cobblemon.mod.common.client.render.pokemon.LegacyGuiPreviewLights
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.client.render.SpriteType
import com.cobblemon.mod.common.client.render.item.HeldItemRenderer
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository
import com.cobblemon.mod.common.client.render.pokemon.PokemonRenderState
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.RenderablePokemon
import com.cobblemon.mod.common.util.math.toEulerXYZDegrees
import com.cobblemon.mod.common.util.toHex
import com.mojang.blaze3d.ProjectionType
import com.mojang.blaze3d.platform.Lighting
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.textures.GpuTextureView
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.render.TextureSetup
import com.cobblemon.mod.common.client.renderer.CachedOrthoProjectionMatrixBuffer
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.client.renderer.state.gui.BlitRenderState
import net.minecraft.client.renderer.state.gui.GuiRenderState
import net.minecraft.resources.Identifier
import net.minecraft.util.LightCoordsUtil
import net.minecraft.util.Mth
import org.joml.Matrix3x2fStack
import org.joml.Matrix3x2f
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.max

private val guiRenderStateClass by lazy {
    GuiRenderState::class.java
}

private val pokemonGuiGraphicsCtor by lazy {
    GuiGraphicsExtractor::class.java.getDeclaredConstructor(
        Minecraft::class.java,
        guiRenderStateClass,
        Int::class.javaPrimitiveType,
        Int::class.javaPrimitiveType
    ).apply { isAccessible = true }
}

private val pokemonGuiScissorStackField by lazy {
    GuiGraphicsExtractor::class.java.getDeclaredField("scissorStack").apply { isAccessible = true }
}

private val pokemonGuiScissorPeekMethod by lazy {
    Class.forName("net.minecraft.client.gui.GuiGraphicsExtractor\$ScissorStack").getDeclaredMethod("peek").apply { isAccessible = true }
}

private fun createMatrixGuiGraphics(matrixStack: Matrix3x2fStack): GuiGraphicsExtractor {
    val minecraft = Minecraft.getInstance()
    val window = minecraft.window
    val mouse = minecraft.mouseHandler
    val guiRenderState = minecraft.gameRenderer.getGameRenderState().guiRenderState
    val graphics = pokemonGuiGraphicsCtor.newInstance(
        minecraft,
        guiRenderState,
        mouse.getScaledXPos(window).toInt(),
        mouse.getScaledYPos(window).toInt()
    ) as GuiGraphicsExtractor
    graphics.pose().set(matrixStack)
    return graphics
}

private fun currentScissorBounds(context: GuiGraphicsExtractor): ScreenRectangle? {
    val scissorStack = pokemonGuiScissorStackField.get(context) ?: return null
    return pokemonGuiScissorPeekMethod.invoke(scissorStack) as? ScreenRectangle
}

private fun guiScale(matrixStack: Matrix3x2fStack): Float {
    return max(abs(matrixStack.m00()), abs(matrixStack.m11())).coerceAtLeast(1.0E-3F)
}

private fun poseToGuiMatrix(matrixStack: PoseStack): Matrix3x2fStack {
    val pose = matrixStack.last().pose()
    return Matrix3x2fStack(16).apply {
        translate(pose.m30(), pose.m31())
        scale(pose.m00(), pose.m11())
    }
}

private val legacyHeldItemRenderer = HeldItemRenderer()

private fun createGuiRenderState(
    species: Identifier,
    poseType: PoseType,
    state: PosableState,
    partialTicks: Float,
    applyProfileTransform: Boolean,
    applyBaseScale: Boolean,
    doQuirks: Boolean,
    headYaw: Float,
    headPitch: Float,
    blockLight: Int
): PokemonRenderState? {
    val minecraft = Minecraft.getInstance()
    val level = minecraft.level ?: return null
    val pokemonSpecies = PokemonSpecies.getByIdentifier(species) ?: return null
    state.currentModel = VaryingModelRepository.getPoser(species, state)
    state.setPoseToFirstSuitable(poseType)
    state.updatePartialTicks(partialTicks)
    val previewPokemon = Pokemon().apply {
        isClient = true
        this.species = pokemonSpecies
        forcedAspects = state.currentAspects
    }
    val previewEntity = PokemonEntity(level, previewPokemon).apply {
        setPos(0.0, 0.0, 0.0)
        yRotO = 0F
        xRotO = 0F
        yBodyRotO = 0F
        yBodyRot = 0F
        setYRot(0F)
        setXRot(0F)
    }

    val renderState = minecraft.entityRenderDispatcher.extractEntity(previewEntity, partialTicks) as? PokemonRenderState ?: return null
    val baseScale = pokemonSpecies.getForm(state.currentAspects).baseScale
    // Keep extracted bounds/light defaults from the temporary entity, but clear the
    // extracted entity references so GUI previews use the dedicated non-entity path.
    renderState.pokemonEntity = null
    renderState.guiPreviewEntity = null
    renderState.poseState = state
    renderState.species = species
    renderState.aspects = state.currentAspects
    renderState.texture = VaryingModelRepository.getTexture(species, state)
    renderState.baseScale = baseScale
    renderState.poseType = poseType
    renderState.applyPortraitTransform = poseType == PoseType.PORTRAIT && applyProfileTransform && applyBaseScale
    renderState.applyProfileTransform = applyProfileTransform
    renderState.applyBaseScale = applyBaseScale
    renderState.doQuirks = doQuirks
    renderState.heldItem = state.displayedHeldItem.copy()
    renderState.ageInTicks = state.animationSeconds * 20F
    renderState.previewHeadYaw = headYaw
    renderState.previewHeadPitch = headPitch
    renderState.bodyRot = 0F
    renderState.yRot = 0F
    renderState.xRot = 0F
    renderState.lightCoords = LightCoordsUtil.pack(blockLight, 15)
    renderState.shadowPieces.clear()
    renderState.shadowRadius = 0F
    renderState.outlineColor = 0
    renderState.nameTag = null
    renderState.displayFireAnimation = false
    renderState.isInvisible = false
    renderState.isInvisibleToPlayer = false
    if (renderState.scale != 0F) {
        renderState.boundingBoxWidth /= renderState.scale
        renderState.boundingBoxHeight /= renderState.scale
        renderState.scale = 1F
    }
    return renderState
}

private fun previewRadius(scale: Float, matrixScale: Float = 1F): Int {
    return ceil((scale * matrixScale * 2.5F).toDouble()).toInt().coerceAtLeast(16)
}

private data class PreviewBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

private object LegacyLitGuiPreviewRenderer {
    private data class PreviewTarget(
        val colorTexture: GpuTexture,
        val depthTexture: GpuTexture,
    )

    private data class PreviewTargetKey(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val width: Int,
        val height: Int
    )

    private val projectionBuffer = CachedOrthoProjectionMatrixBuffer("Cobblemon legacy GUI preview", -10000.0F, 10000.0F, true)
    private val targets = linkedMapOf<PreviewTargetKey, PreviewTarget>()
    private val transientViews = mutableListOf<GpuTextureView>()
    private var lastGuiTick = Int.MIN_VALUE

    private fun closeTarget(target: PreviewTarget) {
        target.colorTexture.close()
        target.depthTexture.close()
    }

    private fun closeView(view: GpuTextureView) {
        if (!view.isClosed) {
            view.close()
        }
    }

    private fun resetTargetsForGuiTick() {
        val guiTick = Minecraft.getInstance().gui.getGuiTicks()
        if (guiTick == lastGuiTick) {
            return
        }

        transientViews.forEach(::closeView)
        transientViews.clear()
        targets.values.forEach(::closeTarget)
        targets.clear()
        lastGuiTick = guiTick
    }

    private fun getOrCreateTarget(bounds: PreviewBounds, width: Int, height: Int): PreviewTarget {
        resetTargetsForGuiTick()

        val key = PreviewTargetKey(bounds.left, bounds.top, bounds.right, bounds.bottom, width, height)
        targets[key]?.let { return it }

        val device = RenderSystem.getDevice()
        val colorTexture = device.createTexture(
            { "Cobblemon legacy GUI preview color" },
            GpuTexture.USAGE_COPY_DST or GpuTexture.USAGE_TEXTURE_BINDING or GpuTexture.USAGE_RENDER_ATTACHMENT,
            com.mojang.blaze3d.textures.TextureFormat.RGBA8,
            width,
            height,
            1,
            1
        )
        val depthTexture = device.createTexture(
            { "Cobblemon legacy GUI preview depth" },
            GpuTexture.USAGE_COPY_DST or GpuTexture.USAGE_RENDER_ATTACHMENT,
            com.mojang.blaze3d.textures.TextureFormat.DEPTH32,
            width,
            height,
            1,
            1
        )
        return PreviewTarget(colorTexture, depthTexture).also { targets[key] = it }
    }

    fun render(
        context: GuiGraphicsExtractor,
        species: Identifier,
        poseType: PoseType,
        state: PosableState,
        partialTicks: Float,
        effectiveScale: Float,
        externalScale: Float,
        anchorX: Float,
        anchorY: Float,
        rotation: Quaternionf,
        applyProfileTransform: Boolean,
        applyBaseScale: Boolean,
        doQuirks: Boolean,
        headYaw: Float,
        headPitch: Float,
        blockLight: Int,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float,
        bounds: PreviewBounds,
        clipBounds: ScreenRectangle?,
        mirrorPortraitTranslationX: Boolean
    ) {
        if (VaryingModelRepository.getSprite(species, state, SpriteType.PROFILE) != null) {
            drawProfilePokemon(
                species = species,
                context = context,
                left = bounds.left,
                top = bounds.top,
                right = bounds.right,
                bottom = bounds.bottom,
                anchorX = anchorX,
                anchorY = anchorY,
                rotation = rotation,
                poseType = poseType,
                state = state,
                partialTicks = partialTicks,
                scale = effectiveScale / externalScale,
                applyProfileTransform = applyProfileTransform,
                applyBaseScale = applyBaseScale,
                doQuirks = doQuirks,
                r = red,
                g = green,
                b = blue,
                a = alpha,
                headYaw = headYaw,
                headPitch = headPitch,
                blockLight = blockLight
            )
            return
        }

        val minecraft = Minecraft.getInstance()
        val guiScale = minecraft.window.guiScale.toInt().coerceAtLeast(1)
        val pixelScale = guiScale.toFloat()
        val width = (bounds.right - bounds.left) * guiScale
        val height = (bounds.bottom - bounds.top) * guiScale
        if (width <= 0 || height <= 0) {
            return
        }

        val target = getOrCreateTarget(bounds, width, height)
        val colorTexture = target.colorTexture
        val depthTexture = target.depthTexture
        val device = RenderSystem.getDevice()
        val colorAttachmentView = device.createTextureView(colorTexture)
        val colorSampleView = device.createTextureView(colorTexture)
        val depthAttachmentView = device.createTextureView(depthTexture)
        transientViews += colorAttachmentView
        transientViews += colorSampleView
        transientViews += depthAttachmentView

        device.createCommandEncoder().clearColorAndDepthTextures(colorTexture, 0, depthTexture, 1.0)

        RenderSystem.outputColorTextureOverride = colorAttachmentView
        RenderSystem.outputDepthTextureOverride = depthAttachmentView
        RenderSystem.setProjectionMatrix(projectionBuffer.getBuffer(width.toFloat(), height.toFloat()), ProjectionType.ORTHOGRAPHIC)

        try {
            val poseStack = PoseStack().apply {
                translate((anchorX - bounds.left) * pixelScale, (anchorY - bounds.top) * pixelScale, 0F)
                scale(effectiveScale * pixelScale, effectiveScale * pixelScale, -(effectiveScale * pixelScale))
            }
            val model = VaryingModelRepository.getPoser(species, state)
            val texture = VaryingModelRepository.getTexture(species, state)
            val renderContext = RenderContext().also {
                VaryingModelRepository.getTextureNoSubstitute(species, state).let { originalTexture -> it.put(RenderContext.TEXTURE, originalTexture) }
                val baseScale = PokemonSpecies.getByIdentifier(species)!!.getForm(state.currentAspects).baseScale
                it.put(RenderContext.SCALE, baseScale)
                it.put(RenderContext.SPECIES, species)
                it.put(RenderContext.ASPECTS, state.currentAspects)
                it.put(RenderContext.RENDER_STATE, RenderContext.RenderState.PROFILE)
                it.put(RenderContext.POSABLE_STATE, state)
                it.put(RenderContext.DO_QUIRKS, doQuirks)
            }
            model.context = renderContext
            state.currentModel = model

            val originalPose = state.currentPose
            state.setPoseToFirstSuitable(poseType)
            state.updatePartialTicks(partialTicks)
            val ageInTicks = state.animationSeconds * 20F
            val baseScale = PokemonSpecies.getByIdentifier(species)!!.getForm(state.currentAspects).baseScale

            val applyPortraitTransform = poseType == PoseType.PORTRAIT && applyProfileTransform && applyBaseScale

            if (applyPortraitTransform) {
                poseStack.translate(
                    if (mirrorPortraitTranslationX) -model.portraitTranslation.x else model.portraitTranslation.x,
                    model.portraitTranslation.y + 1.5 * model.portraitScale,
                    model.portraitTranslation.z - 4.0
                )
                poseStack.scale(model.portraitScale, model.portraitScale, 1F / model.portraitScale)
            } else if (applyProfileTransform) {
                poseStack.translate(
                    model.profileTranslation.x,
                    model.profileTranslation.y + 1.5 * model.profileScale,
                    model.profileTranslation.z - 4.0
                )
                poseStack.scale(model.profileScale, model.profileScale, 1F / model.profileScale)
            } else {
                poseStack.translate(0F, 0F, -4F)
                if (applyBaseScale) {
                    poseStack.scale(baseScale, baseScale, 1F / baseScale)
                }
            }

            poseStack.mulPose(rotation)
            model.applyAnimations(null, state, 0F, 0F, ageInTicks, headYaw, headPitch)
            originalPose?.let(state::setPose)

            val bufferSource = minecraft.renderBuffers().bufferSource()
            val renderType = RenderTypes.entityCutoutCull(texture)
            val packedLight = LightCoordsUtil.pack(blockLight, 15)
            val colour = toHex(red, green, blue, alpha)

            minecraft.gameRenderer.getLighting().setupFor(Lighting.Entry.ENTITY_IN_UI)
            LegacyGuiPreviewLights.withOverride {
                model.withLayerContext(bufferSource, state, VaryingModelRepository.getLayers(species, state)) {
                    model.render(renderContext, poseStack, bufferSource.getBuffer(renderType), packedLight, OverlayTexture.NO_OVERLAY, colour)
                }
                legacyHeldItemRenderer.renderOnModel(
                    state.displayedHeldItem,
                    state,
                    poseStack,
                    minecraft.gameRenderer.getSubmitNodeStorage(),
                    LightCoordsUtil.pack(15, 15),
                    frontLight = true
                )
                minecraft.gameRenderer.getFeatureRenderDispatcher().renderAllFeatures()
                bufferSource.endBatch()
                minecraft.renderBuffers().outlineBufferSource().endOutlineBatch()
                minecraft.renderBuffers().crumblingBufferSource().endBatch()
            }
            model.setDefault()
        } finally {
            RenderSystem.outputColorTextureOverride = null
            RenderSystem.outputDepthTextureOverride = null
        }

        val guiRenderState = minecraft.gameRenderer.getGameRenderState().guiRenderState
        guiRenderState.addBlitToCurrentLayer(
            BlitRenderState(
                RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA,
                TextureSetup.singleTexture(colorSampleView, RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST)),
                Matrix3x2f(),
                bounds.left,
                bounds.top,
                bounds.right,
                bounds.bottom,
                0.0F,
                1.0F,
                1.0F,
                0.0F,
                -1,
                clipBounds
            )
        )
    }
}

private fun previewBounds(
    anchorX: Float,
    anchorY: Float,
    effectiveScale: Float,
    scissor: ScreenRectangle? = null
): PreviewBounds {
    if (scissor != null) {
        return PreviewBounds(
            left = scissor.left(),
            top = scissor.top(),
            right = scissor.right(),
            bottom = scissor.bottom()
        )
    }

    val radius = previewRadius(effectiveScale)
    return PreviewBounds(
        left = anchorX.toInt() - radius,
        top = anchorY.toInt() - radius,
        right = anchorX.toInt() + radius,
        bottom = anchorY.toInt() + radius
    )
}

private fun adjustedPreviewRotation(rotation: Quaternionf): Quaternionf {
    // GuiEntityRenderer applies this rotation before PokemonRenderer can add the model's
    // profile/portrait translation. Keep the PIP rotation neutral and let PokemonRenderer
    // apply the preview rotation after those transforms to preserve the 1.21.1 pivot order.
    return Quaternionf()
}

private fun adjustedPreviewCameraAngle(rotation: Quaternionf): Quaternionf {
    // 1.21.1 used the same quaternion for model rotation and camera override,
    // then conjugated it before passing it to the entity extractRenderState dispatcher.
    // In 1.21.11, GuiEntityRenderer additionally rotates the camera by +PI around Y
    // after conjugating the override angle, so pre-rotate here to preserve the old view.
    return Quaternionf().rotateY(Math.PI.toFloat()).mul(rotation)
}

private fun previewTranslation(
    anchorX: Float,
    anchorY: Float,
    bounds: PreviewBounds,
    effectiveScale: Float
): Vector3f {
    val scale = effectiveScale.coerceAtLeast(1.0E-3F)
    val centerX = (bounds.left + bounds.right) / 2F
    val centerY = (bounds.top + bounds.bottom) / 2F
    return Vector3f(
        (anchorX - centerX) / scale,
        (anchorY - centerY) / scale,
        0F
    )
}

fun drawProfilePokemon(
    renderablePokemon: RenderablePokemon,
    matrixStack: PoseStack,
    rotation: Quaternionf,
    poseType: PoseType = PoseType.PROFILE,
    state: PosableState,
    partialTicks: Float,
    scale: Float = 20F,
    applyProfileTransform: Boolean = true,
    applyBaseScale: Boolean = false,
    r: Float = 1F,
    g: Float = 1F,
    b: Float = 1F,
    a: Float = 1F,
    headYaw: Float = 0f,
    headPitch: Float = 0f,
    blockLight: Int = 13
) = drawProfilePokemon(
    species = renderablePokemon.species.resourceIdentifier,
    matrixStack = matrixStack,
    rotation = rotation,
    poseType = poseType,
    state = state.also {
        it.currentAspects = renderablePokemon.aspects
        it.displayedHeldItem = renderablePokemon.heldItem
    },
    partialTicks = partialTicks,
    scale = scale,
    applyProfileTransform = applyProfileTransform,
    applyBaseScale = applyBaseScale,
    r = r,
    g = g,
    b = b,
    a = a,
    headYaw = headYaw,
    headPitch = headPitch,
    blockLight = blockLight
)

fun drawProfilePokemon(
    renderablePokemon: RenderablePokemon,
    context: GuiGraphicsExtractor,
    originX: Number,
    originY: Number,
    rotation: Quaternionf,
    poseType: PoseType = PoseType.PROFILE,
    state: PosableState,
    partialTicks: Float,
    scale: Float = 20F,
    applyProfileTransform: Boolean = true,
    applyBaseScale: Boolean = false,
    r: Float = 1F,
    g: Float = 1F,
    b: Float = 1F,
    a: Float = 1F,
    headYaw: Float = 0f,
    headPitch: Float = 0f,
    blockLight: Int = 13
) = drawProfilePokemon(
    species = renderablePokemon.species.resourceIdentifier,
    context = context,
    originX = originX,
    originY = originY,
    rotation = rotation,
    poseType = poseType,
    state = state.also {
        it.currentAspects = renderablePokemon.aspects
        it.displayedHeldItem = renderablePokemon.heldItem
    },
    partialTicks = partialTicks,
    scale = scale,
    applyProfileTransform = applyProfileTransform,
    applyBaseScale = applyBaseScale,
    r = r,
    g = g,
    b = b,
    a = a,
    headYaw = headYaw,
    headPitch = headPitch,
    blockLight = blockLight
)

fun drawProfilePokemon(
    species: Identifier,
    context: GuiGraphicsExtractor,
    originX: Number,
    originY: Number,
    rotation: Quaternionf,
    poseType: PoseType = PoseType.PROFILE,
    state: PosableState,
    partialTicks: Float,
    scale: Float = 20F,
    applyProfileTransform: Boolean = true,
    applyBaseScale: Boolean = false,
    doQuirks: Boolean = false,
    r: Float = 1F,
    g: Float = 1F,
    b: Float = 1F,
    a: Float = 1F,
    headYaw: Float = 0f,
    headPitch: Float = 0f,
    blockLight: Int = 13
) {
    val adjustedCameraAngle = adjustedPreviewCameraAngle(rotation)
    val bounds = previewBounds(originX.toFloat(), originY.toFloat(), scale)
    val renderState = createGuiRenderState(
        species = species,
        poseType = poseType,
        state = state,
        partialTicks = partialTicks,
        applyProfileTransform = applyProfileTransform,
        applyBaseScale = applyBaseScale,
        doQuirks = doQuirks,
        headYaw = headYaw,
        headPitch = headPitch,
        blockLight = blockLight
    ) ?: return
    renderState.previewRotation = Quaternionf(rotation)
    context.entity(
        renderState,
        scale,
        previewTranslation(originX.toFloat(), originY.toFloat(), bounds, scale),
        adjustedPreviewRotation(rotation),
        adjustedCameraAngle,
        bounds.left,
        bounds.top,
        bounds.right,
        bounds.bottom
    )
}

fun drawProfilePokemon(
    renderablePokemon: RenderablePokemon,
    context: GuiGraphicsExtractor,
    left: Int,
    top: Int,
    right: Int,
    bottom: Int,
    anchorX: Float = (left + right) / 2F,
    anchorY: Float = (top + bottom) / 2F,
    rotation: Quaternionf,
    poseType: PoseType = PoseType.PROFILE,
    state: PosableState,
    partialTicks: Float,
    scale: Float = 20F,
    applyProfileTransform: Boolean = true,
    applyBaseScale: Boolean = false,
    r: Float = 1F,
    g: Float = 1F,
    b: Float = 1F,
    a: Float = 1F,
    headYaw: Float = 0f,
    headPitch: Float = 0f,
    blockLight: Int = 13
) = drawProfilePokemon(
    species = renderablePokemon.species.resourceIdentifier,
    context = context,
    left = left,
    top = top,
    right = right,
    bottom = bottom,
    anchorX = anchorX,
    anchorY = anchorY,
    rotation = rotation,
    poseType = poseType,
    state = state.also {
        it.currentAspects = renderablePokemon.aspects
        it.displayedHeldItem = renderablePokemon.heldItem
    },
    partialTicks = partialTicks,
    scale = scale,
    applyProfileTransform = applyProfileTransform,
    applyBaseScale = applyBaseScale,
    r = r,
    g = g,
    b = b,
    a = a,
    headYaw = headYaw,
    headPitch = headPitch,
    blockLight = blockLight
)

fun drawProfilePokemon(
    species: Identifier,
    context: GuiGraphicsExtractor,
    left: Int,
    top: Int,
    right: Int,
    bottom: Int,
    anchorX: Float = (left + right) / 2F,
    anchorY: Float = (top + bottom) / 2F,
    rotation: Quaternionf,
    poseType: PoseType = PoseType.PROFILE,
    state: PosableState,
    partialTicks: Float,
    scale: Float = 20F,
    applyProfileTransform: Boolean = true,
    applyBaseScale: Boolean = false,
    doQuirks: Boolean = false,
    r: Float = 1F,
    g: Float = 1F,
    b: Float = 1F,
    a: Float = 1F,
    headYaw: Float = 0f,
    headPitch: Float = 0f,
    blockLight: Int = 13
) {
    val adjustedCameraAngle = adjustedPreviewCameraAngle(rotation)
    val bounds = PreviewBounds(left, top, right, bottom)
    val renderState = createGuiRenderState(
        species = species,
        poseType = poseType,
        state = state,
        partialTicks = partialTicks,
        applyProfileTransform = applyProfileTransform,
        applyBaseScale = applyBaseScale,
        doQuirks = doQuirks,
        headYaw = headYaw,
        headPitch = headPitch,
        blockLight = blockLight
    ) ?: return
    renderState.previewRotation = Quaternionf(rotation)
    context.entity(
        renderState,
        scale,
        previewTranslation(anchorX, anchorY, bounds, scale),
        adjustedPreviewRotation(rotation),
        adjustedCameraAngle,
        bounds.left,
        bounds.top,
        bounds.right,
        bounds.bottom
    )
}

fun drawProfilePokemon(
    species: Identifier,
    matrixStack: PoseStack,
    context: GuiGraphicsExtractor,
    rotation: Quaternionf,
    poseType: PoseType = PoseType.PROFILE,
    state: PosableState,
    partialTicks: Float,
    scale: Float = 20F,
    applyProfileTransform: Boolean = true,
    applyBaseScale: Boolean = false,
    doQuirks: Boolean = false,
    r: Float = 1F,
    g: Float = 1F,
    b: Float = 1F,
    a: Float = 1F,
    headYaw: Float = 0f,
    headPitch: Float = 0f,
    blockLight: Int = 13
) {
    drawProfilePokemon(
        species = species,
        matrixStack = poseToGuiMatrix(matrixStack),
        context = context,
        rotation = rotation,
        poseType = poseType,
        state = state,
        partialTicks = partialTicks,
        scale = scale,
        applyProfileTransform = applyProfileTransform,
        applyBaseScale = applyBaseScale,
        doQuirks = doQuirks,
        r = r,
        g = g,
        b = b,
        a = a,
        headYaw = headYaw,
        headPitch = headPitch,
        blockLight = blockLight
    )
}

fun drawProfilePokemon(
    species: Identifier,
    matrixStack: PoseStack,
    rotation: Quaternionf,
    poseType: PoseType = PoseType.PROFILE,
    state: PosableState,
    partialTicks: Float,
    scale: Float = 20F,
    applyProfileTransform: Boolean = true,
    applyBaseScale: Boolean = false,
    doQuirks: Boolean = false,
    r: Float = 1F,
    g: Float = 1F,
    b: Float = 1F,
    a: Float = 1F,
    headYaw: Float = 0f,
    headPitch: Float = 0f,
    blockLight: Int = 13
) {
    drawProfilePokemon(
        species = species,
        matrixStack = poseToGuiMatrix(matrixStack),
        rotation = rotation,
        poseType = poseType,
        state = state,
        partialTicks = partialTicks,
        scale = scale,
        applyProfileTransform = applyProfileTransform,
        applyBaseScale = applyBaseScale,
        doQuirks = doQuirks,
        r = r,
        g = g,
        b = b,
        a = a,
        headYaw = headYaw,
        headPitch = headPitch,
        blockLight = blockLight
    )
}

fun drawProfilePokemon(
    renderablePokemon: RenderablePokemon,
    matrixStack: Matrix3x2fStack,
    context: GuiGraphicsExtractor,
    rotation: Quaternionf,
    poseType: PoseType = PoseType.PROFILE,
    state: PosableState,
    partialTicks: Float,
    scale: Float = 20F,
    applyProfileTransform: Boolean = true,
    applyBaseScale: Boolean = false,
    r: Float = 1F,
    g: Float = 1F,
    b: Float = 1F,
    a: Float = 1F,
    headYaw: Float = 0f,
    headPitch: Float = 0f,
    blockLight: Int = 13
) {
    drawProfilePokemon(
        species = renderablePokemon.species.resourceIdentifier,
        matrixStack = matrixStack,
        context = context,
        rotation = rotation,
        poseType = poseType,
        state = state.also {
            it.currentAspects = renderablePokemon.aspects
            it.displayedHeldItem = renderablePokemon.heldItem
        },
        partialTicks = partialTicks,
        scale = scale,
        applyProfileTransform = applyProfileTransform,
        applyBaseScale = applyBaseScale,
        r = r,
        g = g,
        b = b,
        a = a,
        headYaw = headYaw,
        headPitch = headPitch,
        blockLight = blockLight
    )
}

fun drawLegacyLitProfilePokemon(
    renderablePokemon: RenderablePokemon,
    matrixStack: Matrix3x2fStack,
    context: GuiGraphicsExtractor,
    rotation: Quaternionf,
    poseType: PoseType = PoseType.PROFILE,
    state: PosableState,
    partialTicks: Float,
    scale: Float = 20F,
    applyProfileTransform: Boolean = true,
    applyBaseScale: Boolean = false,
    r: Float = 1F,
    g: Float = 1F,
    b: Float = 1F,
    a: Float = 1F,
    headYaw: Float = 0f,
    headPitch: Float = 0f,
    blockLight: Int = 13
) {
    drawLegacyLitProfilePokemon(
        species = renderablePokemon.species.resourceIdentifier,
        matrixStack = matrixStack,
        context = context,
        rotation = rotation,
        poseType = poseType,
        state = state.also {
            it.currentAspects = renderablePokemon.aspects
            it.displayedHeldItem = renderablePokemon.heldItem
        },
        partialTicks = partialTicks,
        scale = scale,
        applyProfileTransform = applyProfileTransform,
        applyBaseScale = applyBaseScale,
        r = r,
        g = g,
        b = b,
        a = a,
        headYaw = headYaw,
        headPitch = headPitch,
        blockLight = blockLight
    )
}

fun drawLegacyLitProfilePokemon(
    renderablePokemon: RenderablePokemon,
    context: GuiGraphicsExtractor,
    left: Int,
    top: Int,
    right: Int,
    bottom: Int,
    anchorX: Float = (left + right) / 2F,
    anchorY: Float = (top + bottom) / 2F,
    rotation: Quaternionf,
    poseType: PoseType = PoseType.PROFILE,
    state: PosableState,
    partialTicks: Float,
    scale: Float = 20F,
    externalScale: Float = 1F,
    applyProfileTransform: Boolean = true,
    applyBaseScale: Boolean = false,
    r: Float = 1F,
    g: Float = 1F,
    b: Float = 1F,
    a: Float = 1F,
    headYaw: Float = 0f,
    headPitch: Float = 0f,
    blockLight: Int = 13
) {
    drawLegacyLitProfilePokemon(
        species = renderablePokemon.species.resourceIdentifier,
        context = context,
        left = left,
        top = top,
        right = right,
        bottom = bottom,
        anchorX = anchorX,
        anchorY = anchorY,
        rotation = rotation,
        poseType = poseType,
        state = state.also {
            it.currentAspects = renderablePokemon.aspects
            it.displayedHeldItem = renderablePokemon.heldItem
        },
        partialTicks = partialTicks,
        scale = scale,
        externalScale = externalScale,
        applyProfileTransform = applyProfileTransform,
        applyBaseScale = applyBaseScale,
        r = r,
        g = g,
        b = b,
        a = a,
        headYaw = headYaw,
        headPitch = headPitch,
        blockLight = blockLight
    )
}

fun drawProfilePokemon(
    renderablePokemon: RenderablePokemon,
    matrixStack: Matrix3x2fStack,
    rotation: Quaternionf,
    poseType: PoseType = PoseType.PROFILE,
    state: PosableState,
    partialTicks: Float,
    scale: Float = 20F,
    applyProfileTransform: Boolean = true,
    applyBaseScale: Boolean = false,
    r: Float = 1F,
    g: Float = 1F,
    b: Float = 1F,
    a: Float = 1F,
    headYaw: Float = 0f,
    headPitch: Float = 0f,
    blockLight: Int = 13
) {
    drawProfilePokemon(
        species = renderablePokemon.species.resourceIdentifier,
        matrixStack = matrixStack,
        rotation = rotation,
        poseType = poseType,
        state = state.also {
            it.currentAspects = renderablePokemon.aspects
            it.displayedHeldItem = renderablePokemon.heldItem
        },
        partialTicks = partialTicks,
        scale = scale,
        applyProfileTransform = applyProfileTransform,
        applyBaseScale = applyBaseScale,
        r = r,
        g = g,
        b = b,
        a = a,
        headYaw = headYaw,
        headPitch = headPitch,
        blockLight = blockLight
    )
}

fun drawProfilePokemon(
    species: Identifier,
    matrixStack: Matrix3x2fStack,
    context: GuiGraphicsExtractor,
    rotation: Quaternionf,
    poseType: PoseType = PoseType.PROFILE,
    state: PosableState,
    partialTicks: Float,
    scale: Float = 20F,
    applyProfileTransform: Boolean = true,
    applyBaseScale: Boolean = false,
    doQuirks: Boolean = false,
    r: Float = 1F,
    g: Float = 1F,
    b: Float = 1F,
    a: Float = 1F,
    headYaw: Float = 0f,
    headPitch: Float = 0f,
    blockLight: Int = 13
) {
    val externalScale = guiScale(matrixStack)
    val effectiveScale = scale * externalScale
    val anchorX = matrixStack.m20()
    val anchorY = matrixStack.m21()
    val bounds = previewBounds(anchorX, anchorY, effectiveScale, currentScissorBounds(context))
    val adjustedCameraAngle = adjustedPreviewCameraAngle(rotation)
    val renderState = createGuiRenderState(
        species = species,
        poseType = poseType,
        state = state,
        partialTicks = partialTicks,
        applyProfileTransform = applyProfileTransform,
        applyBaseScale = applyBaseScale,
        doQuirks = doQuirks,
        headYaw = headYaw,
        headPitch = headPitch,
        blockLight = blockLight
    ) ?: return
    renderState.previewRotation = Quaternionf(rotation)
    context.entity(
        renderState,
        effectiveScale,
        previewTranslation(anchorX, anchorY, bounds, effectiveScale),
        adjustedPreviewRotation(rotation),
        adjustedCameraAngle,
        bounds.left,
        bounds.top,
        bounds.right,
        bounds.bottom
    )
}

fun drawLegacyLitProfilePokemon(
    species: Identifier,
    matrixStack: Matrix3x2fStack,
    context: GuiGraphicsExtractor,
    rotation: Quaternionf,
    poseType: PoseType = PoseType.PROFILE,
    state: PosableState,
    partialTicks: Float,
    scale: Float = 20F,
    applyProfileTransform: Boolean = true,
    applyBaseScale: Boolean = false,
    doQuirks: Boolean = false,
    r: Float = 1F,
    g: Float = 1F,
    b: Float = 1F,
    a: Float = 1F,
    headYaw: Float = 0f,
    headPitch: Float = 0f,
    blockLight: Int = 13,
    mirrorPortraitTranslationX: Boolean = false
) {
    val externalScale = guiScale(matrixStack)
    val effectiveScale = scale * externalScale
    val anchorX = matrixStack.m20()
    val anchorY = matrixStack.m21()
    val clipBounds = currentScissorBounds(context)
    val bounds = previewBounds(anchorX, anchorY, effectiveScale)
    LegacyLitGuiPreviewRenderer.render(
        context = context,
        species = species,
        poseType = poseType,
        state = state,
        partialTicks = partialTicks,
        effectiveScale = effectiveScale,
        externalScale = externalScale,
        anchorX = anchorX,
        anchorY = anchorY,
        rotation = Quaternionf(rotation),
        applyProfileTransform = applyProfileTransform,
        applyBaseScale = applyBaseScale,
        doQuirks = doQuirks,
        headYaw = headYaw,
        headPitch = headPitch,
        blockLight = blockLight,
        red = r,
        green = g,
        blue = b,
        alpha = a,
        bounds = bounds,
        clipBounds = clipBounds,
        mirrorPortraitTranslationX = mirrorPortraitTranslationX
    )
}

fun drawLegacyLitProfilePokemon(
    species: Identifier,
    context: GuiGraphicsExtractor,
    left: Int,
    top: Int,
    right: Int,
    bottom: Int,
    anchorX: Float = (left + right) / 2F,
    anchorY: Float = (top + bottom) / 2F,
    rotation: Quaternionf,
    poseType: PoseType = PoseType.PROFILE,
    state: PosableState,
    partialTicks: Float,
    scale: Float = 20F,
    externalScale: Float = 1F,
    applyProfileTransform: Boolean = true,
    applyBaseScale: Boolean = false,
    doQuirks: Boolean = false,
    r: Float = 1F,
    g: Float = 1F,
    b: Float = 1F,
    a: Float = 1F,
    headYaw: Float = 0f,
    headPitch: Float = 0f,
    blockLight: Int = 13,
    mirrorPortraitTranslationX: Boolean = false
) {
    val effectiveScale = scale * externalScale
    val bounds = PreviewBounds(left, top, right, bottom)
    val clipBounds = currentScissorBounds(context) ?: ScreenRectangle(left, top, right - left, bottom - top)
    LegacyLitGuiPreviewRenderer.render(
        context = context,
        species = species,
        poseType = poseType,
        state = state,
        partialTicks = partialTicks,
        effectiveScale = effectiveScale,
        externalScale = externalScale,
        anchorX = anchorX,
        anchorY = anchorY,
        rotation = Quaternionf(rotation),
        applyProfileTransform = applyProfileTransform,
        applyBaseScale = applyBaseScale,
        doQuirks = doQuirks,
        headYaw = headYaw,
        headPitch = headPitch,
        blockLight = blockLight,
        red = r,
        green = g,
        blue = b,
        alpha = a,
        bounds = bounds,
        clipBounds = clipBounds,
        mirrorPortraitTranslationX = mirrorPortraitTranslationX
    )
}

fun drawProfilePokemon(
    species: Identifier,
    matrixStack: Matrix3x2fStack,
    rotation: Quaternionf,
    poseType: PoseType = PoseType.PROFILE,
    state: PosableState,
    partialTicks: Float,
    scale: Float = 20F,
    applyProfileTransform: Boolean = true,
    applyBaseScale: Boolean = false,
    doQuirks: Boolean = false,
    r: Float = 1F,
    g: Float = 1F,
    b: Float = 1F,
    a: Float = 1F,
    headYaw: Float = 0f,
    headPitch: Float = 0f,
    blockLight: Int = 13
) {
    val externalScale = guiScale(matrixStack)
    val effectiveScale = scale * externalScale
    val anchorX = matrixStack.m20()
    val anchorY = matrixStack.m21()
    val bounds = previewBounds(anchorX, anchorY, effectiveScale)
    val adjustedCameraAngle = adjustedPreviewCameraAngle(rotation)
    val renderState = createGuiRenderState(
        species = species,
        poseType = poseType,
        state = state,
        partialTicks = partialTicks,
        applyProfileTransform = applyProfileTransform,
        applyBaseScale = applyBaseScale,
        doQuirks = doQuirks,
        headYaw = headYaw,
        headPitch = headPitch,
        blockLight = blockLight
    ) ?: return
    renderState.previewRotation = Quaternionf(rotation)
    createMatrixGuiGraphics(matrixStack).entity(
        renderState,
        effectiveScale,
        previewTranslation(anchorX, anchorY, bounds, effectiveScale),
        adjustedPreviewRotation(rotation),
        adjustedCameraAngle,
        bounds.left,
        bounds.top,
        bounds.right,
        bounds.bottom
    )
}

const val HEAD_YAW_FACTOR = 40f
const val HEAD_PITCH_FACTOR = 20f
const val SMOOTHING_FACTOR = 0.05f

fun calculateHeadYawAndPitch(
    xCoordinate: Float,
    yCoordinate: Float,
    rotation: Quaternionf,
    mouseX: Int,
    mouseY: Int,
    currentYaw: Float,
    currentPitch: Float,
    resetToCenter: Boolean,
): Pair<Float, Float> {
    val horizontalAngle = atan(((xCoordinate - mouseX) / 40.0f).toDouble()).toFloat()
    val verticalAngle = atan(((yCoordinate - mouseY) / 40.0f).toDouble()).toFloat()

    val eulerXYZRotationDegrees = rotation.toEulerXYZDegrees()
    val targetYaw = if (!resetToCenter) horizontalAngle * HEAD_YAW_FACTOR - eulerXYZRotationDegrees.y else 0f
    val targetPitch = if (!resetToCenter) -verticalAngle * HEAD_PITCH_FACTOR - eulerXYZRotationDegrees.x else 0f

    val newYaw = Mth.rotLerp(SMOOTHING_FACTOR, currentYaw, targetYaw)
    val newPitch = Mth.rotLerp(SMOOTHING_FACTOR, currentPitch, targetPitch)

    return Pair(newYaw, newPitch)
}
