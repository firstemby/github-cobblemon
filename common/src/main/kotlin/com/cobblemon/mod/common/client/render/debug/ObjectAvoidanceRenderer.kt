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

    // Lines to render
    val lineStates = mutableMapOf<Int, LineState>()

    fun render(poseStack: PoseStack, multiBufferSource: MultiBufferSource, partialTicks: Float, camera: Camera) {
        val matrix = poseStack.last().pose()

        val level = Minecraft.getInstance().level ?: return
        lineStates.forEach { uuid, lineState ->
            lineState.draw(multiBufferSource.getBuffer(RenderType.debugLineStrip(6.0)), matrix, partialTicks, camera)
        }

        val keysToRemove = lineStates.keys.filter { entityId ->
            val entity = level.getEntity(entityId)
            // Remove if the entity is null OR if it's no longer in the BOIDS activity.
            entity == null
        }
        keysToRemove.forEach {
            lineStates.remove(it)
        }
    }

}
