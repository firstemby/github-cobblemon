/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.debug

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.platform.events.PlatformEvents
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType

object SteerForceRenderer {

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

        val currTick = level.gameTime
        val ticksUntilStale = 3

        val keysToRemove = lineStates.keys.filter { entityId ->
            val entity = level.getEntity(entityId) as? PokemonEntity
            val lastRecTick = lineStates[entityId]?.lastReceivedLineTick
            entity == null || (lastRecTick != null && (currTick - lastRecTick) > ticksUntilStale)
        }
        keysToRemove.forEach { lineStates.remove(it) }

        // One buffer for all independent line segments
        val lineBuffer = multiBufferSource.getBuffer(RenderType.lines())

        lineStates.forEach { entityId, lineState ->
            val entity = level.getEntity(entityId) as? PokemonEntity ?: return@forEach
            lineState.draw(lineBuffer, poseStack.last(), partialTicks, camera, entity, partialTicks)

        }
    }


}
