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
import com.cobblemon.mod.common.client.entity.NPCClientDelegate
import com.cobblemon.mod.common.client.entity.PokemonClientDelegate
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState
import com.cobblemon.mod.common.item.PokeBallItem
import com.cobblemon.mod.common.item.PokedexItem
import com.cobblemon.mod.common.item.WearableItem
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.item.ItemStackRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.component.DataComponents
import net.minecraft.util.LightCoordsUtil
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack

class HeldItemRenderer {
    private val minecraft: Minecraft = Minecraft.getInstance()

    fun renderOnModel(
        item: ItemStack,
        state: PosableState,
        poseStack: PoseStack,
        collector: SubmitNodeCollector,
        light: Int,
        outlineColor: Int = 0,
        frontLight: Boolean = false,
        entity: LivingEntity? = null
    ) {
        if (!item.`is`(CobblemonItemTags.HIDDEN_ITEMS)) {
            renderAtLocator(item, state, entity, poseStack, collector, light, outlineColor, frontLight)
        }

        for ((targetLocator, animationItem) in state.animationItems) {
            renderAtLocator(animationItem, state, entity, poseStack, collector, light, outlineColor, frontLight, targetLocator)
        }
    }

    private fun renderAtLocator(
        item: ItemStack,
        state: PosableState,
        entity: LivingEntity?,
        poseStack: PoseStack,
        collector: SubmitNodeCollector,
        light: Int,
        outlineColor: Int,
        frontLight: Boolean = false,
        targetLocator: String =
            if (item.`is`(WEARABLE_FACE_ITEMS) && state.locatorStates.containsKey(ITEM_FACE)) ITEM_FACE
            else if (item.`is`(WEARABLE_HAT_ITEMS) && state.locatorStates.containsKey(ITEM_HAT)) ITEM_HAT
            else ITEM
    ) {
        if (item.isEmpty || !state.locatorStates.containsKey(targetLocator)) {
            return
        }

        val displayContext =
            state.currentModel?.getLocatorDisplayContext(targetLocator)
                ?: if ((item.`is`(WEARABLE_FACE_ITEMS) && targetLocator == ITEM_FACE) || (item.`is`(WEARABLE_HAT_ITEMS) && targetLocator == ITEM_HAT)) {
                    ItemDisplayContext.HEAD
                } else {
                    ItemDisplayContext.FIXED
                }
        val renderItem = resolveRenderItem(item, displayContext, entity)
        val itemState = ItemStackRenderState()
        if (entity != null) {
            minecraft.itemModelResolver.updateForLiving(itemState, renderItem, displayContext, entity)
        } else {
            minecraft.itemModelResolver.updateForTopItem(itemState, renderItem, displayContext, minecraft.level, null, displayContext.ordinal)
        }

        if (itemState.isEmpty) {
            return
        }

        val isLeft = displayContext == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
        poseStack.pushPose()
        poseStack.mulPose(state.locatorStates[targetLocator]!!.matrix)
        when (displayContext) {
            ItemDisplayContext.FIXED -> {
                poseStack.setOriginAndScale(0f, .5f, 0f, .5f)
                poseStack.mulPose(Axis.XP.rotationDegrees(90.0f))
            }
            ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, ItemDisplayContext.THIRD_PERSON_LEFT_HAND -> {
                if (state is NPCClientDelegate) {
                    poseStack.mulPose(Axis.XP.rotationDegrees(-90.0f))
                    poseStack.translate(0.025f * if (isLeft) 1 else -1, 0.0f, 0.0f)
                } else if (state is PokemonClientDelegate || state is FloatingState) {
                    poseStack.mulPose(Axis.YP.rotationDegrees(-90.0f * if (isLeft) -1f else 1f))
                    poseStack.mulPose(Axis.ZP.rotationDegrees(90.0f * if (isLeft) -1f else 1f))
                    poseStack.translate(0.0f, 0.0f, -0.0625f)
                }
            }
            ItemDisplayContext.HEAD -> {
                if (state is NPCClientDelegate) {
                    if (item.`is`(WEARABLE_FACE_ITEMS)) {
                        poseStack.setOriginAndScale(0f, -2f, 7.25f, 0.62f)
                    } else if (item.`is`(WEARABLE_HAT_ITEMS)) {
                        poseStack.setOriginAndScale(0f, -10.25f, 0f, 0.62f)
                    }
                } else {
                    if (item.`is`(WEARABLE_FACE_ITEMS)) {
                        poseStack.setOriginAndScale(0f, 1f, 6.75f, 0.62f)
                    } else if (item.`is`(WEARABLE_HAT_ITEMS)) {
                        poseStack.setOriginAndScale(0f, -6.75f, 0f, 0.62f)
                    }
                }
            }
            else -> {}
        }

        itemState.submit(
            poseStack,
            collector,
            if (frontLight) LightCoordsUtil.pack(15, 15) else light,
            OverlayTexture.NO_OVERLAY,
            outlineColor
        )
        poseStack.popPose()
    }

    companion object {
        const val ITEM_FACE = "item_face"
        const val ITEM_HAT = "item_hat"
        const val ITEM = "item"

        private fun PoseStack.setOriginAndScale(x: Float, y: Float, z: Float, scale: Float = 1f) {
            this.translate(x * .0625 * scale, y * .0625 * scale, z * .0625 * scale)
            this.scale(scale, scale, scale)
        }
    }

    private fun resolveRenderItem(item: ItemStack, displayContext: ItemDisplayContext, entity: LivingEntity?): ItemStack {
        if (displayContext != ItemDisplayContext.HEAD) {
            when (val itemType = item.item) {
                is PokeBallItem -> {
                    return item.copy().apply {
                        set(DataComponents.ITEM_MODEL, itemType.pokeBall.model3d.withPath { path -> path.removePrefix("item/") })
                    }
                }
                is PokedexItem -> {
                    return item.copy().apply {
                        val isScanModel = entity is Player && entity.getUseItem() === item && entity.isUsingItem()
                        val canOpenScreen = entity != null && ((entity.offhandItem === item && entity.mainHandItem.item !is PokedexItem) || entity.mainHandItem === item)
                        val modelState = if (isScanModel) "scanning" else if (canOpenScreen) null else "off"
                        set(
                            DataComponents.ITEM_MODEL,
                            itemType.type.getItemModelPath(modelState).withPath { path -> path.removePrefix("item/") }
                        )
                    }
                }
            }
        }

        val wearableItem = item.item as? WearableItem ?: return item
        if (displayContext != ItemDisplayContext.HEAD) {
            return item
        }

        return item.copy().apply {
            set(DataComponents.ITEM_MODEL, wearableItem.getModel3d().withPath { path -> path.removePrefix("item/") })
        }
    }
}
