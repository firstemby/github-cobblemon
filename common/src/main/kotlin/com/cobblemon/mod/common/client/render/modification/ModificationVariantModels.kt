/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.modification

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.modification.ModificationVariant
import com.cobblemon.mod.common.block.entity.ModificationVariantBlockEntity
import com.cobblemon.mod.common.api.modification.ModificationVariants
import com.cobblemon.mod.common.block.modification.ModificationVariantBlock
import com.cobblemon.mod.common.block.modification.ModificationVariantStructures
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.Sheets
import net.minecraft.client.resources.model.BakedModel
import net.minecraft.client.resources.model.ModelResourceLocation
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState

object ModificationVariantModels {
    private const val VARIANT_MODEL_FOLDER = "mod_station/"
    private const val STANDALONE_VARIANT = "standalone"
    private val itemModels = hashMapOf<ResourceLocation, BakedModel>()
    private val missingModelLocations = hashSetOf<ResourceLocation>()

    @JvmStatic
    fun isVariantModel(location: ResourceLocation): Boolean {
        return location.path.startsWith("block/$VARIANT_MODEL_FOLDER") || location.path.startsWith("item/$VARIANT_MODEL_FOLDER")
    }

    @JvmStatic
    fun bakedIdentifier(location: ResourceLocation): ModelResourceLocation = ModelResourceLocation(location, STANDALONE_VARIANT)

    @JvmStatic
    fun clearCache() {
        itemModels.clear()
        missingModelLocations.clear()
    }

    @JvmStatic
    fun isTranslucentVariant(stack: ItemStack): Boolean = ModificationVariants.fromStack(stack)?.isTranslucent == true

    @JvmStatic
    fun itemModel(stack: ItemStack): BakedModel? {
        val variant = ModificationVariants.fromStack(stack) ?: return null
        val model = bakedModel(variant, variant.model) ?: return null
        return itemModels.getOrPut(variant.model) { ModificationVariantItemModel(model, variant) }
    }

    fun bakedModel(variant: ModificationVariant, modelLocation: ResourceLocation): BakedModel? {
        val modelManager = Minecraft.getInstance().modelManager
        val model = modelManager.getModel(bakedIdentifier(modelLocation))
        if (model !== modelManager.missingModel) {
            return model
        }
        if (missingModelLocations.add(modelLocation)) {
            Cobblemon.LOGGER.warn("Modification variant {} references model {} which is not under a mod_station model folder or does not exist", variant.identifier, modelLocation)
        }
        return null
    }

    fun renderBlock(entity: ModificationVariantBlockEntity, poseStack: PoseStack, buffers: MultiBufferSource, light: Int, overlay: Int) {
        val state = entity.blockState
        if (!ModificationVariantStructures.isModified(state)) {
            return
        }
        if (state.block !is ModificationVariantBlock) {
            return
        }
        val variant = entity.variant()
        val variantModel = variant?.let { bakedModel(it, it.modelFor(state)) }
        if (variantModel != null) {
            renderRotatedModel(variantModel, state, variant.isTranslucent, poseStack, buffers, light, overlay)
            return
        }
        val stockState = state.setValue(ModificationVariantBlock.MODIFIED, false)
        val stockModel = Minecraft.getInstance().blockRenderer.getBlockModel(stockState)
        val renderType = Sheets.cutoutBlockSheet()
        Minecraft.getInstance().blockRenderer.modelRenderer.renderModel(poseStack.last(), buffers.getBuffer(renderType), stockState, stockModel, 1F, 1F, 1F, light, overlay)
    }

    private fun renderRotatedModel(
        model: BakedModel,
        state: BlockState,
        isTranslucent: Boolean,
        poseStack: PoseStack,
        buffers: MultiBufferSource,
        light: Int,
        overlay: Int
    ) {
        val renderType = if (isTranslucent) Sheets.translucentCullBlockSheet() else Sheets.cutoutBlockSheet()
        poseStack.pushPose()
        poseStack.translate(0.5, 0.0, 0.5)
        poseStack.mulPose(Axis.YP.rotationDegrees(-ModificationVariantStructures.facingOf(state).toYRot()))
        poseStack.translate(-0.5, 0.0, -0.5)
        Minecraft.getInstance().blockRenderer.modelRenderer.renderModel(poseStack.last(), buffers.getBuffer(renderType), state, model, 1F, 1F, 1F, light, overlay)
        poseStack.popPose()
    }
}
