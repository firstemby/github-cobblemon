/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.player

import com.bedrockk.molang.runtime.value.DoubleValue
import com.cobblemon.mod.common.api.riding.Rideable
import com.cobblemon.mod.common.client.MountedPokemonAnimationRenderController
import com.cobblemon.mod.common.client.entity.PokemonClientDelegate
import com.cobblemon.mod.common.client.render.models.blockbench.bedrock.animation.BedrockAnimationRepository
import com.cobblemon.mod.common.client.render.models.blockbench.getRotation
import com.cobblemon.mod.common.client.render.models.blockbench.pose.ModelPartTransformation.Companion.defaultRuntime
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.resolveBoolean
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import org.joml.AxisAngle4f
import org.joml.Vector3f

/**
 * @author landonjw
 */
object MountedPlayerRenderer {

    // Dummy part to hold animation data
    var bodyPart: ModelPart = ModelPart(emptyList(),mutableMapOf())

    fun render(player: AbstractClientPlayer, entity: PokemonEntity, stack: PoseStack, bob: Float, yBodyRot: Float, partialTicks: Float, i: Float) {
        if(player.vehicle !is Rideable) return
        val matrix = stack.last().pose()

        val seatIndex = entity.passengers.indexOf(player)
        val seat = entity.seats[seatIndex]
        val delegate = entity.delegate as PokemonClientDelegate
        val locator = delegate.locatorStates[seat.locator]

        //Positions player
        if (locator != null) {
            MountedPokemonAnimationRenderController.setup(entity, partialTicks)

            //Undo seat position
            val playerPos = Vec3(
                Mth.lerp(partialTicks.toDouble(), player.xOld, player.x),
                Mth.lerp(partialTicks.toDouble(), player.yOld, player.y),
                Mth.lerp(partialTicks.toDouble(), player.zOld, player.z),
            )

            val entityPos = Vec3(
                Mth.lerp(partialTicks.toDouble(), entity.xOld, entity.x),
                Mth.lerp(partialTicks.toDouble(), entity.yOld, entity.y),
                Mth.lerp(partialTicks.toDouble(), entity.zOld, entity.z),
            )


            matrix.translate(playerPos.subtract(entityPos).toVector3f().negate())
            matrix.translate(locator.matrix.getTranslation(Vector3f()))

            //val offset = Vector3f(0f, player.bbHeight / 2, 0f).mul(-1f)

            matrix.rotate(locator.matrix.getRotation(AxisAngle4f()))

            matrix.translate(
                bodyPart.x / 16.0f,
                -bodyPart.y / 16.0f,
                bodyPart.z / 16.0f
            )
            matrix.rotateXYZ(
                -bodyPart.xRot,
                -bodyPart.yRot,
                -bodyPart.zRot
            )
            matrix.rotate(Axis.YP.rotationDegrees(180 + yBodyRot))
            //matrix.translate(offset)

            //matrix.translate(Vector3f(0f, 0.35f, 0f))
        }
    }

    fun animate(
        pokemonEntity: PokemonEntity,
        player: AbstractClientPlayer,
        relevantPartsByName: MutableMap<String, ModelPart>,
        headYaw: Float,
        headPitch: Float,
        ageInTicks: Float,
        limbSwing: Float,
        limbSwingAmount: Float
    ) {
        val seatIndex = pokemonEntity.passengers.indexOf(player).takeIf { it != -1 && it < pokemonEntity.seats.size } ?: return
        val seat = pokemonEntity.seats[seatIndex]
        val animations = seat.poseAnimations.firstOrNull { it.poseTypes.isEmpty() || it.poseTypes.contains(pokemonEntity.getCurrentPoseType()) }?.animations
            ?: return
        val state = pokemonEntity.delegate as PokemonClientDelegate

        // Add in the dummy body part to gather body animation data
        relevantPartsByName["body"] = bodyPart

        bodyPart.x = 0.0f
        bodyPart.y = 0.0f
        bodyPart.z = 0.0f

        var rotation = floatArrayOf(0F, 0F, 0F)
        val scale = floatArrayOf(1F, 1F, 1F)

        // Reset all relevant parts to their base values
        relevantPartsByName.values.forEach {
            it.xRot = rotation[0]
            it.yRot = rotation[1]
            it.zRot = rotation[2]
            it.xScale = scale[0]
            it.yScale = scale[1]
            it.zScale = scale[2]
        }

        state.runtime.environment.setSimpleVariable("age_in_ticks", DoubleValue(ageInTicks.toDouble()))
        state.runtime.environment.setSimpleVariable("limb_swing", DoubleValue(limbSwing.toDouble()))
        state.runtime.environment.setSimpleVariable("limb_swing_amount", DoubleValue(limbSwingAmount.toDouble()))

        // TODO add the q.player reference, requires a cached thing or something on the client idk

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
    }
}
