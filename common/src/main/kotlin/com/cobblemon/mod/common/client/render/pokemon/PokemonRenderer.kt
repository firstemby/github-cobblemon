/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.pokemon

import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.buffers.Std140Builder
import com.mojang.blaze3d.buffers.Std140SizeCalculator
import com.mojang.blaze3d.systems.RenderSystem
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.OrientationControllable
import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.battle.ClientBallDisplay
import com.cobblemon.mod.common.client.entity.NPCClientDelegate
import com.cobblemon.mod.common.client.entity.PokemonClientDelegate
import com.cobblemon.mod.common.client.keybind.boundKey
import com.cobblemon.mod.common.client.keybind.keybinds.PartySendBinding
import com.cobblemon.mod.common.client.render.item.HeldItemRenderer
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState
import com.cobblemon.mod.common.client.render.models.blockbench.pose.ModelPartTransformation
import com.cobblemon.mod.common.client.render.models.blockbench.repository.MiscModelRepository
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository
import com.cobblemon.mod.common.client.render.pokeball.PokeBallPosableState
import com.cobblemon.mod.common.client.render.renderBeaconBeam
import com.cobblemon.mod.common.client.render.models.blockbench.pokemon.PosablePokemonEntityModel
import com.cobblemon.mod.common.entity.PlatformType
import com.cobblemon.mod.common.entity.PosableEntity
import com.cobblemon.mod.common.client.settings.ServerSettings
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.entity.npc.NPCEntity
import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokeball.PokeBall
import com.cobblemon.mod.common.util.math.DoubleRange
import com.cobblemon.mod.common.util.math.geometry.toRadians
import com.cobblemon.mod.common.util.math.remap
import com.cobblemon.mod.common.util.effectiveName
import com.cobblemon.mod.common.util.isLookingAt
import com.cobblemon.mod.common.util.lang
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.ChatFormatting
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font.DisplayMode
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.LivingEntityRenderer
import net.minecraft.client.renderer.entity.MobRenderer
import net.minecraft.client.renderer.feature.ItemFeatureRenderer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier
import net.minecraft.util.ARGB
import net.minecraft.util.Mth
import net.minecraft.world.entity.Pose
import net.minecraft.world.phys.Vec3
import net.minecraft.client.renderer.texture.OverlayTexture
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import org.joml.Quaternionf
import org.lwjgl.system.MemoryStack
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.max
import kotlin.math.sqrt
import com.cobblemon.mod.common.client.render.AnimatedModelTextureSupplier
import net.minecraft.util.LightCoordsUtil

internal object LegacyGuiPreviewLights {
    private val uboSize = Std140SizeCalculator().putVec3().putVec3().get()
    private val lights = arrayOf(
        Vector3f(-1F, 1F, 1F),
        Vector3f(1.3F, -1F, 1F)
    )

    private val slice: GpuBufferSlice by lazy {
        MemoryStack.stackPush().use { stack ->
            val data = Std140Builder.onStack(stack, uboSize)
                .putVec3(lights[0])
                .putVec3(lights[1])
                .get()
            RenderSystem.getDevice()
                .createBuffer(
                    { "Cobblemon legacy GUI preview lights" },
                    GpuBuffer.USAGE_UNIFORM or GpuBuffer.USAGE_COPY_DST,
                    data
                )
                .slice()
        }
    }

    fun <T> withOverride(block: () -> T): T {
        val previous = RenderSystem.getShaderLights()
        RenderSystem.setShaderLights(slice)
        return try {
            block()
        } finally {
            if (previous != null) {
                RenderSystem.setShaderLights(previous)
            }
        }
    }
}

class PokemonRenderer(
    context: EntityRendererProvider.Context
) : MobRenderer<PokemonEntity, PokemonRenderState, PosablePokemonEntityModel>(context, PosablePokemonEntityModel(), 0.5f) {
    private val heldItemRenderer = HeldItemRenderer()
    private val ballContext = RenderContext().also {
        it.put(RenderContext.RENDER_STATE, RenderContext.RenderState.WORLD)
    }

    private fun snapshotRelevantParts(model: com.cobblemon.mod.common.client.render.models.blockbench.PosableModel): List<ModelPartTransformation> {
        return model.relevantPartsByName.values.distinct().map(ModelPartTransformation::derive)
    }

    private fun restoreRelevantParts(snapshot: List<ModelPartTransformation>) {
        snapshot.forEach(ModelPartTransformation::set)
    }

    companion object {
        private val LEVEL_LABEL_STYLE = Style.EMPTY.withColor(ChatFormatting.WHITE)
            .withBold(false)
            .withItalic(false)
            .withUnderlined(false)
            .withStrikethrough(false)
            .withObfuscated(false)
        private val recallBeamColour = Vector4f(1F, 0.1F, 0.1F, 1F)
        private const val HIDDEN_NAME = "???"
        private const val SPACE = " "

        fun ease(x: Double): Double {
            return 1 - (1 - x) * (1 - x) * (1 - x)
        }
    }

    override fun createRenderState() = PokemonRenderState()

    override fun extractRenderState(entity: PokemonEntity, reusedState: PokemonRenderState, partialTick: Float) {
        super.extractRenderState(entity, reusedState, partialTick)

        val delegate = entity.delegate as? PokemonClientDelegate ?: return
        val species = entity.pokemon.species.resourceIdentifier

        reusedState.pokemonEntity = entity
        reusedState.poseState = delegate
        reusedState.species = species
        reusedState.aspects = entity.pokemon.aspects
        reusedState.texture = VaryingModelRepository.getTexture(species, delegate)
        reusedState.baseScale = entity.pokemon.form.baseScale
        reusedState.modelScale = entity.pokemon.form.baseScale * entity.pokemon.scaleModifier * delegate.entityScaleModifier
        reusedState.heldItem = entity.shownItem.copy()
        reusedState.shadowSize =
            (min(
                entity.boundingBox.maxX - entity.boundingBox.minX,
                entity.boundingBox.maxZ - entity.boundingBox.minZ
            ).toFloat() / 1.5F * delegate.entityScaleModifier) / entity.scale

        val freezeFrame = entity.entityData.get(PokemonEntity.FREEZE_FRAME)
        if (freezeFrame != -1F) {
            delegate.updateAge(0)
            delegate.updatePartialTicks(freezeFrame * 20F)
        } else {
            delegate.updatePartialTicks(partialTick)
        }
        reusedState.ageInTicks = delegate.animationSeconds * 20F
        delegate.currentAspects = entity.pokemon.aspects
        delegate.updateLocatorPosition(entity.position())
    }

    override fun getTextureLocation(state: PokemonRenderState): Identifier = state.texture

    override fun getShadowRadius(state: PokemonRenderState): Float = state.shadowSize

    override fun scale(state: PokemonRenderState, poseStack: PoseStack) {
        poseStack.scale(state.modelScale, state.modelScale, state.modelScale)
    }

    override fun submit(state: PokemonRenderState, poseStack: PoseStack, collector: SubmitNodeCollector, cameraRenderState: CameraRenderState) {
        val poseState = state.poseState ?: return
        val species = state.species ?: return
        val modelNow = VaryingModelRepository.getPoser(species, poseState)
        val isGuiPreview = state.pokemonEntity == null
        val animationEntity = state.pokemonEntity ?: state.guiPreviewEntity
        val renderContext = RenderContext().also {
            it.put(RenderContext.RENDER_STATE, if (isGuiPreview) RenderContext.RenderState.PROFILE else RenderContext.RenderState.WORLD)
            it.put(RenderContext.SCALE, state.baseScale)
            it.put(RenderContext.SPECIES, species)
            it.put(RenderContext.ASPECTS, state.aspects)
            it.put(RenderContext.TEXTURE, state.texture)
            it.put(RenderContext.POSABLE_STATE, poseState)
            it.put(RenderContext.DO_QUIRKS, state.doQuirks)
        }
        animationEntity?.let {
            renderContext.put(RenderContext.ENTITY, it)
        } ?: run {
            renderContext.put(RenderContext.POSE_TYPE, state.poseType)
        }

        modelNow.context = renderContext
        poseState.currentModel = modelNow
        poseState.currentAspects = state.aspects

        if (animationEntity is PokemonEntity && poseState is PokemonClientDelegate && animationEntity.passengers.isNotEmpty()) {
            val controller = (animationEntity as? OrientationControllable)?.orientationController
            if (controller?.active == true) {
                poseState.applyRenderRotation(poseState.getPartialTicks())
            }
        }

        if (animationEntity != null) {
            modelNow.applyAnimations(
                animationEntity,
                poseState,
                state.walkAnimationPos,
                state.walkAnimationSpeed,
                state.ageInTicks,
                state.yRot,
                state.xRot
            )
        } else {
            val originalPose = poseState.currentPose
            poseState.setPoseToFirstSuitable(state.poseType)
            modelNow.applyAnimations(
                null,
                poseState,
                0F,
                0F,
                state.ageInTicks,
                state.previewHeadYaw,
                state.previewHeadPitch
            )
            originalPose?.let(poseState::setPose)
        }

        val worldBufferSource = if (!isGuiPreview) Minecraft.getInstance().renderBuffers().bufferSource() else null
        val pokemonEntity = state.pokemonEntity
        val pokemonDelegate = if (!isGuiPreview) pokemonEntity?.delegate as? PokemonClientDelegate else null
        var renderedTransition = false
        if (pokemonEntity != null && pokemonDelegate != null && pokemonEntity.beamMode != 0 && !Minecraft.getInstance().isPaused) {
            renderTransition(
                modelNow = modelNow,
                beamMode = pokemonEntity.beamMode,
                entity = pokemonEntity,
                partialTicks = pokemonDelegate.getPartialTicks(),
                poseMatrix = poseStack,
                buffer = worldBufferSource ?: return,
                packedLight = state.lightCoords,
                clientDelegate = pokemonDelegate
            )
            renderedTransition = true
        }

        poseStack.pushPose()

        if (isGuiPreview) {
            if (state.applyPortraitTransform) {
                poseStack.translate(
                    modelNow.portraitTranslation.x,
                    modelNow.portraitTranslation.y + 1.5 * modelNow.portraitScale,
                    modelNow.portraitTranslation.z - 4.0
                )
                poseStack.scale(modelNow.portraitScale, modelNow.portraitScale, 1F / modelNow.portraitScale)
            } else if (state.applyProfileTransform) {
                poseStack.translate(
                    modelNow.profileTranslation.x,
                    modelNow.profileTranslation.y + 1.5 * modelNow.profileScale,
                    modelNow.profileTranslation.z - 4.0
                )
                poseStack.scale(modelNow.profileScale, modelNow.profileScale, 1F / modelNow.profileScale)
            } else {
                poseStack.translate(0F, 0F, -4F)
                if (state.applyBaseScale) {
                    poseStack.scale(state.baseScale, state.baseScale, 1F / state.baseScale)
                }
            }
            state.previewRotation?.let(poseStack::mulPose)
        } else {
            if (pokemonEntity != null && pokemonDelegate != null && pokemonEntity.passengers.isNotEmpty()) {
                val controller = (pokemonEntity as? OrientationControllable)?.orientationController
                if (controller?.active == true) {
                    val partialTicks = pokemonDelegate.getPartialTicks()
                    val yaw = Mth.rotLerp(partialTicks, pokemonEntity.yBodyRotO, pokemonEntity.yBodyRot)
                    val center = Vector3f(0f, pokemonEntity.bbHeight / 2, 0f)
                    val transformationMatrix = Matrix4f()
                    transformationMatrix.translate(center)
                    transformationMatrix.rotate(controller.getRenderOrientation(partialTicks))
                    transformationMatrix.translate(center.negate(Vector3f()))
                    transformationMatrix.rotate(Axis.YP.rotationDegrees(yaw + 180f))
                    poseStack.last().pose().mul(transformationMatrix)
                }
            }

            if (state.hasPose(Pose.SLEEPING)) {
                state.bedOrientation?.let { bedOrientation ->
                    val eyeHeight = state.eyeHeight - 0.1F
                    poseStack.translate(
                        -bedOrientation.stepX.toFloat() * eyeHeight,
                        0F,
                        -bedOrientation.stepZ.toFloat() * eyeHeight
                    )
                }
            }

            val entityScale = state.scale
            poseStack.scale(entityScale, entityScale, entityScale)
            setupRotations(state, poseStack, state.bodyRot, entityScale)
            poseStack.scale(-1F, -1F, 1F)
            scale(state, poseStack)
            poseStack.translate(0F, -1.501F, 0F)
        }

        val bodyVisible = isBodyVisible(state)
        val translucentBody = !bodyVisible && !state.isInvisibleToPlayer
        val renderType = getRenderType(state, bodyVisible, translucentBody, state.appearsGlowing())
        if (renderType != null) {
            val overlay = LivingEntityRenderer.getOverlayCoords(state, getWhiteOverlayProgress(state))
            val colour = ARGB.multiply(if (translucentBody) 654311423 else -1, getModelTint(state))

            if (isGuiPreview) {
                val partSnapshot = snapshotRelevantParts(modelNow)
                collector.submitCustomGeometry(poseStack, renderType) { pose, consumer ->
                    val renderStack = PoseStack()
                    renderStack.last().set(pose)
                    LegacyGuiPreviewLights.withOverride {
                        restoreRelevantParts(partSnapshot)
                        modelNow.render(renderContext, renderStack, consumer, state.lightCoords, overlay, colour)
                        modelNow.setDefault()
                    }
                }

                for (layer in VaryingModelRepository.getLayers(species, poseState)) {
                    val texture = layer.texture?.invoke(poseState) ?: continue
                    val layerRenderType =
                        if (layer.scrolling != null) {
                            modelNow.getScrollingLayer(texture, layer.scrolling)
                        } else if (layer.texture is AnimatedModelTextureSupplier && layer.texture.interpolation) {
                            modelNow.getLayer(texture, layer.emissive, layer.translucent, layer.translucent_cull)
                        } else {
                            modelNow.getLayer(texture, layer.emissive, layer.translucent, layer.translucent_cull)
                        }
                    val tint = layer.tint
                    val rgba = colour
                    val alpha = ARGB.alpha(rgba) / 255F
                    val red = ARGB.red(rgba) / 255F
                    val green = ARGB.green(rgba) / 255F
                    val blue = ARGB.blue(rgba) / 255F
                    val tintColor =
                        ((alpha * tint.w * 255).toInt() shl 24) or
                        ((red * tint.x * 255).toInt() shl 16) or
                        ((green * tint.y * 255).toInt() shl 8) or
                        (blue * tint.z * 255).toInt()

                    collector.submitCustomGeometry(poseStack, layerRenderType) { pose, consumer ->
                        val renderStack = PoseStack()
                        renderStack.last().set(pose)
                        LegacyGuiPreviewLights.withOverride {
                            restoreRelevantParts(partSnapshot)
                            modelNow.rootPart.render(renderStack, consumer, state.lightCoords, overlay, tintColor)
                            modelNow.setDefault()
                        }
                    }
                }
            } else {
                // The old PosableEntityModel wrapper added this offset in renderToBuffer.
                poseStack.translate(0F, 1.5F, 0F)
                modelNow.withLayerContext(worldBufferSource!!, poseState, VaryingModelRepository.getLayers(species, poseState)) {
                    val buffer = worldBufferSource.getBuffer(renderType)
                    modelNow.render(renderContext, poseStack, buffer, state.lightCoords, overlay, colour)
                }
                worldBufferSource.endBatch()
            }

            if (isGuiPreview) {
                heldItemRenderer.renderOnModel(
                    item = state.heldItem,
                    state = poseState,
                    poseStack = poseStack,
                    collector = collector,
                    light = LightCoordsUtil.pack(15, 15),
                    outlineColor = state.outlineColor,
                    frontLight = true,
                    entity = animationEntity
                )
            }
        }

        modelNow.setDefault()
        modelNow.red = 1F
        modelNow.green = 1F
        modelNow.blue = 1F
        modelNow.resetLayerContext()
        poseStack.popPose()
        if (!isGuiPreview && renderType == null && renderedTransition) {
            worldBufferSource!!.endBatch()
        }
        if (!isGuiPreview && renderType != null) {
            renderCobblemonLabel(state, poseStack, worldBufferSource!!, cameraRenderState)
            heldItemRenderer.renderOnModel(
                item = state.heldItem,
                state = poseState,
                poseStack = poseStack,
                collector = collector,
                light = state.lightCoords,
                outlineColor = state.outlineColor,
                entity = animationEntity
            )
            worldBufferSource.endBatch()
        }
    }

    private fun renderTransition(
        modelNow: com.cobblemon.mod.common.client.render.models.blockbench.PosableModel,
        beamMode: Int,
        entity: PokemonEntity,
        partialTicks: Float,
        poseMatrix: PoseStack,
        buffer: MultiBufferSource,
        packedLight: Int,
        clientDelegate: PokemonClientDelegate
    ) {
        val s = clientDelegate.secondsSinceBeamEffectStarted
        if (beamMode == 3 && s > PokemonClientDelegate.BEAM_EXTEND_TIME) {
            val value = (s - PokemonClientDelegate.BEAM_EXTEND_TIME) / PokemonClientDelegate.BEAM_SHRINK_TIME
            val colourValue = 1F - min(0.6F, value)
            modelNow.green = colourValue
            modelNow.blue = colourValue
        }

        val phaseTarget = clientDelegate.phaseTarget
            ?: entity.entityData.get(PokemonEntity.PHASING_TARGET_ID)
                .takeIf { it != -1 }
                ?.let {
                    clientDelegate.setPhaseTarget(it)
                    clientDelegate.phaseTarget
                }
            ?: return
        poseMatrix.pushPose()
        var beamSourcePosition = if (phaseTarget is NPCEntity) {
            val npcDelegate = phaseTarget.delegate as NPCClientDelegate
            val baseScale = phaseTarget.renderScale.toDouble()
            (npcDelegate.locatorStates["beam"]?.getOrigin()?.scale(baseScale))
                ?: phaseTarget.position().add(0.0, (phaseTarget.bbHeight / 2.0) * baseScale, 0.0)
        } else if (phaseTarget is PosableEntity) {
            (phaseTarget.delegate as PosableState).locatorStates["beam"]?.getOrigin() ?: phaseTarget.position()
        } else {
            if (phaseTarget.uuid == Minecraft.getInstance().player?.uuid) {
                val lookVec = phaseTarget.lookAngle.yRot((Mth.PI / 2).toFloat()).multiply(1.0, 0.0, 1.0).normalize()
                phaseTarget.getEyePosition(partialTicks).subtract(0.0, 0.4, 0.0).subtract(lookVec.scale(0.3))
            } else {
                val lookVec =
                    phaseTarget.lookAngle
                        .yRot((Mth.PI / 2 - (phaseTarget.visualRotationYInDegrees - phaseTarget.xRot).toRadians()).toFloat())
                        .multiply(1.0, 0.0, 1.0)
                        .normalize()
                phaseTarget.getEyePosition(partialTicks).subtract(0.0, 0.7, 0.0).subtract(lookVec.scale(0.4))
            }
        }

        if (clientDelegate.sendOutPosition == null && beamMode == 1) {
            clientDelegate.sendOutPosition = beamSourcePosition
        } else if (beamMode == 1) {
            clientDelegate.sendOutPosition = clientDelegate.sendOutPosition!!.add(0.0, 0.04, 0.0)
            beamSourcePosition = clientDelegate.sendOutPosition!!
        }

        val offsetDirection = beamSourcePosition.subtract(entity.position()).normalize().scale(-clientDelegate.ballOffset.toDouble())
        val facingDir: Vec3
        with(beamSourcePosition.subtract(entity.position())) {
            var newOffset = offsetDirection.scale(2.0)
            val distance = beamSourcePosition.distanceTo(entity.position())
            newOffset = newOffset.scale((distance / 10.0) * 5)
            facingDir = newOffset.normalize()
            clientDelegate.sendOutOffset = newOffset
            poseMatrix.translate(x + newOffset.x, y + newOffset.y, z + newOffset.z)
        }

        val dir = beamSourcePosition.subtract(entity.position()).normalize()
        val angle = Mth.atan2(dir.z, dir.x) - Mth.PI / 2
        poseMatrix.mulPose(Axis.YP.rotation(-angle.toFloat() + Mth.PI.toFloat()))

        if (beamMode == 1 && !clientDelegate.ballDone) {
            if (entity.pokemon.caughtBall.name.toString().contains("beast")) {
                val xAngleFacingDir = Mth.atan2(facingDir.y, sqrt(facingDir.x * facingDir.x + facingDir.z * facingDir.z))
                poseMatrix.mulPose(Axis.XP.rotation(-xAngleFacingDir.toFloat()))
            }
            drawPokeBall(
                state = ClientBallDisplay(entity.pokemon.caughtBall, setOf()),
                matrixStack = poseMatrix,
                scale = clientDelegate.ballOffset,
                partialTicks = partialTicks,
                buff = buffer,
                packedLight = packedLight,
                ball = CobblemonClient.storage.party.firstOrNull { it?.uuid == entity.pokemon.uuid }?.caughtBall
                    ?: clientDelegate.currentEntity.pokemon.caughtBall,
                distance = ceil(beamSourcePosition.distanceTo(entity.position()) / 4f).toInt()
            )
        }
        poseMatrix.popPose()

        if (beamMode == 3) {
            renderBeam(
                matrixStack = poseMatrix,
                partialTicks = partialTicks,
                entity = entity,
                beamTarget = phaseTarget,
                buffer = buffer,
                offset = offsetDirection
            )
        }
    }

    private fun renderBeam(
        matrixStack: PoseStack,
        partialTicks: Float,
        entity: PokemonEntity,
        beamTarget: net.minecraft.world.entity.Entity,
        buffer: MultiBufferSource,
        offset: Vec3
    ) {
        val clientDelegate = entity.delegate as? PokemonClientDelegate ?: return
        val pokemonPosition = entity.position().add(0.0, entity.bbHeight / 2.0 * clientDelegate.entityScaleModifier.toDouble(), 0.0)
        var beamSourcePosition = if (beamTarget is EmptyPokeBallEntity) {
            (beamTarget.delegate as PokeBallPosableState).locatorStates["beam"]?.getOrigin() ?: beamTarget.position()
        } else if (beamTarget.uuid == Minecraft.getInstance().player?.uuid) {
            val lookVec = beamTarget.lookAngle.yRot((Math.PI / 2).toFloat()).multiply(1.0, 0.0, 1.0).normalize()
            beamTarget.getEyePosition(partialTicks).subtract(0.0, 0.4, 0.0).subtract(lookVec.scale(0.3))
        } else if (beamTarget is NPCEntity) {
            val npcDelegate = beamTarget.delegate as NPCClientDelegate
            val baseScale = beamTarget.renderScale * beamTarget.hitboxScale
            (npcDelegate.locatorStates["beam"]?.getOrigin()?.scale(baseScale.toDouble()))
                ?: beamTarget.position().add(0.0, (beamTarget.bbHeight / 2.0) * baseScale, 0.0)
        } else {
            val lookVec =
                beamTarget.lookAngle
                    .yRot((Math.PI / 2 - (beamTarget.visualRotationYInDegrees - beamTarget.xRot).toRadians()).toFloat())
                    .multiply(1.0, 0.0, 1.0)
                    .normalize()
            beamTarget.getEyePosition(partialTicks).subtract(0.0, 0.7, 0.0).subtract(lookVec.scale(0.4))
        }

        if (clientDelegate.sendOutPosition != null) {
            beamSourcePosition = clientDelegate.sendOutPosition!!
        }

        if (beamSourcePosition.distanceTo(pokemonPosition) > 20) {
            return
        }

        var newOffset = offset.scale(2.0)
        val distance = beamSourcePosition.distanceTo(entity.position())
        newOffset = newOffset.scale((distance / 10.0) * 5)
        newOffset = newOffset.multiply(0.0, 1 + ease(clientDelegate.ballOffset.toDouble()), 0.0)
        val direction = pokemonPosition.subtract(beamSourcePosition.add(newOffset)).let { Vector3f(it.x.toFloat(), it.y.toFloat(), it.z.toFloat()) }

        matrixStack.pushPose()
        with(beamSourcePosition.subtract(entity.position())) {
            matrixStack.translate(x + newOffset.x, y + newOffset.y, z + newOffset.z)
        }

        val s = clientDelegate.secondsSinceBeamEffectStarted
        val ratio = if (s < PokemonClientDelegate.BEAM_EXTEND_TIME) {
            s / PokemonClientDelegate.BEAM_EXTEND_TIME
        } else if (s > PokemonClientDelegate.BEAM_EXTEND_TIME + PokemonClientDelegate.BEAM_SHRINK_TIME) {
            1 - min((s - PokemonClientDelegate.BEAM_EXTEND_TIME - PokemonClientDelegate.BEAM_SHRINK_TIME) / PokemonClientDelegate.BEAM_EXTEND_TIME, 1F)
        } else {
            1F
        }

        direction.normalize()
        val yAxis = Vector3f(0F, 1F, 0F)
        val dot = direction.dot(yAxis)
        val cross = yAxis.cross(direction)
        val q = Quaternionf(cross.x, cross.y, cross.z, 1 + dot).normalize()
        matrixStack.mulPose(q)

        renderBeaconBeam(
            matrixStack = matrixStack,
            buffer = buffer,
            partialTicks = partialTicks,
            totalLevelTime = entity.level().gameTime,
            height = pokemonPosition.distanceTo(beamSourcePosition.add(offset)).toFloat() * ratio,
            red = recallBeamColour.x,
            green = recallBeamColour.y,
            blue = recallBeamColour.z,
            alpha = recallBeamColour.w,
            beamRadius = 0.03F,
            glowRadius = 0.07F,
            glowAlpha = 0.4F
        )

        matrixStack.popPose()
    }

    private fun renderCobblemonLabel(
        state: PokemonRenderState,
        poseStack: PoseStack,
        bufferSource: net.minecraft.client.renderer.MultiBufferSource.BufferSource,
        cameraRenderState: CameraRenderState
    ) {
        val entity = state.pokemonEntity ?: return
        if (!shouldRenderLabel(entity)) {
            return
        }

        val player = Minecraft.getInstance().player ?: return
        val distance = this.entityRenderDispatcher.distanceToSqr(entity)
        if (distance > 4096.0) {
            return
        }

        val label = resolveBaseLabel(entity)
        if (ServerSettings.displayEntityLevelLabel && entity.labelLevel() > 0) {
            if (ServerSettings.displayEntityNameLabel) {
                label.append(SPACE)
            }
            label.append(lang("label.lv", entity.labelLevel()).setStyle(LEVEL_LABEL_STYLE))
        }

        val scale = min(1.5, kotlin.math.max(0.65, ((distance + 16.0) / 112.0)))
        val sizeScale = 0.5 + ((scale - 0.65) / 0.85).coerceIn(0.0, 1.0) * 0.5
        val offsetScale = ((scale - 0.65) / 0.85).coerceIn(0.0, 1.0)
        val opacity = (Minecraft.getInstance().options.getBackgroundOpacity(0.25F) * 255.0F).toInt() shl 24
        val packedLight = LightCoordsUtil.pack(15, 15)
        val entityHeight = entity.boundingBox.ysize + 0.5F

        poseStack.pushPose()
        poseStack.translate(0.0, entityHeight.toDouble(), 0.0)
        poseStack.mulPose(cameraRenderState.orientation)
        poseStack.translate(0.0, offsetScale / 2.0, -(scale + offsetScale))
        poseStack.scale((0.025 * sizeScale).toFloat(), (-0.025 * sizeScale).toFloat(), sizeScale.toFloat())

        val matrix = poseStack.last().pose()
        var x = (-this.font.width(label) / 2).toFloat()
        this.font.drawInBatch(label, x, 0F, 0x20FFFFFF, false, matrix, bufferSource, DisplayMode.SEE_THROUGH, opacity, packedLight)
        this.font.drawInBatch(label, x, 0F, -1, false, matrix, bufferSource, DisplayMode.NORMAL, 0, packedLight)

        if (CobblemonClient.clientPlayerData.showChallengeLabel && entity.canBattle(player)) {
            val battlePrompt = lang("challenge_label", PartySendBinding.boundKey().displayName)
            x = (-this.font.width(battlePrompt) / 2).toFloat()
            this.font.drawInBatch(battlePrompt, x, 10F, 0x20FFFFFF, false, matrix, bufferSource, DisplayMode.SEE_THROUGH, opacity, packedLight)
            this.font.drawInBatch(battlePrompt, x, 10F, -1, false, matrix, bufferSource, DisplayMode.NORMAL, 0, packedLight)
        }

        poseStack.popPose()
    }

    private fun shouldRenderLabel(entity: PokemonEntity): Boolean {
        if (!entity.shouldShowName()) {
            return false
        }
        if (entity.entityData.get(PokemonEntity.HIDE_LABEL)) {
            return false
        }

        val player = Minecraft.getInstance().player ?: return false
        if (player.vehicle == entity) {
            return false
        }
        val delegate = entity.delegate as? PokemonClientDelegate ?: return false
        return (!Cobblemon.config.displayEntityLabelsWhenCrouchingOnly || player.isCrouching) &&
            player.isLookingAt(entity) &&
            delegate.phaseTarget == null &&
            !CobblemonClient.pokedexUsageContext.scanningGuiOpen
    }

    private fun resolveBaseLabel(entity: PokemonEntity): MutableComponent {
        return when {
            !ServerSettings.displayEntityNameLabel -> Component.empty()
            Cobblemon.config.displayNameForUnknownPokemon ||
                CobblemonClient.clientPokedexData.getKnowledgeForSpecies(entity.pokemon.species.resourceIdentifier) != PokedexEntryProgress.NONE ->
                entity.effectiveName().copy()
            else -> Component.literal(HIDDEN_NAME)
        }
    }

    private fun drawPokeBall(
        state: ClientBallDisplay,
        matrixStack: PoseStack,
        scale: Float = 5F,
        partialTicks: Float,
        buff: MultiBufferSource,
        packedLight: Int,
        ball: PokeBall,
        distance: Int
    ) {
        matrixStack.pushPose()
        matrixStack.scale(0.7F, -0.7F, -0.7F)
        val model = VaryingModelRepository.getPoser(ball.name, state)
        val texture = VaryingModelRepository.getTexture(ball.name, state)
        if (scale == 1.0f) {
            model.moveToPose(state, model.poses["open"]!!)
        } else {
            matrixStack.translate(0.0, -0.2, 0.0)
            val rotation = 360F * distance
            if (ball.name.toString().contains("beast")) {
                matrixStack.mulPose(Axis.ZN.rotationDegrees(Mth.lerp(scale, 0F, rotation)))
            } else {
                matrixStack.mulPose(Axis.XN.rotationDegrees(Mth.lerp(scale, 0F, rotation)))
            }
            matrixStack.translate(0.0, 0.2, 0.0)
        }
        state.updatePartialTicks(partialTicks)
        model.context = ballContext
        ballContext.put(RenderContext.ASPECTS, state.aspects)
        ballContext.put(RenderContext.POSABLE_STATE, state)
        model.applyAnimations(null, state, 0F, 0F, 0F, 0F, 0F)
        val vertexBuffer = ItemFeatureRenderer.getFoilBuffer(buff, RenderTypes.entityCutout(texture), false, false)
        model.render(ballContext, matrixStack, vertexBuffer, packedLight, OverlayTexture.NO_OVERLAY, -0x1)
        model.green = 1F
        model.blue = 1F
        model.red = 1F
        model.resetLayerContext()
        matrixStack.popPose()
    }
}
