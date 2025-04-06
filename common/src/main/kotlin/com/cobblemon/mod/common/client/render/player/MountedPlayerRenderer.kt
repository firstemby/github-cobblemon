/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.player

import com.bedrockk.molang.runtime.value.DoubleValue
import com.cobblemon.mod.common.OrientationControllable
import com.cobblemon.mod.common.api.riding.Rideable
import com.cobblemon.mod.common.client.entity.PokemonClientDelegate
import com.cobblemon.mod.common.client.render.models.blockbench.bedrock.animation.BedrockAnimationRepository
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.cobblemonResource
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.player.RemotePlayer
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import org.joml.Matrix3f
import org.joml.Matrix4f
import org.joml.Vector3f

/**
 * @author landonjw
 */
object MountedPlayerRenderer {
    const val disableRollableRenderDebug: Boolean = false

    fun render(player: AbstractClientPlayer, entity: PokemonEntity, stack: PoseStack, bob: Float, yBodyRot: Float, partialTicks: Float, i: Float) {
        if(player.vehicle !is Rideable) return
        val matrix = stack.last().pose()

        val seatIndex = entity.passengers.indexOf(player)
        val seat = entity.seats[seatIndex]
        val delegate = entity.delegate as PokemonClientDelegate
        val locator = delegate.locatorStates[seat.locator]

        //Positions player
        if (locator != null) {
            val locatorOffset = locator.matrix.getTranslation(Vector3f())

            val center = Vector3f(0f, entity.bbHeight/2, 0f)
            val locatorOffsetToCenter = locatorOffset.sub(center, Vector3f())

            val transformationMatrix = Matrix4f()
            //if (player is Rollable && player.shouldRoll()){
                //transformationMatrix.rotate(Axis.YP.rotationDegrees(-yBodyRot))
                //transformationMatrix.mulLocal(player.orientation)
            //}

            val rotatedOffset = transformationMatrix.transformPosition(locatorOffsetToCenter, Vector3f()).add(center).sub(Vector3f(0f, player.bbHeight/2, 0f))
            matrix.translate(rotatedOffset)

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
        }

        //Rotates player
        if (player is OrientationControllable && player.orientationController.active && !disableRollableRenderDebug) {
            val center = Vector3f(0f, player.bbHeight / 2, 0f)
            val transformationMatrix = Matrix4f()
            transformationMatrix.translate(center)
            transformationMatrix.rotate(player.orientationController.getRenderOrientation(partialTicks))
            transformationMatrix.translate(center.negate(Vector3f()))
            //Pre-Undo Yaw
            transformationMatrix.rotate(Axis.YP.rotationDegrees(yBodyRot+180f))
            matrix.mul(transformationMatrix)
        }
        matrix.translate(0f, 0.25f, 0f)
    }

    fun animate(
        pokemonEntity: PokemonEntity,
        player: AbstractClientPlayer,
        relevantPartsByName: Map<String, ModelPart>,
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

        relevantPartsByName.values.forEach {
            it.xRot = 0F
            it.yRot = 0F
            it.zRot = 0F
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
