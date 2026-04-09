/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.debug

import com.cobblemon.mod.common.api.net.Encodable
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3

/**
 * Data class currently used for storing line data for rendering in [SteerForceRenderer]
 *
 * @author Jackson
 * @since September 18th, 2025
 */
data class EntityDebugLine(
    val vector: Vec3,   // Direction/magnitude in world space, relative to entity origin
    val r: Float,
    val g: Float,
    val b: Float,
    val a: Float,
) : Encodable {
    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeVec3(vector)
        buffer.writeFloat(r)
        buffer.writeFloat(g)
        buffer.writeFloat(b)
        buffer.writeFloat(a)
    }

    companion object {
        operator fun invoke(vector: Vec3, r: Float = 1f, g: Float = 1f, b: Float = 1f, a: Float = 1f): EntityDebugLine {
            return EntityDebugLine(vector, r.coerceIn(0f,1f), g.coerceIn(0f,1f), b.coerceIn(0f,1f), a.coerceIn(0f,1f))
        }

        fun decode(buffer: RegistryFriendlyByteBuf): EntityDebugLine {
            val vector = buffer.readVec3()
            return EntityDebugLine(vector, buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat())
        }
    }
}

class LineState {
    /**
     * The comparison of the previous and current lines relies on the order being consistent
     */
    var previousLines: List<EntityDebugLine> = emptyList()
    var currentLines: List<EntityDebugLine> = emptyList()
    var lastReceivedLineTick: Long? = null

    fun tick() {
        previousLines = currentLines
    }

    fun update(newLines: List<EntityDebugLine>) {
        lastReceivedLineTick = Minecraft.getInstance().level?.gameTime
        currentLines = newLines
        if (previousLines.isEmpty()) previousLines = newLines
    }

    // entityPos: interpolated world position of the entity this tick
    fun draw(vc: VertexConsumer, pose: PoseStack.Pose, partialTick: Float, camera: Camera, entity: PokemonEntity, partialTicks: Float) {
        previousLines.zip(currentLines).forEach { (prev, curr) ->
            val iVec = prev.vector.lerp(curr.vector, partialTick.toDouble())
            val iR = prev.r.lerp(curr.r, partialTick)
            val iG = prev.g.lerp(curr.g, partialTick)
            val iB = prev.b.lerp(curr.b, partialTick)
            val iA = prev.a.lerp(curr.a, partialTick)

            // Interpolated world position — same method used everywhere in the renderer
            val worldStart = Vec3(
                Mth.lerp(partialTicks.toDouble(), entity.xOld, entity.x),
                Mth.lerp(partialTicks.toDouble(), entity.yOld, entity.y),
                Mth.lerp(partialTicks.toDouble(), entity.zOld, entity.z)
            )
            val worldEnd = worldStart.add(iVec)

            val relStart = worldStart.subtract(camera.position)
            val relEnd = worldEnd.subtract(camera.position)

            // Normal is the normalized direction of the segment
            val dir = iVec.normalize()
            val nx = dir.x.toFloat()
            val ny = dir.y.toFloat()
            val nz = dir.z.toFloat()

            vc.addVertex(pose.pose(), relStart.x.toFloat(), relStart.y.toFloat(), relStart.z.toFloat())
                .setColor(iR, iG, iB, iA)
                .setNormal(pose, nx, ny, nz)

            vc.addVertex(pose.pose(), relEnd.x.toFloat(), relEnd.y.toFloat(), relEnd.z.toFloat())
                .setColor(iR, iG, iB, iA)
                .setNormal(pose, nx, ny, nz)
        }
    }


    fun reset() {
        previousLines = emptyList()
        currentLines = emptyList()
    }
}

private fun Float.lerp(end: Float, t: Float): Float = this + (end - this) * t.coerceIn(0f, 1f)

