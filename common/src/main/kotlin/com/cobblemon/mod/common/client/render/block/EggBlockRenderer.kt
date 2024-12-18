package com.cobblemon.mod.common.client.render.block

import com.cobblemon.mod.common.api.pokemon.breeding.Egg
import com.cobblemon.mod.common.block.entity.EggBlockEntity
import com.cobblemon.mod.common.client.render.models.blockbench.TexturedModel
import com.cobblemon.mod.common.client.render.models.blockbench.repository.EggModelRepo
import com.cobblemon.mod.common.client.render.layer.CobblemonRenderLayers
import com.cobblemon.mod.common.util.cobblemonResource
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.resources.ResourceLocation

class EggBlockRenderer(ctx: BlockEntityRendererProvider.Context) : BlockEntityRenderer<EggBlockEntity> {
    override fun render(
            entity: EggBlockEntity,
            tickDelta: Float,
            matrices: PoseStack,
            vertexConsumers: MultiBufferSource,
            light: Int,
            overlay: Int
    ) {
        entity.getEgg()?.let { egg ->
            matrices.pushPose()

            // Get the texture based on species
            val texture = getTextureForSpecies(egg)
            val model = EggModelRepo.eggModels[cobblemonResource("egg_models/egg")] ?: return

            // Render the egg model with the specified texture
            renderEggModel(model, texture, matrices, vertexConsumers, light, overlay)

            matrices.popPose()
        }
    }

    private fun renderEggModel(
            model: TexturedModel,
            texture: ResourceLocation,
            matrices: PoseStack,
            vertexConsumers: MultiBufferSource,
            light: Int,
            overlay: Int
    ) {
        // Retrieve the buffer for the texture
        val buffer = vertexConsumers.getBuffer(CobblemonRenderLayers.ENTITY_CUTOUT.apply(texture))

        // Bake and render the model
        val bakedModel = model.createWithUvOverride(false, 0, 0, 64, 64).bakeRoot()
        bakedModel.render(matrices, buffer, light, overlay)
    }

    private fun getTextureForSpecies(egg: Egg): ResourceLocation {
        // Generate texture path dynamically based on species
        return cobblemonResource("textures/egg/${egg.species.path}_egg.png")
    }
}
