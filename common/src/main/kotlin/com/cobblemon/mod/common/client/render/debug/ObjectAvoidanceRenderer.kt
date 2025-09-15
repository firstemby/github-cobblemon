/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.debug

import com.cobblemon.mod.common.CobblemonActivities
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.math.geometry.toRadians
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.world.phys.Vec3

object ObjectAvoidanceRenderer {

    // Lines to render per
    public val avoidanceLines = mutableMapOf<Int, List<Pair<Vec3, Vec3>>>()

    fun render(poseStack: PoseStack, multiBufferSource: MultiBufferSource, camera: Camera) {

        val matrix = poseStack.last().pose()
        val vertexConsumer = multiBufferSource.getBuffer(RenderType.debugLineStrip(1.0))

        val level = Minecraft.getInstance().level ?: return
        avoidanceLines.forEach { uuid, lines ->
            lines.forEach { linePair ->
                val relativeStart = linePair.first.subtract(camera.position)
                val relativeEnd = linePair.second.subtract(camera.position)
                vertexConsumer.addVertex(matrix, relativeStart.x.toFloat(), relativeStart.y.toFloat(), relativeStart.z.toFloat()).setColor(1f, 0f, 0f, 1f)
                vertexConsumer.addVertex(matrix, relativeEnd.x.toFloat(), relativeEnd.y.toFloat(), relativeEnd.z.toFloat()).setColor(1f, 0f, 0f, 1f)
            }
        }


        val keysToRemove = avoidanceLines.keys.filter { entityId ->
            val entity = level.getEntity(entityId)
            // Remove if the entity is null OR if it's no longer in the BOIDS activity.
            entity == null || (entity as? PokemonEntity)?.brain?.activeNonCoreActivity != CobblemonActivities.POKEMON_BOIDS
        }

        // --- STEP 3: Remove the stale entries ---
        // Now that we are done iterating, we can safely modify the map.
        keysToRemove.forEach {
            avoidanceLines.remove(it)
        }


        // Define a line that floats 1.5 to 2.5 blocks above the camera
        val startPoint = camera.position.add(0.0, 1.5, -2.0)
        val endPoint = camera.position.add(0.0, 2.5, -2.0)

        // Make the coordinates relative to the camera for rendering
        val relativeStart = startPoint.subtract(camera.position)
        val relativeEnd = endPoint.subtract(camera.position)

        // Add the two vertices of the line to the buffer, making it red
        matrix.rotateY(camera.yRot.toRadians())
//        vertexConsumer.addVertex(matrix, relativeStart.x.toFloat(), relativeStart.y.toFloat(), relativeStart.z.toFloat()).setColor(1f, 0f, 0f, 1f)
//        vertexConsumer.addVertex(matrix, relativeEnd.x.toFloat(), relativeEnd.y.toFloat(), relativeEnd.z.toFloat()).setColor(1f, 0f, 0f, 1f)
    }

}
