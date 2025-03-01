/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.block

import com.cobblemon.mod.common.CobblemonBlocks
import com.cobblemon.mod.common.block.entity.StadiumBlockEntity
import com.cobblemon.mod.common.client.render.models.blockbench.drone.DroneModel
import com.cobblemon.mod.common.client.render.models.blockbench.drone.DroneState
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository
import com.cobblemon.mod.common.util.cobblemonResource
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.state.BlockState

class StadiumBlockRenderer(ctx: BlockEntityRendererProvider.Context) : BlockEntityRenderer<StadiumBlockEntity> {
    val context = RenderContext().also {
        it.put(RenderContext.DO_QUIRKS, false)
        it.put(RenderContext.RENDER_STATE, RenderContext.RenderState.BLOCK)
    }

    private var pathIndex = 0
    private var animationTime = 0.0
    private val droneSpeed = 0.10

    override fun render(
            blockEntity: StadiumBlockEntity,
            tickDelta: Float,
            poseStack: PoseStack,
            multiBufferSource: MultiBufferSource,
            light: Int,
            overlay: Int
    ) {
        val blockState = blockEntity.blockState
        val yRot = blockState.getValue(HorizontalDirectionalBlock.FACING).toYRot()

        val isClientSide = blockEntity.level?.isClientSide == true
        if (!isClientSide) return

        val stadiumBlockState = blockEntity.level?.getBlockState(blockEntity.blockPos) ?: return
        if (stadiumBlockState.block != CobblemonBlocks.STADIUM) {
            return
        }

        val droneState = blockEntity.droneState


        poseStack.pushPose()
        if (blockEntity.isInUse) {
            if (blockEntity.pathPoints.isEmpty()) {

                poseStack.popPose()
                return
            }

            spawnDebugParticles(blockEntity) // debug particles

            // Center the drone at the block's center
            poseStack.translate(0.5, -3.0, 0.5)
            //poseStack.mulPose(Axis.XP.rotationDegrees(180F))
            //poseStack.mulPose(Axis.YP.rotationDegrees(180F))

            val points = blockEntity.pathPoints
            val nextIndex = (pathIndex + 1) % points.size

            val currentPoint = points[pathIndex]
            val nextPoint = points[nextIndex]

            animationTime += tickDelta * droneSpeed
            if (animationTime >= 1.0) {
                animationTime = 0.0
                pathIndex = nextIndex
            }

            val interpolatedPos = currentPoint.lerp(nextPoint, animationTime.toDouble())

            println("DEBUG: Drone moving between points:")
            println("  - Current Point: ${currentPoint}")
            println("  - Next Point: ${nextPoint}")
            println("  - Interpolated Position: $interpolatedPos")

            poseStack.translate(interpolatedPos.x - blockEntity.blockPos.x,
                    interpolatedPos.y - blockEntity.blockPos.y,
                    interpolatedPos.z - blockEntity.blockPos.z)

            println("DEBUG: Drone position interpolated to: $interpolatedPos")

            poseStack.mulPose(Axis.YP.rotationDegrees(-yRot))
            poseStack.mulPose(Axis.ZP.rotationDegrees(180F)) // flip the drone right side up

            renderDrone(droneState, "sleep", tickDelta, poseStack, 1.0F, multiBufferSource, light)
        } else {
            val yRot = blockState.getValue(HorizontalDirectionalBlock.FACING).toYRot()

            val poseEntry = poseStack.last()
            val matrix = poseEntry.pose()

            poseStack.translate(0.5, 0.0, 0.5)
            poseStack.mulPose(Axis.YP.rotationDegrees(yRot))

            val posX = matrix.m03()
            val posY = matrix.m13()
            val posZ = matrix.m23()

            println("DEBUG: Drone is NOT in use. Rendering default position at: x=$posX, y=$posY, z=$posZ")

            poseStack.mulPose(Axis.ZP.rotationDegrees(180F))
            renderDrone(droneState, "sleep", tickDelta, poseStack, 1.0F, multiBufferSource, light)
        }

        poseStack.popPose()
    }

    fun renderDrone(
        state: DroneState,
        pose: String,
        tickDelta: Float,
        poseStack: PoseStack,
        scale: Float = 1F,
        multiBufferSource: MultiBufferSource,
        light: Int,
    ) {
        state.updatePartialTicks(tickDelta)

        val droneResource: ResourceLocation = cobblemonResource("rotom_drone")

        val model = VaryingModelRepository.getPoser(droneResource, state) as DroneModel
        val texture = VaryingModelRepository.getTexture(droneResource, state)

        val buffer = multiBufferSource.getBuffer(RenderType.entityCutout(texture))
        model.bufferProvider = multiBufferSource

        state.currentModel = model
        state.setPose(pose)

        model.context = context
        context.put(RenderContext.TEXTURE, texture)
        context.put(RenderContext.RENDER_STATE, RenderContext.RenderState.BLOCK)
        context.put(RenderContext.POSABLE_STATE, state)

        model.applyAnimations(
            entity = null,
            state = state,
            headYaw = 0F,
            headPitch = 0F,
            limbSwing = 0F,
            limbSwingAmount = 0F,
            ageInTicks = state.animationSeconds * 20
        )

        poseStack.scale(scale, scale, scale)

        // TODO: Make drone lighting dynamic (simply using the light arg makes the drone always appear dark)
        model.withLayerContext(multiBufferSource, state, VaryingModelRepository.getLayers(droneResource, state)) {
            model.render(context, poseStack, buffer, 200, OverlayTexture.NO_OVERLAY, -0x1)
        }
        model.setDefault()
    }

    private fun spawnDebugParticles(blockEntity: StadiumBlockEntity) {
        val world = blockEntity.level ?: return
        if (!world.isClientSide) return // Only run on the client-side

        for (point in blockEntity.pathPoints) {
            world.addParticle(
                    net.minecraft.core.particles.ParticleTypes.FLAME, // Test particle (changeable)
                    point.x, point.y, point.z, // Position of the particle
                    0.0, 0.02, 0.0 // Slight upward motion
            )
        }

        // Spawn a special particle at the next interpolation point
        if (blockEntity.pathPoints.isNotEmpty()) {
            val nextPoint = blockEntity.pathPoints[(pathIndex + 1) % blockEntity.pathPoints.size]
            world.addParticle(
                    net.minecraft.core.particles.ParticleTypes.END_ROD, // Different effect for next point
                    nextPoint.x, nextPoint.y, nextPoint.z,
                    0.0, 0.05, 0.0
            )
        }
    }
}