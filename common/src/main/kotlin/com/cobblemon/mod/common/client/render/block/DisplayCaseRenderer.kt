/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.block

import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.api.tags.CobblemonItemTags
import com.cobblemon.mod.common.block.DisplayCaseBlock
import com.cobblemon.mod.common.block.entity.DisplayCaseBlockEntity
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState
import net.minecraft.client.renderer.feature.ModelFeatureRenderer
import net.minecraft.client.renderer.item.ItemModelResolver
import net.minecraft.client.renderer.item.ItemStackRenderState
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.BannerItem
import net.minecraft.world.item.BedItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomModelData
import net.minecraft.world.phys.Vec3
import com.cobblemon.mod.common.item.PokedexItem
import com.cobblemon.mod.common.item.PokeBallItem
import net.minecraft.world.level.Level

class DisplayCaseRenderer(ctx: BlockEntityRendererProvider.Context) : BlockEntityRenderer<DisplayCaseBlockEntity, BlockEntityRenderState> {
    private val itemModelResolver: ItemModelResolver = ctx.itemModelResolver()
    private val mobHeads = listOf<Item>(
        Items.SKELETON_SKULL,
        Items.WITHER_SKELETON_SKULL,
        Items.ZOMBIE_HEAD,
        Items.PIGLIN_HEAD,
        Items.PLAYER_HEAD,
        Items.DRAGON_HEAD,
        Items.CREEPER_HEAD
    )
    private val coinPouchStack: ItemStack by lazy {
        ItemStack(CobblemonItems.RELIC_COIN_POUCH).also {
            it.set(DataComponents.CUSTOM_MODEL_DATA, CustomModelData(listOf(1F), emptyList(), emptyList(), emptyList()))
        }
    }

    override fun createRenderState(): BlockEntityRenderState = DisplayCaseRenderState()

    override fun extractRenderState(
        blockEntity: DisplayCaseBlockEntity,
        renderState: BlockEntityRenderState,
        tickDelta: Float,
        cameraPos: Vec3,
        crumblingOverlay: ModelFeatureRenderer.CrumblingOverlay?
    ) {
        BlockEntityRenderState.extractBase(blockEntity, renderState, crumblingOverlay)
        if (renderState !is DisplayCaseRenderState) {
            return
        }

        renderState.itemState.clear()
        val level = blockEntity.level ?: return
        val stack = if (blockEntity.getStack().`is`(CobblemonItems.RELIC_COIN_POUCH)) {
            coinPouchStack.copy()
        } else {
            blockEntity.getStack().copy()
        }
        if (stack.isEmpty) {
            return
        }

        val pokeBall = stack.item as? PokeBallItem
        if (pokeBall != null) {
            stack.set(DataComponents.ITEM_MODEL, pokeBall.pokeBall.model3d.withPath { path -> path.removePrefix("item/") })
        }

        renderState.positioningType = getPositioningType(stack, level)
        renderState.yRot = if (renderState.positioningType == PositioningType.ITEM_MODEL) {
            blockEntity.blockState.getValue(DisplayCaseBlock.ITEM_DIRECTION).opposite.toYRot()
        } else {
            blockEntity.blockState.getValue(DisplayCaseBlock.ITEM_DIRECTION).toYRot()
        }
        itemModelResolver.updateForTopItem(renderState.itemState, stack, ItemDisplayContext.GROUND, level, null, 0)
    }

    override fun submit(
        renderState: BlockEntityRenderState,
        poseStack: PoseStack,
        submitNodeCollector: SubmitNodeCollector,
        cameraRenderState: CameraRenderState
    ) {
        if (renderState !is DisplayCaseRenderState || renderState.itemState.isEmpty) {
            return
        }

        val posType = renderState.positioningType
        poseStack.pushPose()
        poseStack.translate(0.5F, 0.4F, 0.5F)
        poseStack.scale(posType.scaleX, posType.scaleY, posType.scaleZ)
        poseStack.translate(posType.transX, posType.transY, posType.transZ)
        poseStack.mulPose(Axis.YP.rotationDegrees(-renderState.yRot))
        poseStack.mulPose(Axis.YP.rotationDegrees(posType.rotY))
        renderState.itemState.submit(
            poseStack,
            submitNodeCollector,
            renderState.lightCoords,
            OverlayTexture.NO_OVERLAY,
            0
        )
        poseStack.popPose()
    }

    private fun getPositioningType(stack: ItemStack, world: Level): PositioningType {
        if (mobHeads.contains(stack.item)) {
            return PositioningType.MOB_HEAD
        }
        if (stack.item is BedItem) {
            return PositioningType.BED
        }
        if (stack.item is BannerItem) {
            return PositioningType.BANNER
        }
        if (stack.`is`(CobblemonItemTags.POKE_BALLS)) {
            return PositioningType.POKE_BALL
        }
        if (stack.`is`(CobblemonItemTags.PLAQUES)) {
            return PositioningType.ITEM_MODEL
        }
        if (stack.item is PokedexItem) {
            return PositioningType.POKEDEX
        }
        if (stack.item == CobblemonItems.RELIC_COIN_POUCH) {
            return PositioningType.COIN_POUCH
        }
        if (stack.item == CobblemonItems.PASTURE) {
            return PositioningType.PASTURE
        }
        if (stack.item == Items.SHIELD) {
            return PositioningType.SHIELD
        }
        if (stack.item == Items.DECORATED_POT) {
            return PositioningType.MOB_HEAD
        }

        val previewState = ItemStackRenderState()
        itemModelResolver.updateForTopItem(previewState, stack, ItemDisplayContext.GROUND, world, null, 0)
        return if (previewState.usesBlockLight()) PositioningType.BLOCK_MODEL else PositioningType.ITEM_MODEL
    }

    enum class PositioningType(
        val scaleX: Float,
        val scaleY: Float,
        val scaleZ: Float,
        val transX: Float,
        val transY: Float,
        val transZ: Float,
        val rotY: Float = 0F
    ) {
        POKE_BALL(1F, 1F, 1F, 0F, 0.04F, 0F),
        POKEDEX(1F, 1F, 1F, 0F, 0.04F, 0F, 180F),
        BLOCK_MODEL(1F, 1F, 1F, 0F, -0.15F, 0F),
        ITEM_MODEL(1F, 1F, 1F, 0F, 0.04F, 0F),
        BED(1F, 1F, 1F, 0F, -0.02F, 0F),
        BANNER(1F, 1F, 1F, 0F, -0.02F, 0F, 180F),
        MOB_HEAD(1F, 1F, 1F, 0F, -0.025F, 0F, 180F),
        SHIELD(1F, 1F, 1F, 0F, -0.045F, 0F, 180F),
        PASTURE(1F, 1F, 1F, 0F, 0.0375F, 0F),
        COIN_POUCH(1F, 1F, 1F, 0F, 0.415F, 0F)
    }
}

private class DisplayCaseRenderState : BlockEntityRenderState() {
    val itemState = ItemStackRenderState()
    var positioningType = DisplayCaseRenderer.PositioningType.ITEM_MODEL
    var yRot = 0F
}
