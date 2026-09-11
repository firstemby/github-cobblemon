/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.modification

import com.cobblemon.mod.common.api.modification.ModificationVariant
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import net.minecraft.client.renderer.block.model.BakedQuad
import net.minecraft.client.renderer.block.model.ItemOverrides
import net.minecraft.client.renderer.block.model.ItemTransform
import net.minecraft.client.renderer.block.model.ItemTransforms
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.client.resources.model.BakedModel
import net.minecraft.core.Direction
import net.minecraft.util.RandomSource
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.level.block.state.BlockState
import org.joml.Vector3f

class ModificationVariantItemModel(private val delegate: BakedModel, variant: ModificationVariant) : BakedModel {
    private val cellExtent = cellExtent(variant)
    private val scale = 1F / cellExtent
    private val center: Vector3f
    private val quadsBySide = hashMapOf<Direction?, MutableList<BakedQuad>>()

    init {
        val min = variant.cellsMin()
        val max = variant.cellsMax()
        center = Vector3f((min.x + max.x + 1) / 2F, (min.y + max.y + 1) / 2F, (min.z + max.z + 1) / 2F)
    }

    override fun getQuads(state: BlockState?, side: Direction?, random: RandomSource): MutableList<BakedQuad> {
        if (cellExtent <= 1) {
            return delegate.getQuads(state, side, random)
        }
        return quadsBySide.getOrPut(side) { delegate.getQuads(state, side, random).map(::scaleQuad).toMutableList() }
    }

    override fun useAmbientOcclusion(): Boolean = delegate.useAmbientOcclusion()

    override fun isGui3d(): Boolean = true

    override fun usesBlockLight(): Boolean = true

    override fun isCustomRenderer(): Boolean = delegate.isCustomRenderer

    override fun getParticleIcon(): TextureAtlasSprite = delegate.particleIcon

    override fun getTransforms(): ItemTransforms {
        return if (delegate.transforms.hasTransform(ItemDisplayContext.GUI)) delegate.transforms else BLOCK_TRANSFORMS
    }

    override fun getOverrides(): ItemOverrides = delegate.overrides

    private fun scaleQuad(quad: BakedQuad): BakedQuad {
        val vertices = quad.vertices.copyOf()
        for (vertex in 0 until VERTICES_PER_QUAD) {
            val positionIndex = vertex * VERTEX_STRIDE
            vertices[positionIndex] = scaleCoordinate(vertices[positionIndex], center.x)
            vertices[positionIndex + 1] = scaleCoordinate(vertices[positionIndex + 1], center.y)
            vertices[positionIndex + 2] = scaleCoordinate(vertices[positionIndex + 2], center.z)
        }
        return BakedQuad(vertices, quad.tintIndex, quad.direction, quad.sprite, quad.isShade)
    }

    private fun scaleCoordinate(packed: Int, centerCoordinate: Float): Int {
        val coordinate = Float.fromBits(packed)
        return (0.5F + (coordinate - centerCoordinate) * scale).toBits()
    }

    companion object {
        private const val VERTICES_PER_QUAD = 4
        private val VERTEX_STRIDE = DefaultVertexFormat.BLOCK.vertexSize / Int.SIZE_BYTES
        private val BLOCK_TRANSFORMS = ItemTransforms(
            blockTransform(75F, 45F, 0F, 0F, 2.5F, 0F, 0.375F),
            blockTransform(75F, 45F, 0F, 0F, 2.5F, 0F, 0.375F),
            blockTransform(0F, 225F, 0F, 0F, 0F, 0F, 0.4F),
            blockTransform(0F, 45F, 0F, 0F, 0F, 0F, 0.4F),
            ItemTransform.NO_TRANSFORM,
            blockTransform(30F, 225F, 0F, 0F, 0F, 0F, 0.625F),
            blockTransform(0F, 0F, 0F, 0F, 3F, 0F, 0.25F),
            blockTransform(0F, 0F, 0F, 0F, 0F, 0F, 0.5F)
        )

        fun cellExtent(variant: ModificationVariant): Int {
            val size = variant.cellsMax().subtract(variant.cellsMin())
            return maxOf(size.x, size.y, size.z) + 1
        }

        private fun blockTransform(
            rotationX: Float,
            rotationY: Float,
            rotationZ: Float,
            translationX: Float,
            translationY: Float,
            translationZ: Float,
            scale: Float
        ): ItemTransform = ItemTransform(
            Vector3f(rotationX, rotationY, rotationZ),
            Vector3f(translationX / 16F, translationY / 16F, translationZ / 16F),
            Vector3f(scale, scale, scale)
        )
    }
}
