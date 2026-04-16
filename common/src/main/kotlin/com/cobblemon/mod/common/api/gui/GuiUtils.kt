/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.gui

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.text.font
import com.cobblemon.mod.common.client.gui.dialogue.DialogueScreen
import com.cobblemon.mod.common.client.gui.drawLegacyLitProfilePokemon
import com.cobblemon.mod.common.client.gui.drawProfilePokemon
import com.cobblemon.mod.common.client.gui.battle.BattleOverlay.Companion.PORTRAIT_DIAMETER
import com.cobblemon.mod.common.client.render.SpriteType
import com.cobblemon.mod.common.client.render.pokemon.LegacyGuiPreviewLights
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.util.toHex
import com.mojang.blaze3d.ProjectionType
import com.mojang.blaze3d.platform.Lighting
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.textures.GpuTextureView
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.render.TextureSetup
import com.cobblemon.mod.common.client.renderer.CachedOrthoProjectionMatrixBuffer
import com.cobblemon.mod.common.mixin.client.accessor.GuiGraphicsExtractorAccessor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.client.renderer.state.gui.BlitRenderState
import net.minecraft.client.renderer.state.gui.GuiRenderState
import net.minecraft.network.chat.FontDescription
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.resources.Identifier
import net.minecraft.util.ARGB
import net.minecraft.util.FormattedCharSequence
import net.minecraft.util.LightCoordsUtil
import org.joml.Matrix3x2fStack
import org.joml.Matrix3x2f
import org.joml.Quaternionf
import kotlin.math.abs
import kotlin.math.max

private fun opaqueTextColour(colour: Int) = if (colour ushr 24 == 0) ARGB.opaque(colour) else colour

private fun createMatrixGuiGraphics(matrixStack: Matrix3x2fStack): GuiGraphicsExtractor {
    val minecraft = Minecraft.getInstance()
    val window = minecraft.window
    val mouse = minecraft.mouseHandler
    val guiRenderState = minecraft.gameRenderer.gameRenderState.guiRenderState
    val graphics = GuiGraphicsExtractor(minecraft, guiRenderState, mouse.getScaledXPos(window).toInt(), mouse.getScaledYPos(window).toInt())
    graphics.pose().set(matrixStack)
    return graphics
}

private fun poseToGuiMatrix(matrixStack: PoseStack): Matrix3x2fStack {
    val pose = matrixStack.last().pose()
    return Matrix3x2fStack(16).apply {
        translate(pose.m30(), pose.m31())
        scale(pose.m00(), pose.m11())
    }
}

private fun guiMatrixToPose(matrixStack: Matrix3x2fStack): PoseStack {
    return PoseStack().apply {
        translate(matrixStack.m20().toDouble(), matrixStack.m21().toDouble(), 0.0)
        scale(matrixStack.m00(), matrixStack.m11(), 1F)
    }
}

private fun guiScale(matrixStack: Matrix3x2fStack): Float {
    return max(abs(matrixStack.m00()), abs(matrixStack.m11())).coerceAtLeast(1.0E-3F)
}

private fun drawRectangle(
    context: GuiGraphicsExtractor,
    texture: Identifier,
    color: Int,
    x: Float,
    y: Float,
    endX: Float,
    endY: Float,
    minU: Float,
    maxU: Float,
    minV: Float,
    maxV: Float
) {
    val width = (endX - x).toInt()
    val height = (endY - y).toInt()
    if (width <= 0 || height <= 0) {
        return
    }

    val textureWidth = if (maxU != minU) ((endX - x) / (maxU - minU)).toInt() else width
    val textureHeight = if (maxV != minV) ((endY - y) / (maxV - minV)).toInt() else height
    val uOffset = (minU * textureWidth).toInt()
    val vOffset = (minV * textureHeight).toInt()

    context.blit(
        RenderPipelines.GUI_TEXTURED,
        texture,
        x.toInt(),
        y.toInt(),
        uOffset.toFloat(),
        vOffset.toFloat(),
        width,
        height,
        width,
        height,
        textureWidth,
        textureHeight,
        color
    )
}

@JvmOverloads
fun blitk(
    context: GuiGraphicsExtractor,
    texture: Identifier? = null,
    x: Number,
    y: Number,
    height: Number = 0,
    width: Number = 0,
    uOffset: Number = 0,
    vOffset: Number = 0,
    textureWidth: Number = width,
    textureHeight: Number = height,
    blitOffset: Number = 0,
    red: Number = 1,
    green: Number = 1,
    blue: Number = 1,
    alpha: Number = 1F,
    blend: Boolean = true,
    scale: Float = 1F
) {
    if(texture == null) {
        return;
    }
    val matrixStack = context.pose()
    matrixStack.pushMatrix()
    matrixStack.scale(scale, scale)
    drawRectangle(
        context = context,
        texture = texture,
        color = ARGB.colorFromFloat(alpha.toFloat(), red.toFloat(), green.toFloat(), blue.toFloat()),
        x = x.toFloat(),
        y = y.toFloat(),
        endX = x.toFloat() + width.toFloat(),
        endY = y.toFloat() + height.toFloat(),
        minU = uOffset.toFloat() / textureWidth.toFloat(),
        maxU = (uOffset.toFloat() + width.toFloat()) / textureWidth.toFloat(),
        minV = vOffset.toFloat() / textureHeight.toFloat(),
        maxV = (vOffset.toFloat() + height.toFloat()) / textureHeight.toFloat()
    )

    matrixStack.popMatrix()
}
@JvmOverloads
fun blitk(
    matrixStack: PoseStack,
    texture: Identifier? = null,
    x: Number,
    y: Number,
    height: Number = 0,
    width: Number = 0,
    uOffset: Number = 0,
    vOffset: Number = 0,
    textureWidth: Number = width,
    textureHeight: Number = height,
    blitOffset: Number = 0,
    red: Number = 1,
    green: Number = 1,
    blue: Number = 1,
    alpha: Number = 1F,
    blend: Boolean = true,
    scale: Float = 1F
) {
    blitk(
        matrixStack = poseToGuiMatrix(matrixStack),
        texture = texture,
        x = x,
        y = y,
        height = height,
        width = width,
        uOffset = uOffset,
        vOffset = vOffset,
        textureWidth = textureWidth,
        textureHeight = textureHeight,
        blitOffset = blitOffset,
        red = red,
        green = green,
        blue = blue,
        alpha = alpha,
        blend = blend,
        scale = scale
    )
}

@JvmOverloads
fun blitk(
    matrixStack: Matrix3x2fStack,
    texture: Identifier? = null,
    x: Number,
    y: Number,
    height: Number = 0,
    width: Number = 0,
    uOffset: Number = 0,
    vOffset: Number = 0,
    textureWidth: Number = width,
    textureHeight: Number = height,
    blitOffset: Number = 0,
    red: Number = 1,
    green: Number = 1,
    blue: Number = 1,
    alpha: Number = 1F,
    blend: Boolean = true,
    scale: Float = 1F
) {
    blitk(
        context = createMatrixGuiGraphics(matrixStack),
        texture = texture,
        x = x,
        y = y,
        height = height,
        width = width,
        uOffset = uOffset,
        vOffset = vOffset,
        textureWidth = textureWidth,
        textureHeight = textureHeight,
        blitOffset = blitOffset,
        red = red,
        green = green,
        blue = blue,
        alpha = alpha,
        blend = blend,
        scale = scale
    )
}

@JvmOverloads
fun drawCenteredText(
    context: GuiGraphicsExtractor,
    font: Identifier? = null,
    text: Component,
    x: Number,
    y: Number,
    colour: Int,
    shadow: Boolean = true
) {
    val comp = (text as MutableComponent).let { if (font != null) it.font(font) else it }
    val textRenderer = Minecraft.getInstance().font
    context.text(textRenderer, comp, x.toInt() - textRenderer.width(comp) / 2, y.toInt(), opaqueTextColour(colour), shadow)
}

@JvmOverloads
fun drawText(
    context: GuiGraphicsExtractor,
    font: Identifier? = null,
    text: MutableComponent,
    x: Number,
    y: Number,
    centered: Boolean = false,
    colour: Int,
    shadow: Boolean = true,
    pMouseX: Number? = null,
    pMouseY: Number? = null
): Boolean {
    val comp = if (font == null) text else text.setStyle(text.style.withFont(FontDescription.Resource(font)))
    val textRenderer = Minecraft.getInstance().font
    val resolvedColour = opaqueTextColour(colour)
    var x = x
    val width = textRenderer.width(comp)
    if (centered) {
        x = x.toDouble() - width / 2
    }
    context.text(textRenderer, comp, x.toInt(), y.toInt(), resolvedColour, shadow)
    var isHovered = false
    if (pMouseY != null && pMouseX != null) {
        if (pMouseX.toInt() >= x.toInt() && pMouseX.toInt() <= x.toInt() + width &&
            pMouseY.toInt() >= y.toInt() && pMouseY.toInt() <= y.toInt() + textRenderer.lineHeight
        ) {
            isHovered = true
        }
    }
    return isHovered
}

@JvmOverloads
fun drawTextJustifiedRight(
    context: GuiGraphicsExtractor,
    font: Identifier? = null,
    text: MutableComponent,
    x: Number,
    y: Number,
    colour: Int,
    shadow: Boolean = true
) {
    val comp = text.let { if (font != null) it.font(font) else it }
    val font = Minecraft.getInstance().font
    context.text(font, comp, x.toInt() - font.width(comp), y.toInt(), opaqueTextColour(colour), shadow)
}

@JvmOverloads
fun drawText(
    context: GuiGraphicsExtractor,
    text: FormattedCharSequence,
    x: Number,
    y: Number,
    centered: Boolean = false,
    colour: Int,
    shadow: Boolean = true
) {
    val textRenderer = Minecraft.getInstance().font
    var tweakedX = x
    if (centered) {
        val width = textRenderer.width(text)
        tweakedX = tweakedX.toDouble() - width / 2
    }
    context.text(textRenderer, text, tweakedX.toInt(), y.toInt(), opaqueTextColour(colour), shadow)
}

@JvmOverloads
fun drawString(
    context: GuiGraphicsExtractor,
    text: String,
    x: Number,
    y: Number,
    colour: Int,
    shadow: Boolean = true,
    font: Identifier? = null
) {
    val comp = Component.literal(text).also {
        font?.let { identifier -> it.style = it.style.withFont(FontDescription.Resource(identifier)) }
    }
    val textRenderer = Minecraft.getInstance().font
    context.text(textRenderer, comp, x.toInt(), y.toInt(), opaqueTextColour(colour), shadow)
}

@JvmOverloads
fun drawPosablePortrait(
    identifier: Identifier,
    matrixStack: PoseStack,
    scale: Float = 13F,
    contextScale: Float = 1F,
    reversed: Boolean = false,
    state: PosableState,
    partialTicks: Float,
    limbSwing: Float = 0F,
    limbSwingAmount: Float = 0F,
    ageInTicks: Float = 0F,
    headYaw: Float = 0F,
    headPitch: Float = 0F,
    doQuirks: Boolean = true,
    r: Float = 1F,
    g: Float = 1F,
    b: Float = 1F,
    a: Float = 1F
) {
    if (PokemonSpecies.getByIdentifier(identifier) == null) {
        drawGenericPosablePortrait(
            identifier = identifier,
            matrixStack = matrixStack,
            scale = scale,
            contextScale = contextScale,
            reversed = reversed,
            state = state,
            partialTicks = partialTicks,
            limbSwing = limbSwing,
            limbSwingAmount = limbSwingAmount,
            ageInTicks = ageInTicks,
            headYaw = headYaw,
            headPitch = headPitch,
            doQuirks = doQuirks,
            r = r,
            g = g,
            b = b,
            a = a
        )
        return
    }

    drawPosablePortrait(
        identifier = identifier,
        matrixStack = poseToGuiMatrix(matrixStack),
        scale = scale,
        contextScale = contextScale,
        reversed = reversed,
        state = state,
        partialTicks = partialTicks,
        limbSwing = limbSwing,
        limbSwingAmount = limbSwingAmount,
        ageInTicks = ageInTicks,
        headYaw = headYaw,
        headPitch = headPitch,
        doQuirks = doQuirks,
        r = r,
        g = g,
        b = b,
        a = a
    )
}

fun drawPosablePortrait(
    identifier: Identifier,
    matrixStack: Matrix3x2fStack,
    scale: Float = 13F,
    contextScale: Float = 1F,
    reversed: Boolean = false,
    state: PosableState,
    partialTicks: Float,
    limbSwing: Float = 0F,
    limbSwingAmount: Float = 0F,
    ageInTicks: Float = 0F,
    headYaw: Float = 0F,
    headPitch: Float = 0F,
    doQuirks: Boolean = true,
    r: Float = 1F,
    g: Float = 1F,
    b: Float = 1F,
    a: Float = 1F
) {
    if (PokemonSpecies.getByIdentifier(identifier) == null) {
        drawGenericPosablePortrait(
            identifier = identifier,
            matrixStack = guiMatrixToPose(matrixStack),
            scale = scale,
            contextScale = contextScale,
            reversed = reversed,
            state = state,
            partialTicks = partialTicks,
            limbSwing = limbSwing,
            limbSwingAmount = limbSwingAmount,
            ageInTicks = ageInTicks,
            headYaw = headYaw,
            headPitch = headPitch,
            doQuirks = doQuirks,
            r = r,
            g = g,
            b = b,
            a = a
        )
        return
    }

    drawLegacyLitPosablePortrait(
        identifier = identifier,
        matrixStack = matrixStack,
        context = createMatrixGuiGraphics(matrixStack),
        scale = scale,
        contextScale = contextScale,
        reversed = reversed,
        state = state,
        partialTicks = partialTicks,
        limbSwing = limbSwing,
        limbSwingAmount = limbSwingAmount,
        ageInTicks = ageInTicks,
        headYaw = headYaw,
        headPitch = headPitch,
        doQuirks = doQuirks,
        r = r,
        g = g,
        b = b,
        a = a
    )
}

fun drawDialoguePosablePortrait(
    context: GuiGraphicsExtractor,
    identifier: Identifier,
    matrixStack: Matrix3x2fStack,
    scale: Float = 13F,
    contextScale: Float = 1F,
    reversed: Boolean = false,
    state: PosableState,
    partialTicks: Float,
    limbSwing: Float = 0F,
    limbSwingAmount: Float = 0F,
    ageInTicks: Float = 0F,
    headYaw: Float = 0F,
    headPitch: Float = 0F,
    doQuirks: Boolean = true,
    r: Float = 1F,
    g: Float = 1F,
    b: Float = 1F,
    a: Float = 1F
) {
    if (PokemonSpecies.getByIdentifier(identifier) != null) {
        drawPosablePortrait(
            identifier = identifier,
            matrixStack = matrixStack,
            scale = scale,
            contextScale = contextScale,
            reversed = reversed,
            state = state,
            partialTicks = partialTicks,
            limbSwing = limbSwing,
            limbSwingAmount = limbSwingAmount,
            ageInTicks = ageInTicks,
            headYaw = headYaw,
            headPitch = headPitch,
            doQuirks = doQuirks,
            r = r,
            g = g,
            b = b,
            a = a
        )
        return
    }

    drawDialogueGenericPosablePortrait(
        context = context,
        identifier = identifier,
        matrixStack = guiMatrixToPose(matrixStack),
        scale = scale,
        contextScale = contextScale,
        reversed = reversed,
        state = state,
        partialTicks = partialTicks,
        limbSwing = limbSwing,
        limbSwingAmount = limbSwingAmount,
        ageInTicks = ageInTicks,
        headYaw = headYaw,
        headPitch = headPitch,
        doQuirks = doQuirks,
        r = r,
        g = g,
        b = b,
        a = a
    )
}

private data class DialoguePreviewTarget(
    val colorTexture: GpuTexture,
    val depthTexture: GpuTexture,
)

private data class DialoguePreviewTargetKey(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val width: Int,
    val height: Int
)

private object DialogueNpcPortraitRenderer {
    private val projectionBuffer = CachedOrthoProjectionMatrixBuffer("Cobblemon dialogue NPC portrait", -1000.0F, 1000.0F, true)
    private val targets = linkedMapOf<DialoguePreviewTargetKey, DialoguePreviewTarget>()
    private val transientViews = mutableListOf<GpuTextureView>()
    private var lastGuiTick = Int.MIN_VALUE

    private fun closeTarget(target: DialoguePreviewTarget) {
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

    private fun getOrCreateTarget(bounds: DialoguePreviewTargetKey, width: Int, height: Int): DialoguePreviewTarget {
        resetTargetsForGuiTick()
        targets[bounds]?.let { return it }

        val device = RenderSystem.getDevice()
        val colorTexture = device.createTexture(
            { "Cobblemon dialogue NPC portrait color" },
            GpuTexture.USAGE_COPY_DST or GpuTexture.USAGE_TEXTURE_BINDING or GpuTexture.USAGE_RENDER_ATTACHMENT,
            com.mojang.blaze3d.textures.TextureFormat.RGBA8,
            width,
            height,
            1,
            1
        )
        val depthTexture = device.createTexture(
            { "Cobblemon dialogue NPC portrait depth" },
            GpuTexture.USAGE_COPY_DST or GpuTexture.USAGE_RENDER_ATTACHMENT,
            com.mojang.blaze3d.textures.TextureFormat.DEPTH32,
            width,
            height,
            1,
            1
        )
        return DialoguePreviewTarget(colorTexture, depthTexture).also { targets[bounds] = it }
    }

    fun render(
        context: GuiGraphicsExtractor,
        identifier: Identifier,
        matrixStack: PoseStack,
        scale: Float,
        contextScale: Float,
        reversed: Boolean,
        state: PosableState,
        partialTicks: Float,
        limbSwing: Float,
        limbSwingAmount: Float,
        ageInTicks: Float,
        headYaw: Float,
        headPitch: Float,
        doQuirks: Boolean,
        r: Float,
        g: Float,
        b: Float,
        a: Float
    ) {
        val anchorPose = matrixStack.last().pose()
        val anchorX = anchorPose.m30()
        val anchorY = anchorPose.m31()
        val left = (anchorX - DialogueScreen.PORTRAIT_WIDTH / 2F).toInt()
        val top = anchorY.toInt()
        val right = left + DialogueScreen.PORTRAIT_WIDTH
        val bottom = top + DialogueScreen.PORTRAIT_HEIGHT
        val scissor = (context as GuiGraphicsExtractorAccessor).scissorStack.peek()
        val guiScale = Minecraft.getInstance().window.guiScale.toInt().coerceAtLeast(1)
        val pixelScale = guiScale.toFloat()
        val width = (right - left) * guiScale
        val height = (bottom - top) * guiScale
        if (width <= 0 || height <= 0) {
            return
        }

        val target = getOrCreateTarget(DialoguePreviewTargetKey(left, top, right, bottom, width, height), width, height)
        val device = RenderSystem.getDevice()
        val colorAttachmentView = device.createTextureView(target.colorTexture)
        val colorSampleView = device.createTextureView(target.colorTexture)
        val depthAttachmentView = device.createTextureView(target.depthTexture)
        transientViews += colorAttachmentView
        transientViews += colorSampleView
        transientViews += depthAttachmentView

        device.createCommandEncoder().clearColorAndDepthTextures(target.colorTexture, 0, target.depthTexture, 1.0)

        RenderSystem.outputColorTextureOverride = colorAttachmentView
        RenderSystem.outputDepthTextureOverride = depthAttachmentView
        RenderSystem.setProjectionMatrix(projectionBuffer.getBuffer(width.toFloat(), height.toFloat()), ProjectionType.ORTHOGRAPHIC)

        try {
            val poseStack = PoseStack().apply {
                translate((anchorX - left) * pixelScale, (anchorY - top) * pixelScale, 0F)
                scale(scale * pixelScale, scale * pixelScale, -(scale * pixelScale))
            }
            val model = VaryingModelRepository.getPoser(identifier, state)
            val texture = VaryingModelRepository.getTexture(identifier, state)
            val renderContext = RenderContext()
            model.context = renderContext
            VaryingModelRepository.getTextureNoSubstitute(identifier, state).let { originalTexture ->
                renderContext.put(RenderContext.TEXTURE, originalTexture)
            }
            renderContext.put(RenderContext.SCALE, contextScale)
            renderContext.put(RenderContext.SPECIES, identifier)
            renderContext.put(RenderContext.ASPECTS, state.currentAspects)
            renderContext.put(RenderContext.POSABLE_STATE, state)
            renderContext.put(RenderContext.DO_QUIRKS, doQuirks)

            state.currentModel = model
            val originalPose = state.currentPose
            state.setPoseToFirstSuitable(PoseType.PORTRAIT)
            state.updatePartialTicks(partialTicks)
            model.applyAnimations(null, state, limbSwing, limbSwingAmount, ageInTicks, headYaw, headPitch)
            originalPose?.let(state::setPose)

            poseStack.translate(
                model.portraitTranslation.x * if (reversed) -1.0 else 1.0,
                model.portraitTranslation.y + 1.85 * model.portraitScale,
                model.portraitTranslation.z - 4.0
            )
            poseStack.scale(model.portraitScale, model.portraitScale, 1F / model.portraitScale)
            poseStack.mulPose(Axis.YP.rotationDegrees(if (reversed) 32F else -32F))
            poseStack.mulPose(Axis.XP.rotationDegrees(5F))

            val minecraft = Minecraft.getInstance()
            minecraft.gameRenderer.getLighting().setupFor(Lighting.Entry.ENTITY_IN_UI)
            LegacyGuiPreviewLights.withOverride {
                val bufferSource = minecraft.renderBuffers().bufferSource()
                val buffer = bufferSource.getBuffer(RenderTypes.entityCutout(texture))
                val packedLight = LightCoordsUtil.pack(15, 15)
                val colour = toHex(r, g, b, a)

                model.withLayerContext(bufferSource, state, VaryingModelRepository.getLayers(identifier, state)) {
                    model.render(renderContext, poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY, colour)
                    bufferSource.endBatch()
                }

                model.setDefault()
            }
        } finally {
            RenderSystem.outputColorTextureOverride = null
            RenderSystem.outputDepthTextureOverride = null
        }

        val guiRenderState = Minecraft.getInstance().gameRenderer.getGameRenderState().guiRenderState
        guiRenderState.addBlitToCurrentLayer(
            BlitRenderState(
                RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA,
                TextureSetup.singleTexture(colorSampleView, RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST)),
                Matrix3x2f(),
                left,
                top,
                right,
                bottom,
                0.0F,
                1.0F,
                1.0F,
                0.0F,
                -1,
                scissor,
                null
            )
        )
    }
}

private fun drawDialogueGenericPosablePortrait(
    context: GuiGraphicsExtractor,
    identifier: Identifier,
    matrixStack: PoseStack,
    scale: Float = 13F,
    contextScale: Float = 1F,
    reversed: Boolean = false,
    state: PosableState,
    partialTicks: Float,
    limbSwing: Float = 0F,
    limbSwingAmount: Float = 0F,
    ageInTicks: Float = 0F,
    headYaw: Float = 0F,
    headPitch: Float = 0F,
    doQuirks: Boolean = true,
    r: Float = 1F,
    g: Float = 1F,
    b: Float = 1F,
    a: Float = 1F
) {
    val sprite = VaryingModelRepository.getSprite(identifier, state, SpriteType.PORTRAIT)
    if (sprite != null) {
        renderSprite(matrixStack, sprite)
        return
    }

    DialogueNpcPortraitRenderer.render(
        context = context,
        identifier = identifier,
        matrixStack = matrixStack,
        scale = scale,
        contextScale = contextScale,
        reversed = reversed,
        state = state,
        partialTicks = partialTicks,
        limbSwing = limbSwing,
        limbSwingAmount = limbSwingAmount,
        ageInTicks = ageInTicks,
        headYaw = headYaw,
        headPitch = headPitch,
        doQuirks = doQuirks,
        r = r,
        g = g,
        b = b,
        a = a
    )
}

private fun drawGenericPosablePortrait(
    identifier: Identifier,
    matrixStack: PoseStack,
    scale: Float = 13F,
    contextScale: Float = 1F,
    reversed: Boolean = false,
    state: PosableState,
    partialTicks: Float,
    limbSwing: Float = 0F,
    limbSwingAmount: Float = 0F,
    ageInTicks: Float = 0F,
    headYaw: Float = 0F,
    headPitch: Float = 0F,
    doQuirks: Boolean = true,
    r: Float = 1F,
    g: Float = 1F,
    b: Float = 1F,
    a: Float = 1F
) {
    matrixStack.pushPose()
    try {
        matrixStack.translate(0.0, PORTRAIT_DIAMETER.toDouble() + 2.0, 0.0)
        matrixStack.scale(scale, scale, -scale)
        matrixStack.translate(0.0, -PORTRAIT_DIAMETER / 18.0, 0.0)

        val sprite = VaryingModelRepository.getSprite(identifier, state, SpriteType.PORTRAIT)
        if (sprite != null) {
            renderSprite(matrixStack, sprite)
            return
        }

        val model = VaryingModelRepository.getPoser(identifier, state)
        state.currentModel = model
        val texture = VaryingModelRepository.getTexture(identifier, state)

        val renderContext = RenderContext()
        model.context = renderContext
        VaryingModelRepository.getTextureNoSubstitute(identifier, state).let { renderContext.put(RenderContext.TEXTURE, it) }
        renderContext.put(RenderContext.SCALE, contextScale)
        renderContext.put(RenderContext.SPECIES, identifier)
        renderContext.put(RenderContext.ASPECTS, state.currentAspects)
        renderContext.put(RenderContext.POSABLE_STATE, state)
        renderContext.put(RenderContext.DO_QUIRKS, doQuirks)
        renderContext.put(RenderContext.RENDER_STATE, RenderContext.RenderState.PROFILE)

        val originalPose = state.currentPose
        state.setPoseToFirstSuitable(PoseType.PORTRAIT)
        state.updatePartialTicks(partialTicks)
        model.applyAnimations(null, state, limbSwing, limbSwingAmount, ageInTicks, headYaw, headPitch)
        originalPose?.let(state::setPose)

        matrixStack.translate(
            model.portraitTranslation.x * if (reversed) -1.0 else 1.0,
            model.portraitTranslation.y + 1.5 * model.portraitScale,
            model.portraitTranslation.z - 4.0
        )
        matrixStack.scale(model.portraitScale, model.portraitScale, 1F / model.portraitScale)
        matrixStack.mulPose(Axis.YP.rotationDegrees(if (reversed) 32F else -32F))
        matrixStack.mulPose(Axis.XP.rotationDegrees(5F))

        val minecraft = Minecraft.getInstance()
        val modelViewStack = RenderSystem.getModelViewStack()
        modelViewStack.pushMatrix()
        try {
            modelViewStack.mul(matrixStack.last().pose())
            minecraft.gameRenderer.getLighting().setupFor(Lighting.Entry.ENTITY_IN_UI)
            LegacyGuiPreviewLights.withOverride {
                val bufferSource = Minecraft.getInstance().renderBuffers().bufferSource()
                val buffer = bufferSource.getBuffer(RenderTypes.entityCutout(texture))
                val packedLight = LightCoordsUtil.pack(11, 7)
                val colour = toHex(r, g, b, a)

                model.withLayerContext(bufferSource, state, VaryingModelRepository.getLayers(identifier, state)) {
                    model.render(renderContext, matrixStack, buffer, packedLight, OverlayTexture.NO_OVERLAY, colour)
                    bufferSource.endBatch()
                }

                model.setDefault()
            }
        } finally {
            modelViewStack.popMatrix()
        }
    } finally {
        matrixStack.popPose()
    }
}

fun drawLegacyLitPosablePortrait(
    identifier: Identifier,
    matrixStack: Matrix3x2fStack,
    context: GuiGraphicsExtractor,
    scale: Float = 13F,
    contextScale: Float = 1F,
    reversed: Boolean = false,
    state: PosableState,
    partialTicks: Float,
    limbSwing: Float = 0F,
    limbSwingAmount: Float = 0F,
    ageInTicks: Float = 0F,
    headYaw: Float = 0F,
    headPitch: Float = 0F,
    doQuirks: Boolean = true,
    r: Float = 1F,
    g: Float = 1F,
    b: Float = 1F,
    a: Float = 1F
) {
    val species = PokemonSpecies.getByIdentifier(identifier) ?: return
    val effectiveScale = scale * guiScale(matrixStack)
    val anchorX = matrixStack.m20()
    val anchorY = matrixStack.m21() + PORTRAIT_DIAMETER + 2F - (effectiveScale * (PORTRAIT_DIAMETER / 18F))
    val radius = kotlin.math.ceil((effectiveScale * 2.5F).toDouble()).toInt().coerceAtLeast(16)
    drawLegacyLitProfilePokemon(
        species = species.resourceIdentifier,
        context = context,
        left = anchorX.toInt() - radius,
        top = anchorY.toInt() - radius,
        right = anchorX.toInt() + radius,
        bottom = anchorY.toInt() + radius,
        anchorX = anchorX,
        anchorY = anchorY,
        rotation = Quaternionf()
            .rotateY(Math.toRadians((if (reversed) 32.0 else -32.0)).toFloat())
            .rotateX(Math.toRadians(5.0).toFloat()),
        poseType = PoseType.PORTRAIT,
        state = state,
        partialTicks = partialTicks,
        scale = effectiveScale,
        applyProfileTransform = true,
        applyBaseScale = true,
        doQuirks = doQuirks,
        r = r,
        g = g,
        b = b,
        a = a,
        headYaw = headYaw,
        headPitch = headPitch,
        mirrorPortraitTranslationX = reversed
    )
}

fun drawProfile(
    resourceIdentifier: Identifier,
    matrixStack: PoseStack,
    state: PosableState,
    partialTicks: Float,
    scale: Float = 20F
) {
    if (PokemonSpecies.getByIdentifier(resourceIdentifier) != null) {
        drawProfile(
            resourceIdentifier = resourceIdentifier,
            matrixStack = poseToGuiMatrix(matrixStack),
            state = state,
            partialTicks = partialTicks,
            scale = scale
        )
        return
    }

    drawGenericProfile(
        resourceIdentifier = resourceIdentifier,
        matrixStack = matrixStack,
        state = state,
        partialTicks = partialTicks,
        scale = scale
    )
}

fun drawProfile(
    resourceIdentifier: Identifier,
    matrixStack: Matrix3x2fStack,
    state: PosableState,
    partialTicks: Float,
    scale: Float = 20F
) {
    if (PokemonSpecies.getByIdentifier(resourceIdentifier) != null) {
        drawProfilePokemon(
            species = resourceIdentifier,
            context = createMatrixGuiGraphics(matrixStack),
            originX = matrixStack.m20(),
            originY = matrixStack.m21(),
            rotation = Axis.YP.rotationDegrees(-32F).mul(Axis.XP.rotationDegrees(5F)),
            poseType = PoseType.PROFILE,
            state = state,
            partialTicks = partialTicks,
            scale = scale,
            applyProfileTransform = true,
            applyBaseScale = false
        )
        return
    }

    drawGenericProfile(
        resourceIdentifier = resourceIdentifier,
        matrixStack = guiMatrixToPose(matrixStack),
        state = state,
        partialTicks = partialTicks,
        scale = scale
    )
}

private fun drawGenericProfile(
    resourceIdentifier: Identifier,
    matrixStack: PoseStack,
    state: PosableState,
    partialTicks: Float,
    scale: Float = 20F
) {
    matrixStack.scale(scale, scale, -scale)

    val sprite = VaryingModelRepository.getSprite(resourceIdentifier, state, SpriteType.PROFILE)
    if (sprite == null) {
        val model = VaryingModelRepository.getPoser(resourceIdentifier, state)
        val texture = VaryingModelRepository.getTexture(resourceIdentifier, state)

        val context = RenderContext()
        model.context = context
        VaryingModelRepository.getTextureNoSubstitute(resourceIdentifier, state).let { context.put(RenderContext.TEXTURE, it) }
        context.put(RenderContext.SCALE, 1F)
        context.put(RenderContext.SPECIES, resourceIdentifier)
        context.put(RenderContext.ASPECTS, state.currentAspects)
        context.put(RenderContext.POSABLE_STATE, state)
        state.currentModel = model

        val renderType = RenderTypes.entityCutout(texture)

        val originalPose = state.currentPose
        state.setPoseToFirstSuitable(PoseType.PORTRAIT)
        state.updatePartialTicks(partialTicks)
        model.applyAnimations(null, state, 0F, 0F, state.animationSeconds * 20F, 0F, 0F)
        originalPose?.let { state.setPose(it) }
        matrixStack.translate(
            model.profileTranslation.x,
            model.profileTranslation.y + 1.5 * model.profileScale,
            model.profileTranslation.z - 4.0
        )
        matrixStack.scale(model.profileScale, model.profileScale, 1 / model.profileScale)
        matrixStack.mulPose(Axis.YP.rotationDegrees(-32F))
        matrixStack.mulPose(Axis.XP.rotationDegrees(5F))

        val minecraft = Minecraft.getInstance()
        minecraft.gameRenderer.getLighting().setupFor(Lighting.Entry.ENTITY_IN_UI)
        LegacyGuiPreviewLights.withOverride {
            val bufferSource = minecraft.renderBuffers().bufferSource()
            val buffer = bufferSource.getBuffer(renderType)
            val packedLight = LightCoordsUtil.pack(11, 7)

            model.withLayerContext(bufferSource, state, VaryingModelRepository.getLayers(resourceIdentifier, state)) {
                model.render(context, matrixStack, buffer, packedLight, OverlayTexture.NO_OVERLAY, -0x1)
                bufferSource.endBatch()
            }
        }
        model.setDefault()
    } else {
        renderSprite(matrixStack, sprite)
    }
}

fun renderSprite(matrixStack: PoseStack, sprite: Identifier) {
    renderSprite(poseToGuiMatrix(matrixStack), sprite)
}

fun renderSprite(matrixStack: Matrix3x2fStack, sprite: Identifier) {
    blitk(
        context = createMatrixGuiGraphics(matrixStack),
        texture = sprite,
        x = -1,
        y = 0,
        width = 2,
        height = 2
    )
}
