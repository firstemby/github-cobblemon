/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.block

import com.cobblemon.mod.common.block.entity.ModificationVariantBlockEntity
import com.cobblemon.mod.common.client.render.modification.ModificationVariantModels
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider

class ModificationVariantRenderer<T : ModificationVariantBlockEntity>(context: BlockEntityRendererProvider.Context) : BlockEntityRenderer<T> {
    override fun render(entity: T, partialTick: Float, poseStack: PoseStack, buffers: MultiBufferSource, light: Int, overlay: Int) {
        ModificationVariantModels.renderBlock(entity, poseStack, buffers, light, overlay)
    }

    override fun shouldRenderOffScreen(entity: T): Boolean = true
}
