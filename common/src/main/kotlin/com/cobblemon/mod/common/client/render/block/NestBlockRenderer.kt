package com.cobblemon.mod.common.client.render.block

import com.cobblemon.mod.common.api.pokemon.breeding.Egg
import com.cobblemon.mod.common.block.entity.NestBlockEntity
import com.cobblemon.mod.common.client.render.models.blockbench.TexturedModel
import com.cobblemon.mod.common.client.render.models.blockbench.repository.EggModelRepo
import com.cobblemon.mod.common.client.render.layer.CobblemonRenderLayers
import com.cobblemon.mod.common.util.cobblemonResource
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
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
        // Log whether this is running on the client or server
        val isClientSide = entity.level?.isClientSide ?: false
        if (isClientSide) {
            println("NestBlockRenderer: Running on the CLIENT side.")
        } else {
            println("NestBlockRenderer: Running on the SERVER side.")
        }

        val egg = entity.getEgg()
        if (egg == null) {
            println("No egg found in NestBlockEntity at ${entity.blockPos}")
            return
        } else {
            println("Rendering egg with species: ${egg.species}")
        }

        // Load the egg model
        var model = EggModelRepo.eggModels[cobblemonResource("egg_models/egg")]
        if (model == null) {
            println("Egg model not found in EggModelRepo!")
            return
        }
        // Load the species-specific texture dynamically
        var texture = getTextureForSpecies(egg)

        // Force-rendering a Charmander egg for debugging
        println("DEBUG: Forcing Charmander egg render.")
        texture = cobblemonResource("textures/egg/charmander_egg.png")
        model = EggModelRepo.eggModels[cobblemonResource("egg_models/egg")]
        if (model == null) {
            println("Egg model not found in EggModelRepo!")
            return
        }



        // Push pose stack for proper positioning
        matrices.pushPose()
        matrices.translate(0.5, 0.0, 0.5) // Center the egg in the block space
        matrices.scale(0.75f, 0.75f, 0.75f) // Scale down the egg slightly for better appearance

        // Render the model with the correct texture
        renderEggModel(model, texture, matrices, vertexConsumers, light, overlay)

        matrices.popPose()
    }


    private fun renderEggModel(
            model: TexturedModel,
            texture: ResourceLocation,
            matrices: PoseStack,
            vertexConsumers: MultiBufferSource,
            light: Int,
            overlay: Int
    ) {
        // Use RenderType to render the model with the correct texture
        val buffer = vertexConsumers.getBuffer(RenderType.entityCutout(texture))

        // Bake and render the model
        val bakedModel = model.createWithUvOverride(false, 0, 0, 64, 64).bakeRoot()
        bakedModel.render(matrices, buffer, light, overlay)
    }

    private fun getTextureForSpecies(egg: Egg): ResourceLocation {
        // Construct the texture path dynamically based on the species
        return cobblemonResource("textures/egg/${egg.species.path}_egg.png")
    }
}
