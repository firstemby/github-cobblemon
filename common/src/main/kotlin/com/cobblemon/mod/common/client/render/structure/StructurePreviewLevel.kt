/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.structure

import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.BlockAndTintGetter
import net.minecraft.world.level.ColorResolver
import net.minecraft.world.level.LightLayer
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.lighting.LevelLightEngine
import net.minecraft.world.level.material.FluidState

/**
 * A minimal in-memory [BlockAndTintGetter] holding a structure's blocks so the game's own chunk-block
 * and fluid renderers can draw the preview: neighbor lookups drive face culling, full-bright light is
 * reported, and a fixed biome supplies grass/water/foliage tints. Positions outside the structure read
 * as air, so the structure's outer faces stay exposed.
 */
class StructurePreviewLevel(
    private val states: Map<BlockPos, BlockState>,
    private val biome: Biome?
) : BlockAndTintGetter {

    override fun getBlockState(pos: BlockPos): BlockState = states[pos] ?: Blocks.AIR.defaultBlockState()

    override fun getFluidState(pos: BlockPos): FluidState = getBlockState(pos).fluidState

    override fun getBlockEntity(pos: BlockPos): BlockEntity? = null

    // Full-bright everywhere (overriding these means the light engine is never queried for rendering).
    override fun getBrightness(lightLayer: LightLayer, pos: BlockPos): Int = 15
    override fun getRawBrightness(pos: BlockPos, ambientDarkening: Int): Int = 15

    // Standard overworld face shading for a clear 3D look.
    override fun getShade(direction: Direction, shade: Boolean): Float = when (direction) {
        Direction.DOWN -> 0.5f
        Direction.UP -> 1.0f
        Direction.NORTH, Direction.SOUTH -> 0.8f
        else -> 0.6f
    }

    override fun getBlockTint(pos: BlockPos, colorResolver: ColorResolver): Int {
        val resolvedBiome = biome ?: return -1 // white = no tint
        return colorResolver.getColor(resolvedBiome, pos.x.toDouble(), pos.z.toDouble())
    }

    // Not used for rendering because getBrightness/getRawBrightness are overridden; provide the real
    // client engine as a non-null fallback so nothing NPEs if it is ever touched.
    override fun getLightEngine(): LevelLightEngine = Minecraft.getInstance().level!!.lightEngine

    override fun getHeight(): Int = 384
    override fun getMinBuildHeight(): Int = -64
}
