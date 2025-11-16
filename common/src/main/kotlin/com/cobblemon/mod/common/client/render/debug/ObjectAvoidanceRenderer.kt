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
import com.cobblemon.mod.common.platform.events.PlatformEvents
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.math.geometry.toRadians
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.world.phys.Vec3

object ObjectAvoidanceRenderer {

    // Lines to render attached to the uuid of entities.
    val lineStates = mutableMapOf<Int, LineState>()

    fun registerTickHandler() {
        // Advance line States
        PlatformEvents.CLIENT_TICK_PRE.subscribe {
            lineStates.forEach { uuid, lineState ->
                lineState.tick()
            }
        }
    }

    fun render(poseStack: PoseStack, multiBufferSource: MultiBufferSource, partialTicks: Float, camera: Camera) {
        val matrix = poseStack.last().pose()

        val level = Minecraft.getInstance().level ?: return
        lineStates.forEach { uuid, lineState ->
            lineState.draw(multiBufferSource.getBuffer(RenderType.debugLineStrip(6.0)), matrix, partialTicks, camera)
        }

        // Gather current tick to use in comparison when destroying no longer needed LineStates
        val currTick = level.gameTime
        val ticksUntilStale = 3

        val keysToRemove = lineStates.keys.filter { entityId ->
            val entity = level.getEntity(entityId) as? PokemonEntity
            val lastRecTick = lineStates[entityId]?.lastReceivedLineTick
            // Remove if the entity is null OR if it's no longer in the BOIDS activity.
            entity == null || (lastRecTick != null &&  (currTick - lastRecTick) > ticksUntilStale)
        }
        keysToRemove.forEach {
            lineStates.remove(it)
        }
    }

}
