/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.block

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonBlocks
import com.cobblemon.mod.common.ModAPI
import com.cobblemon.mod.common.block.LecternBlock

import com.cobblemon.mod.common.block.entity.LecternBlockEntity
import com.cobblemon.mod.common.item.PokedexItem
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.blockentity.LecternRenderer
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState
import net.minecraft.client.renderer.blockentity.state.LecternRenderState
import net.minecraft.client.renderer.feature.ModelFeatureRenderer
import net.minecraft.client.renderer.state.CameraRenderState
import net.minecraft.core.Direction
import net.minecraft.world.item.*
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import org.spongepowered.asm.mixin.Unique

class LecternBlockEntityRenderer(private val ctx: BlockEntityRendererProvider.Context) : BlockEntityRenderer<LecternBlockEntity, CobblemonLecternRenderState> {

    @Unique
    private val MODEL_PATH = if (Cobblemon.implementation.modAPI == ModAPI.FABRIC) "fabric_resource" else "standalone"

    override fun submit(
        blockEntityRenderState: CobblemonLecternRenderState,
        poseStack: PoseStack,
        submitNodeCollector: SubmitNodeCollector,
        cameraRenderState: CameraRenderState
    ) {
        //TODO POKEDEX RENDERING
    }

    override fun createRenderState(): CobblemonLecternRenderState {
        return CobblemonLecternRenderState()
    }

    override fun extractRenderState(
        blockEntity: LecternBlockEntity,
        blockEntityRenderState: CobblemonLecternRenderState,
        f: Float,
        vec3: Vec3,
        crumblingOverlay: ModelFeatureRenderer.CrumblingOverlay?
    ) {
        super.extractRenderState(blockEntity, blockEntityRenderState, f, vec3, crumblingOverlay)
        //TODO EXPAND
    }
}

class CobblemonLecternRenderState : LecternRenderState() {

}