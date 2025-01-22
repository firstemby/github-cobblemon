package com.cobblemon.mod.common.client.render.block

import com.cobblemon.mod.common.CobblemonItemComponents
import com.cobblemon.mod.common.api.pokemon.breeding.Egg
import com.cobblemon.mod.common.block.entity.NestBlockEntity
import com.cobblemon.mod.common.client.render.helper.EggRenderHelper
import com.cobblemon.mod.common.client.render.models.blockbench.TexturedModel
import com.cobblemon.mod.common.client.render.models.blockbench.repository.EggModelRepo
import com.cobblemon.mod.common.item.components.EggComponent
import com.cobblemon.mod.common.util.cobblemonResource
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.resources.ResourceLocation

class NestBlockRenderer(ctx: BlockEntityRendererProvider.Context) : BlockEntityRenderer<NestBlockEntity> {

    override fun render(
            entity: NestBlockEntity,
            tickDelta: Float,
            matrices: PoseStack,
            vertexConsumers: MultiBufferSource,
            light: Int,
            overlay: Int
    ) {
        val isClientSide = entity.level?.isClientSide ?: false
        if (!isClientSide) return

        val egg = entity.getItem().get(CobblemonItemComponents.EGG_DATA)?.egg ?: return
        val timeToHatch = egg.timeToHatch ?: return

        val animationName = when {
            timeToHatch < 200 -> "animation.egg.nestjiggletiny"
            timeToHatch < 500 -> "animation.egg.nestjigglesmall"
            timeToHatch < 1000 -> "animation.egg.nestjigglemid"
            else -> "animation.egg.nestjigglelarge"
        }

        val animTime = (System.currentTimeMillis() % 2000) / 1000.0f

        EggRenderHelper.renderEgg(
                egg = egg,
                matrices = matrices,
                vertexConsumers = vertexConsumers,
                light = light,
                overlay = overlay,
                yRot = 0f,
                scale = 1f,
                translation = Triple(0.5f, 0.42f, 0.5f),
                rotation = Triple(180f, 0f, 0f),
                animation = animationName,
                animTime = animTime
        )
    }
}
