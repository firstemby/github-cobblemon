/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.player

import com.bedrockk.molang.runtime.value.DoubleValue
import com.cobblemon.mod.common.api.riding.RidingAnimation
import com.cobblemon.mod.common.client.entity.PokemonClientDelegate
import com.cobblemon.mod.common.client.render.models.blockbench.bedrock.animation.BedrockAnimationRepository
import com.cobblemon.mod.common.mixin.client.accessor.HumanoidModelAccessor
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.model.AnimationUtils
import net.minecraft.client.model.HumanoidModel
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.renderer.entity.state.HumanoidRenderState
import net.minecraft.world.phys.Vec3

object MountedPlayerRenderer {
    @JvmField
    var shouldApplyRootAnimation = false

    @JvmField
    val relevantPartsByName = mutableMapOf(
        "body" to ModelPart(emptyList(), emptyMap()),
    )

    private val defaultAnimations = listOf(
        RidingAnimation(
            fileName = "player",
            animationName = "animation.player.default_ride_pose"
        )
    )

    fun animate(
        model: HumanoidModel<*>,
        pokemonEntity: PokemonEntity,
        player: AbstractClientPlayer,
        renderState: HumanoidRenderState,
        seatIndex: Int,
        seatLocator: String?,
        poseType: PoseType?,
        headYaw: Float,
        headPitch: Float,
        ageInTicks: Float,
        limbSwing: Float,
        limbSwingAmount: Float
    ) {
        val resolvedSeatIndex = seatIndex.takeIf { it != -1 && it < pokemonEntity.seats.size }
            ?: pokemonEntity.passengers.indexOf(player).takeIf { it != -1 && it < pokemonEntity.seats.size }
            ?: return
        val resolvedSeatLocator = seatLocator ?: pokemonEntity.seats.getOrNull(resolvedSeatIndex)?.locator ?: "seat_${resolvedSeatIndex + 1}"
        val resolvedPoseType = poseType ?: pokemonEntity.getCurrentPoseType()
        val seat = pokemonEntity.seats[resolvedSeatIndex]

        val root = relevantPartsByName["body"] ?: return
        root.x = 0F
        root.y = 0F
        root.z = 0F
        root.xRot = 0F
        root.yRot = 0F
        root.zRot = 0F

        val matchedPoseAnimations = seat.poseAnimations
            ?.firstOrNull { it.poseTypes.isEmpty() || it.poseTypes.contains(resolvedPoseType) }
        val animations = matchedPoseAnimations?.animations ?: defaultAnimations
        val state = pokemonEntity.delegate as PokemonClientDelegate

        relevantPartsByName.values.forEach {
            it.xRot = 0F
            it.yRot = 0F
            it.zRot = 0F
        }

        state.runtime.environment.setSimpleVariable("age_in_ticks", DoubleValue(ageInTicks.toDouble()))
        state.runtime.environment.setSimpleVariable("limb_swing", DoubleValue(limbSwing.toDouble()))
        state.runtime.environment.setSimpleVariable("limb_swing_amount", DoubleValue(limbSwingAmount.toDouble()))
        state.runtime.environment.setSimpleVariable("head_yaw", DoubleValue(headYaw.toDouble()))
        state.runtime.environment.setSimpleVariable("head_pitch", DoubleValue(headPitch.toDouble()))

        for (animation in animations) {
            val bedrockAnimation = BedrockAnimationRepository.getAnimationOrNull(animation.fileName, animation.animationName) ?: continue
            bedrockAnimation.run(
                context = null,
                relevantPartsByName = relevantPartsByName,
                rootPart = null,
                state = state,
                animationSeconds = state.animationSeconds,
                limbSwing = limbSwing,
                limbSwingAmount = limbSwingAmount,
                ageInTicks = ageInTicks,
                intensity = 1F
            )
        }

        applyRegularAnimation(model, renderState)
    }

    fun applyRegularAnimation(
        model: HumanoidModel<*>,
        renderState: HumanoidRenderState
    ) {
        val accessor = model as HumanoidModelAccessor

        val rightHanded = renderState.mainArm == net.minecraft.world.entity.HumanoidArm.RIGHT
        if (renderState.isUsingItem) {
            val usingMainHand = renderState.useItemHand == net.minecraft.world.InteractionHand.MAIN_HAND
            if (usingMainHand == rightHanded) {
                accessor.`cobblemon$invokePoseRightArm`(renderState)
            } else {
                accessor.`cobblemon$invokePoseLeftArm`(renderState)
            }
        } else {
            val offHandIsTwoHanded = if (rightHanded) renderState.leftArmPose.isTwoHanded else renderState.rightArmPose.isTwoHanded
            if (rightHanded != offHandIsTwoHanded) {
                accessor.`cobblemon$invokePoseLeftArm`(renderState)
                accessor.`cobblemon$invokePoseRightArm`(renderState)
            } else {
                accessor.`cobblemon$invokePoseRightArm`(renderState)
                accessor.`cobblemon$invokePoseLeftArm`(renderState)
            }
        }

        accessor.`cobblemon$invokeSetupAttackAnimation`(renderState)

        if (renderState.rightArmPose != HumanoidModel.ArmPose.SPYGLASS) {
            AnimationUtils.bobModelPart(model.rightArm, renderState.ageInTicks, 1.0F)
        }

        if (renderState.leftArmPose != HumanoidModel.ArmPose.SPYGLASS) {
            AnimationUtils.bobModelPart(model.leftArm, renderState.ageInTicks, -1.0F)
        }

        model.hat.loadPose(model.head.storePose())
    }

    const val RIDING_SITTING_OFFSET = -10.0

    fun animateRoot(stack: PoseStack) {
        val root = relevantPartsByName["body"] ?: return
        val rootOffset = Vec3(root.x.toDouble(), root.y.toDouble() + RIDING_SITTING_OFFSET, root.z.toDouble()).scale(1 / 16.0)

        stack.mulPose(Axis.ZP.rotation(root.zRot))
        stack.mulPose(Axis.YP.rotation(root.yRot))
        stack.mulPose(Axis.XP.rotation(root.xRot))
        stack.translate(rootOffset.x.toFloat(), rootOffset.y.toFloat(), rootOffset.z.toFloat())
    }
}
