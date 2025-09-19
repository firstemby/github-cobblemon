/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.debug

import com.cobblemon.mod.common.api.net.Encodable
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.Camera
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f

/**
 * Data class currently used for storing line data for rendering in [ObjectAvoidanceRenderer]
 *
 * @author Jackson
 * @since September 18th, 2025
 */
data class EntityDebugLine(
    val startPoint: Vec3,
    val endPoint: Vec3,
    val r: Float,
    val g: Float,
    val b: Float,
    val a: Float,
): Encodable {
    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeVec3(startPoint)
        buffer.writeVec3(endPoint)
        buffer.writeFloat(r)
        buffer.writeFloat(g)
        buffer.writeFloat(b)
        buffer.writeFloat(a)
    }

    companion object {
        // Clamp color values to avoid any issues
        operator fun invoke( startPoint: Vec3, endPoint: Vec3, r: Float= 1.0F, g: Float= 1.0F, b: Float= 1.0F, a: Float= 1.0F): EntityDebugLine {
            return EntityDebugLine(
                startPoint,
                endPoint,
                r.coerceIn(0f, 1f),
                g.coerceIn(0f, 1f),
                b.coerceIn(0f, 1f),
                a.coerceIn(0f, 1f)
            )
        }

        fun decode(buffer: RegistryFriendlyByteBuf): EntityDebugLine {
            val startPoint = buffer.readVec3()
            val endpoint = buffer.readVec3()
            val r = buffer.readFloat()
            val g = buffer.readFloat()
            val b = buffer.readFloat()
            val a = buffer.readFloat()

            return EntityDebugLine(
                startPoint,
                endpoint,
                r,
                g,
                b,
                a
            )
        }
    }
}

class LineState {
    /**
     * The comparison of the previous and current lines relies on the order being consistent
     */
    var previousLines: List<EntityDebugLine> = emptyList()
    var currentLines: List<EntityDebugLine> = emptyList()

    /**
     * Called when a new packet with line data arrives.
     */
    fun update(newLines: List<EntityDebugLine>) {
        // The old "current" becomes the new "previous"
        this.previousLines = this.currentLines
        this.currentLines = newLines

        // Handle the very first update: set both to the same value
        // to prevent snapping from an empty state.
        if (this.previousLines.isEmpty()) {
            this.previousLines = newLines
        }
    }

    /**
     * Zip both previous and current to be able to iterate through them
     * each as a List of pairs. Then interpolate between the previous
     * and current values in those pairs and throw the interpolated line
     * into the rendering dumpster fire.
     */
    fun draw(vc: VertexConsumer, matrix: Matrix4f, partialTick: Float, camera: Camera) {
        previousLines.zip(currentLines).forEach { (prev, curr) ->
            val iStart = prev.startPoint.lerp(curr.startPoint, partialTick.toDouble())
            val iEnd = prev.endPoint.lerp(curr.endPoint, partialTick.toDouble())

            val iRed = prev.r.lerp(curr.r, partialTick)
            val iGreen = prev.g.lerp(curr.g, partialTick)
            val iBlue = prev.b.lerp(curr.b, partialTick)
            val iAlpha = prev.a.lerp(curr.a, partialTick)

            // Make the coordinates relative to the camera before drawing
            val relativeStart = iStart.subtract(camera.position)
            val relativeEnd = iEnd.subtract(camera.position)

            vc.addVertex(matrix, relativeStart.x.toFloat(), relativeStart.y.toFloat(), relativeStart.z.toFloat())
                .setColor(iRed, iGreen, iBlue, iAlpha)
            vc.addVertex(matrix, relativeEnd.x.toFloat(), relativeEnd.y.toFloat(), relativeEnd.z.toFloat())
                .setColor(iRed, iGreen, iBlue, iAlpha)
        }

    }

    fun reset() {
        previousLines = emptyList()
        currentLines = emptyList()
    }
}

// I didn't want to write this 4 times...
private fun Float.lerp(end: Float, partialTick: Float): Float { return this + (end - this) * partialTick.coerceIn(0f, 1f) }
