/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.item

import com.cobblemon.mod.common.api.tags.CobblemonItemTags
import com.cobblemon.mod.common.api.tags.CobblemonItemTags.WEARABLE_FACE_ITEMS
import com.cobblemon.mod.common.api.tags.CobblemonItemTags.WEARABLE_HAT_ITEMS
import com.cobblemon.mod.common.client.render.MatrixWrapper
import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.entity.ItemRenderer
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3

class HeldItemRenderer {
    private val itemRenderer: ItemRenderer = Minecraft.getInstance().itemRenderer
    private var displayContext: ItemDisplayContext = ItemDisplayContext.FIXED

    companion object {
        const val ITEM_FACE = "item_face"
        const val ITEM_HAT = "item_hat"
        const val ITEM = "item"
    }

    fun render(
        entity: LivingEntity?,
        model: PosableModel,
        item: ItemStack,
        locators: Map<String, MatrixWrapper>,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        light: Int,
        seed: Int,
        rotation: Vec3
    ) {
        if (item.isEmpty) return
        displayContext = ItemDisplayContext.FIXED

        poseStack.pushPose()
        when {
            //item_face locator
            (locators.containsKey(ITEM_FACE) && item.`is`(WEARABLE_FACE_ITEMS)) -> {
                displayContext = model.getLocatorDisplayContext(ITEM_FACE) ?: ItemDisplayContext.HEAD
                poseStack.mulPose(locators[ITEM_FACE]!!.matrix)
                poseStack.translate(0f, 0f, .28f)
                poseStack.scale(0.7f, 0.7f, 0.7f)
            }
            //item_hat locator
            (locators.containsKey(ITEM_HAT) && item.`is`(WEARABLE_HAT_ITEMS)) -> {
                displayContext = model.getLocatorDisplayContext(ITEM_HAT) ?: ItemDisplayContext.HEAD
                poseStack.mulPose(locators[ITEM_HAT]!!.matrix)
                poseStack.translate(0f, -0.26f, 0f)
                poseStack.scale(.68f, .68f, .68f)
            }
            //item locator
            (locators.containsKey(ITEM)) -> {
                displayContext = model.getLocatorDisplayContext(ITEM) ?: ItemDisplayContext.FIXED
                poseStack.mulPose(locators[ITEM]!!.matrix)
                applyContextTransformation(poseStack, rotation)
            }
            // Don't render any item
            else -> {
                poseStack.popPose()
                return
            }
        }

        itemRenderer.renderStatic(entity, item, displayContext, (displayContext==ItemDisplayContext.THIRD_PERSON_LEFT_HAND), poseStack, buffer, null, light, OverlayTexture.NO_OVERLAY, seed)
        poseStack.popPose()
    }

    private fun renderAnimationItem(
        model: PosableModel,
        state: PosableState,
        locators: Map<String, MatrixWrapper>,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        light: Int,
        entity: LivingEntity?,
        rotation: Vec3
    ) {
        state.itemRenderingLocations.forEach { (locatorName, item) ->
            poseStack.pushPose()
            if (locators.containsKey(locatorName)) {
                displayContext = model.getLocatorDisplayContext(locatorName)?: ItemDisplayContext.FIXED
                poseStack.mulPose(locators[locatorName]!!.matrix)
                applyContextTransformation(poseStack, rotation)
            }
            itemRenderer.renderStatic(entity, item, displayContext, (displayContext==ItemDisplayContext.THIRD_PERSON_LEFT_HAND), poseStack, buffer, null, light, OverlayTexture.NO_OVERLAY, 0)
            poseStack.popPose()
        }
    }

    fun renderOnModel(
        model: PosableModel,
        item: ItemStack,
        state: PosableState,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        rotation: Vec3 = Vec3(0.0,0.0,0.0),
        light: Int = LightTexture.pack(11, 7),
    ) {
        val locators: Map<String, MatrixWrapper> = state.locatorStates
        if (state.itemRenderingLocations.isNotEmpty()) {
            renderAnimationItem(model, state, locators, poseStack, buffer, light, null, rotation)
        }
        else if (!item.`is`(CobblemonItemTags.HIDDEN_ITEMS)) {
            render(null, model, item, locators, poseStack, buffer, light, 0, rotation)
        }
    }

    fun renderOnEntity(
        entity: LivingEntity,
        item: ItemStack,
        model: PosableModel,
        state: PosableState,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        light: Int,
        rotation: Vec3 = Vec3(0.0,0.0,0.0)
    ) {
        val locators: Map<String, MatrixWrapper> = state.locatorStates
        if (state.itemRenderingLocations.isNotEmpty()) {
            renderAnimationItem(model, state, locators, poseStack, buffer, light, entity, rotation)
        }
        else {
            render(entity, model, item, locators, poseStack, buffer, light, 0, rotation)
        }
    }

    private fun applyContextTransformation(poseStack: PoseStack, rotation: Vec3) {
        when (displayContext) {
            ItemDisplayContext.FIXED -> {
                poseStack.mulPose(Axis.XP.rotationDegrees(90.0f))
                poseStack.scale(.5f, .5f, .5f )
                poseStack.translate(0f, 0.01666f, 0f)
            }
            ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, ItemDisplayContext.THIRD_PERSON_LEFT_HAND -> {
                poseStack.mulPose(Axis.XP.rotationDegrees((rotation.x).toFloat()))
                poseStack.mulPose(Axis.YP.rotationDegrees((rotation.y).toFloat()))
                poseStack.mulPose(Axis.ZP.rotationDegrees((rotation.z).toFloat()))
                poseStack.translate(0.025f * if (displayContext==ItemDisplayContext.THIRD_PERSON_LEFT_HAND) 1 else -1 , 0.0f, 0.0f)
            }
            ItemDisplayContext.HEAD -> {
                poseStack.mulPose(Axis.XP.rotationDegrees(90.0f))
                poseStack.scale(.6f, .6f, .6f)
                poseStack.translate(0f, -0.6f, 0f)
            }
            else -> {}
        }
    }
}