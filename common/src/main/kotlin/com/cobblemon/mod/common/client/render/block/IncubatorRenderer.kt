package com.cobblemon.mod.common.client.render.block

import com.cobblemon.mod.common.api.pokemon.breeding.Egg
import com.cobblemon.mod.common.block.entity.IncubatorBlockEntity
import com.cobblemon.mod.common.client.render.models.blockbench.TexturedModel
import com.cobblemon.mod.common.client.render.models.blockbench.repository.EggModelRepo
import com.cobblemon.mod.common.util.cobblemonResource
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.resources.ResourceLocation

class IncubatorRenderer(ctx: BlockEntityRendererProvider.Context) : BlockEntityRenderer<IncubatorBlockEntity> {

    override fun render(
        entity: IncubatorBlockEntity,
        tickDelta: Float,
        matrices: PoseStack,
        vertexConsumers: MultiBufferSource,
        light: Int,
        overlay: Int
    ) {
        // Ensure the renderer runs only on the client
        val isClientSide = entity.level?.isClientSide ?: false
        if (!isClientSide) return

        // Retrieve the egg data from the entity
        val egg = entity.getEgg() ?: return

        // Load the egg model
        val model = EggModelRepo.eggModels[cobblemonResource("egg")]
        if (model == null) {
            println("Egg model not found in EggModelRepo!")
            return
        }

        // Dynamically determine the texture for the egg
        val texture = getTextureForSpecies(egg)

        // Push pose stack for transformations
        matrices.pushPose()

        // Center, raise, and flip the egg
        matrices.translate(0.5, 0.4, 0.5) // Move to block center and raise
        matrices.mulPose(com.mojang.math.Axis.XP.rotationDegrees(180f)) // Flip upside down
        matrices.scale(1.0f, 1.0f, 1.0f) // Scale down for better appearance

        // Render the egg model with the specified texture
        renderEggModel(model, texture, matrices, vertexConsumers, light, overlay)

        matrices.popPose() // Restore the matrix stack
    }

    private fun renderEggModel(
        model: TexturedModel,
        texture: ResourceLocation,
        matrices: PoseStack,
        vertexConsumers: MultiBufferSource,
        light: Int,
        overlay: Int
    ) {
        // Use the RenderType to render with the correct texture
        val buffer = vertexConsumers.getBuffer(RenderType.entityCutout(texture))

        // Bake and render the model
        val bakedModel = model.createWithUvOverride(false, 0, 0, 64, 16).bakeRoot()
        bakedModel.render(matrices, buffer, light, overlay)
    }

    private fun getTextureForSpecies(egg: Egg): ResourceLocation {
        // Dynamically construct the texture path based on the species
        return cobblemonResource("textures/egg/${egg.species.path}_egg.png")
    }
}
