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
import com.cobblemon.mod.common.client.entity.PokemonClientDelegate
import com.cobblemon.mod.common.client.render.MatrixWrapper
import com.cobblemon.mod.common.client.render.models.blockbench.NullObjectParser
import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack

class HeldItemRenderer() {
    private val itemRenderer = Minecraft.getInstance().itemRenderer
    private var displayContext = ItemDisplayContext.FIXED
    private var scale = 1.0f

    companion object {
        const val ITEM_FACE = "item_face"
        const val ITEM_HAT = "item_hat"
        const val ITEM = "item"
    }

    fun render(
        entity: PokemonEntity?,
        model: PosableModel,
        item: ItemStack,
        locators: Map<String, MatrixWrapper>,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        light: Int,
        seed: Int
    ) {
        if (item.isEmpty) return
        displayContext = ItemDisplayContext.FIXED

        poseStack.pushPose()
        when {
            (locators.containsKey(ITEM_FACE) && item.`is`(WEARABLE_FACE_ITEMS)) -> {
                displayContext = model.getLocatorDisplayContext(ITEM_FACE) ?: ItemDisplayContext.HEAD
                poseStack.mulPose(locators[ITEM_FACE]!!.matrix)
                poseStack.translate(0f, 0f, .28f * scale)
                poseStack.scale(0.7f * scale, 0.7f * scale, 0.7f * scale)
            }
            (locators.containsKey("item_hat") && item.`is`(WEARABLE_HAT_ITEMS)) -> {
                displayContext = model.getLocatorDisplayContext(ITEM_HAT) ?: ItemDisplayContext.HEAD
                poseStack.mulPose(locators["item_hat"]!!.matrix)
                poseStack.translate(0f, -0.26f * scale, 0f)
                poseStack.scale(.68f * scale, .68f * scale, .68f * scale)
            }
            (locators.containsKey("item")) -> {
                updateModifiers("item",locators)
                poseStack.mulPose(locators["item"]!!.matrix)

                displayContext = model.getLocatorDisplayContext(ITEM) ?: ItemDisplayContext.FIXED

                when (displayContext) {
                    ItemDisplayContext.FIXED -> {
                        poseStack.translate(0f, 0.01666f * scale, 0f)
                        poseStack.scale(.5f * scale, .5f * scale, .5f * scale)
                        poseStack.mulPose(Axis.XP.rotationDegrees(90f))
                    }
                    ItemDisplayContext.THIRD_PERSON_RIGHT_HAND -> {
                        poseStack.translate(0.075f* scale, 0f * scale, -0.05f* scale)
                        poseStack.scale(scale, scale, scale)
                        poseStack.mulPose(Axis.XP.rotationDegrees(-90f))
                        poseStack.mulPose(Axis.YP.rotationDegrees(-90f))
                    }
                    else -> {
                        poseStack.scale(scale, scale, scale)
                    }
                }
            }
            else -> { // Don't render any item
                poseStack.popPose()
                return
            }
        }

        itemRenderer.renderStatic(entity, item, displayContext, false, poseStack, buffer, null, light, OverlayTexture.NO_OVERLAY, seed)
        poseStack.popPose()
    }

    fun renderOnModel(
        model: PosableModel,
        item: ItemStack,
        locators: Map<String, MatrixWrapper>,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        light: Int = LightTexture.pack(11, 7)
    ){
        if (!item.`is`(CobblemonItemTags.HIDDEN_ITEMS)) render(null, model, item, locators, poseStack, buffer, light, 0)
    }

    fun renderOnEntity(
        entity: PokemonEntity,
        delegate: PokemonClientDelegate,
        model: PosableModel,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        light: Int
    ) {
        val shownItem = entity.shownItem
        val locators: Map<String, MatrixWrapper> = delegate.locatorStates

        render(entity, model, shownItem, locators, poseStack, buffer, light, 0)
    }

    private fun updateModifiers(name: String, locators: Map<String, MatrixWrapper>) {
        locators.forEach { (locator: String, m: MatrixWrapper) ->
            val modifiers: Map<String, Float>
            if (locator.startsWith("_null_$name[")) {
                modifiers = NullObjectParser.parseNullObject(locator).modifiers
                if (modifiers.containsKey("scale")) scale = modifiers["scale"]!!
                if (modifiers.containsKey("mode")) displayContext = ItemDisplayContext.entries[((modifiers["mode"]!!).toInt()) % 9]
                return
            }
        }
    }
}