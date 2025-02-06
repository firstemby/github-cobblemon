/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.player

import com.bedrockk.molang.runtime.value.DoubleValue
import com.cobblemon.mod.common.client.entity.PokemonClientDelegate
import com.cobblemon.mod.common.client.render.models.blockbench.bedrock.animation.BedrockAnimationRepository
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.math.geometry.toRadians
import com.cobblemon.mod.common.util.withPlayerValue
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Vector3f

/**
 * @author landonjw
 */
object MountedPlayerRenderer {
    fun render(player: AbstractClientPlayer, entity: PokemonEntity, stack: PoseStack) {
        val camera = Minecraft.getInstance().gameRenderer.mainCamera
        val yaw = camera.yRot.toRadians()
        val isEntityFlying = false

        val offset = getOffset(player, entity, isEntityFlying, yaw)

        val matrix = stack.last().pose()
        if (isEntityFlying) {
            val cameraRotation = camera.rotation().get(Matrix4f())
            matrix.mul(cameraRotation)
        }
        matrix.translate(offset)
        if (isEntityFlying) matrix.rotateY(yaw)

        player.yBodyRot = player.yHeadRot
    }

    private fun getOffset(
        player: AbstractClientPlayer,
        entity: PokemonEntity,
        isEntityFlying: Boolean,
        yaw: Float
    ): Vector3f {
        val seats = entity.seats
        val seatIndex = entity.passengers.indexOf(player).takeIf { it != -1 && it < seats.size } ?: return player.position().toVector3f()
        val mountOrigin = (entity.delegate as PokemonClientDelegate).locatorStates["seat${seatIndex+1}"]?.getOrigin() ?: return Vector3f()
        val playerOrigin = Vec3(player.x, player.y, player.z)
        val offsetOrigin = mountOrigin.subtract(playerOrigin).subtract(0.0, 0.6, 0.0)

        val offsetMatrix = Matrix4f()
        if (isEntityFlying) offsetMatrix.rotateY(yaw)
        offsetMatrix.translate(offsetOrigin.toVector3f())
        return offsetMatrix.getTranslation(Vector3f())
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
        //state.runtime.environment.setSimpleVariable(" ", DoubleValue(ground_speed.toDouble()))

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