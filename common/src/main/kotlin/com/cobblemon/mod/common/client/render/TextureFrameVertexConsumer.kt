/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render

import com.mojang.blaze3d.vertex.VertexConsumer

internal class TextureFrameVertexConsumer(
    private val delegate: VertexConsumer,
    private val frame: Int,
    private val frameCount: Int
) : VertexConsumer {
    init {
        require(frameCount > 0 && frame in 0 until frameCount)
    }

    override fun addVertex(x: Float, y: Float, z: Float): VertexConsumer = apply {
        delegate.addVertex(x, y, z)
    }

    override fun setColor(red: Int, green: Int, blue: Int, alpha: Int): VertexConsumer = apply {
        delegate.setColor(red, green, blue, alpha)
    }

    override fun setUv(u: Float, v: Float): VertexConsumer = apply {
        delegate.setUv(u, (v + frame) / frameCount)
    }

    override fun setUv1(u: Int, v: Int): VertexConsumer = apply {
        delegate.setUv1(u, v)
    }

    override fun setUv2(u: Int, v: Int): VertexConsumer = apply {
        delegate.setUv2(u, v)
    }

    override fun setNormal(x: Float, y: Float, z: Float): VertexConsumer = apply {
        delegate.setNormal(x, y, z)
    }
}
