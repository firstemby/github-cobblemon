/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.particle

import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.systems.RenderPass
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.ByteBufferBuilder
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.MeshData
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.particle.SingleQuadParticle
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.feature.ParticleFeatureRenderer
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.renderer.state.level.ParticleGroupRenderState
import net.minecraft.client.renderer.state.level.QuadParticleRenderState
import net.minecraft.client.renderer.texture.TextureManager
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.joml.Vector4f

class SnowstormParticleRenderState : ParticleGroupRenderState, SubmitNodeCollector.ParticleGroupRenderer {
    private val particles = HashMap<SingleQuadParticle.Layer, Storage>()
    private var particleCount = 0

    fun add(
        layer: SingleQuadParticle.Layer,
        x: Float,
        y: Float,
        z: Float,
        qx: Float,
        qy: Float,
        qz: Float,
        qw: Float,
        xSize: Float,
        ySize: Float,
        minU: Float,
        maxU: Float,
        minV: Float,
        maxV: Float,
        color: Int,
        light: Int
    ) {
        particles.getOrPut(layer, ::Storage).add(
            x,
            y,
            z,
            qx,
            qy,
            qz,
            qw,
            xSize,
            ySize,
            minU,
            maxU,
            minV,
            maxV,
            color,
            light
        )
        particleCount++
    }

    override fun clear() {
        particles.values.forEach(Storage::clear)
        particleCount = 0
    }

    override fun isEmpty(): Boolean {
        return particleCount == 0
    }

    override fun prepare(cache: ParticleFeatureRenderer.ParticleBufferCache, translucent: Boolean): QuadParticleRenderState.PreparedBuffers? {
        val vertexCount = particleCount * 4
        ByteBufferBuilder.exactlySized(vertexCount * DefaultVertexFormat.PARTICLE.getVertexSize()).use { byteBufferBuilder ->
            val bufferBuilder = BufferBuilder(byteBufferBuilder, VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE)
            val layers = HashMap<SingleQuadParticle.Layer, QuadParticleRenderState.PreparedLayer>()
            var vertexOffset = 0

            for ((layer, storage) in particles) {
                if (layer.translucent() == translucent) {
                    storage.forEachParticle { x, y, z, qx, qy, qz, qw, xSize, ySize, minU, maxU, minV, maxV, color, light ->
                        renderRotatedQuad(bufferBuilder, x, y, z, qx, qy, qz, qw, xSize, ySize, minU, maxU, minV, maxV, color, light)
                    }
                    if (storage.count() > 0) {
                        layers[layer] = QuadParticleRenderState.PreparedLayer(vertexOffset, storage.count() * 6)
                    }
                    vertexOffset += storage.count() * 4
                }
            }

            val meshData: MeshData = bufferBuilder.build() ?: return null
            cache.write(meshData.vertexBuffer())
            RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS).getBuffer(meshData.drawState().indexCount())
            val dynamicTransforms: GpuBufferSlice = RenderSystem.getDynamicUniforms()
                .writeTransform(RenderSystem.getModelViewMatrix(), Vector4f(1F, 1F, 1F, 1F), Vector3f(), Matrix4f())
            return QuadParticleRenderState.PreparedBuffers(meshData.drawState().indexCount(), dynamicTransforms, layers)
        }
    }

    override fun render(
        preparedBuffers: QuadParticleRenderState.PreparedBuffers,
        cache: ParticleFeatureRenderer.ParticleBufferCache,
        renderPass: RenderPass,
        textureManager: TextureManager
    ) {
        val indexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS)
        renderPass.setVertexBuffer(0, cache.get())
        renderPass.setIndexBuffer(indexBuffer.getBuffer(preparedBuffers.indexCount()), indexBuffer.type())
        renderPass.setUniform("DynamicTransforms", preparedBuffers.dynamicTransforms())

        for ((layer, preparedLayer) in preparedBuffers.layers()) {
            renderPass.setPipeline(layer.pipeline())
            val texture = textureManager.getTexture(layer.textureAtlasLocation())
            renderPass.bindTexture("Sampler0", texture.textureView, texture.sampler)
            renderPass.drawIndexed(preparedLayer.vertexOffset(), 0, preparedLayer.indexCount(), 1)
        }
    }

    override fun submit(collector: SubmitNodeCollector, cameraRenderState: CameraRenderState) {
        if (particleCount > 0) {
            collector.submitParticleGroup(this)
        }
    }

    private fun renderRotatedQuad(
        consumer: VertexConsumer,
        x: Float,
        y: Float,
        z: Float,
        qx: Float,
        qy: Float,
        qz: Float,
        qw: Float,
        xSize: Float,
        ySize: Float,
        minU: Float,
        maxU: Float,
        minV: Float,
        maxV: Float,
        color: Int,
        light: Int
    ) {
        val quaternion = Quaternionf(qx, qy, qz, qw)
        renderVertex(consumer, quaternion, x, y, z, 1F, -1F, xSize, ySize, maxU, maxV, color, light)
        renderVertex(consumer, quaternion, x, y, z, 1F, 1F, xSize, ySize, maxU, minV, color, light)
        renderVertex(consumer, quaternion, x, y, z, -1F, 1F, xSize, ySize, minU, minV, color, light)
        renderVertex(consumer, quaternion, x, y, z, -1F, -1F, xSize, ySize, minU, maxV, color, light)
    }

    private fun renderVertex(
        consumer: VertexConsumer,
        quaternion: Quaternionf,
        x: Float,
        y: Float,
        z: Float,
        xDirection: Float,
        yDirection: Float,
        xSize: Float,
        ySize: Float,
        u: Float,
        v: Float,
        color: Int,
        light: Int
    ) {
        val vertex = Vector3f(xDirection * xSize, yDirection * ySize, 0F).rotate(quaternion).add(x, y, z)
        consumer.addVertex(vertex.x(), vertex.y(), vertex.z()).setUv(u, v).setColor(color).setLight(light)
    }

    private fun interface ParticleConsumer {
        fun consume(
            x: Float,
            y: Float,
            z: Float,
            qx: Float,
            qy: Float,
            qz: Float,
            qw: Float,
            xSize: Float,
            ySize: Float,
            minU: Float,
            maxU: Float,
            minV: Float,
            maxV: Float,
            color: Int,
            light: Int
        )
    }

    private class Storage {
        private var capacity = 1024
        private var floatValues = FloatArray(capacity * 13)
        private var intValues = IntArray(capacity * 2)
        private var currentParticleIndex = 0

        fun add(
            x: Float,
            y: Float,
            z: Float,
            qx: Float,
            qy: Float,
            qz: Float,
            qw: Float,
            xSize: Float,
            ySize: Float,
            minU: Float,
            maxU: Float,
            minV: Float,
            maxV: Float,
            color: Int,
            light: Int
        ) {
            if (currentParticleIndex >= capacity) {
                grow()
            }

            var floatIndex = currentParticleIndex * 13
            floatValues[floatIndex++] = x
            floatValues[floatIndex++] = y
            floatValues[floatIndex++] = z
            floatValues[floatIndex++] = qx
            floatValues[floatIndex++] = qy
            floatValues[floatIndex++] = qz
            floatValues[floatIndex++] = qw
            floatValues[floatIndex++] = xSize
            floatValues[floatIndex++] = ySize
            floatValues[floatIndex++] = minU
            floatValues[floatIndex++] = maxU
            floatValues[floatIndex++] = minV
            floatValues[floatIndex] = maxV

            val intIndex = currentParticleIndex * 2
            intValues[intIndex] = color
            intValues[intIndex + 1] = light
            currentParticleIndex++
        }

        fun count(): Int = currentParticleIndex

        fun clear() {
            currentParticleIndex = 0
        }

        fun forEachParticle(consumer: ParticleConsumer) {
            for (particleIndex in 0 until currentParticleIndex) {
                var floatIndex = particleIndex * 13
                val x = floatValues[floatIndex++]
                val y = floatValues[floatIndex++]
                val z = floatValues[floatIndex++]
                val qx = floatValues[floatIndex++]
                val qy = floatValues[floatIndex++]
                val qz = floatValues[floatIndex++]
                val qw = floatValues[floatIndex++]
                val xSize = floatValues[floatIndex++]
                val ySize = floatValues[floatIndex++]
                val minU = floatValues[floatIndex++]
                val maxU = floatValues[floatIndex++]
                val minV = floatValues[floatIndex++]
                val maxV = floatValues[floatIndex]

                val intIndex = particleIndex * 2
                consumer.consume(x, y, z, qx, qy, qz, qw, xSize, ySize, minU, maxU, minV, maxV, intValues[intIndex], intValues[intIndex + 1])
            }
        }

        private fun grow() {
            capacity *= 2
            floatValues = floatValues.copyOf(capacity * 13)
            intValues = intValues.copyOf(capacity * 2)
        }
    }
}
