/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.helper

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.client.render.models.blockbench.animation.PrimaryAnimation
import com.cobblemon.mod.common.client.render.models.blockbench.egg.EggModel
import com.cobblemon.mod.common.client.render.models.blockbench.egg.EggState
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository
import com.cobblemon.mod.common.item.components.EggComponent
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.sounds.SoundEvent

object EggRenderHelper {
    val context = RenderContext().also {
        it.put(RenderContext.DO_QUIRKS, true)
        it.put(
            RenderContext.RENDER_STATE,
            RenderContext.RenderState.RESURRECTION_MACHINE
        ) // todo make new one that is false
    }

    /**
     * Renders the given egg with animations based on its time to hatch.
     *
     * @param egg The egg to render.
     * @param matrices The pose stack for transformations.
     * @param vertexConsumers The buffer for rendering.
     * @param light The light level.
     * @param overlay The overlay level.
     * @param yRot The rotation angle in degrees around the Y-axis.
     * @param scale The scale of the egg model.
     * @param translation Adjustments for translation in the form [x, y, z].
     * @param rotation Adjustments for rotation in the form [x, y, z].
     * @param animTime Current animation time in seconds.
     */
    fun renderEgg(
        egg: EggComponent,
        state: EggState,
        matrices: PoseStack,
        vertexConsumers: MultiBufferSource,
        light: Int,
        overlay: Int,
        yRot: Float,
        scale: Float = 1.0f,
        translation: Triple<Float, Float, Float> = Triple(0.5f, 0.5f, 0.5f),
        rotation: Triple<Float, Float, Float> = Triple(0f, 0f, 0f),
        tickDelta: Float
    ) {
        // Determine the texture
//        val form = if (egg.form.lowercase() == "normal") "" else "_${egg.form.lowercase()}"
//        val texture = cobblemonResource("textures/egg/${egg.species.path}${form}_egg.png")

        val species = egg.identifier
        state.currentAspects = setOf(egg.variant)
        state.updateAge(Minecraft.getInstance().level?.gameTime?.toInt() ?: 0)
        state.updatePartialTicks(tickDelta)

        val eggModel = VaryingModelRepository.getPoser(species, state) as? EggModel
            ?: run {
                Cobblemon.LOGGER.warn("Could not find egg model for $species and variant ${egg.variant}")
                return
            }

        eggModel.context = context

        val texture = VaryingModelRepository.getTexture(species, state)
        val vertexConsumer = vertexConsumers.getBuffer(RenderType.entityCutout(texture))

        state.currentModel = eggModel
        val poseName = when { // todo polish these values
            egg.ticksToHatch > 9600 -> "stage0" // Above 8 minutes left has No animation
            egg.ticksToHatch > 2400 -> "stage1" // 8 minutes left
            egg.ticksToHatch > 1200 -> "stage2"  // 2 minutes left
            egg.ticksToHatch > 20 -> "stage3"    // 1 minute left
            (egg.ticksToHatch <= 0F && state.isHatching) -> "hatching" // currently trying to hatch....
            else -> "stage4"                // Ready to hatch!
        }
        state.setPose(poseName)

        if (poseName == "hatching" && state.primaryAnimation == null) {
            val animation = eggModel.getAnimation(state, "hatch", eggModel.runtime)
            if (animation is PrimaryAnimation) {
                state.startHatchAnimation(animation)
            }
        }

        matrices.pushPose()
        matrices.translate(translation.first, translation.second, translation.third)
        matrices.mulPose(com.mojang.math.Axis.XP.rotationDegrees(rotation.first))
        matrices.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-yRot + rotation.second))
        matrices.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(rotation.third))
        matrices.scale(scale, scale, scale)

        context.put(RenderContext.TEXTURE, texture)
        context.put(RenderContext.RENDER_STATE, RenderContext.RenderState.RESURRECTION_MACHINE)
        context.put(RenderContext.POSABLE_STATE, state)

        matrices.pushPose()
        eggModel.applyAnimations(
            entity = null,
            state = state,
            headYaw = 0F,
            headPitch = 0F,
            limbSwing = 0F,
            limbSwingAmount = 0F,
            ageInTicks = state.animationSeconds * 20
        )

        eggModel.render(context, matrices, vertexConsumer, light, overlay, -0x1)
        eggModel.withLayerContext(
            vertexConsumers,
            state,
            VaryingModelRepository.getLayers(species, state)
        ) {
            eggModel.render(context, matrices, vertexConsumer, light, OverlayTexture.NO_OVERLAY, -0x1)
        }

        eggModel.setDefault()
        matrices.popPose()
        matrices.popPose()
    }

    fun playSound(soundEvent: SoundEvent) {
        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(soundEvent, 1.0F))
    }
}
