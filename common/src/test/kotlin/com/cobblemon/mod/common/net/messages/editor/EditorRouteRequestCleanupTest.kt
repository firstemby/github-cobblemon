/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.editor

import com.cobblemon.mod.common.CobblemonNetwork
import com.cobblemon.mod.common.api.cinematic.runtime.pathfinding.NativeWaypointMotor
import com.cobblemon.mod.common.api.cinematic.runtime.pathfinding.WaypointPlayback
import com.cobblemon.mod.common.api.cinematic.runtime.pathfinding.WaypointRoutePreview
import com.cobblemon.mod.common.junit.BootstrapMinecraft
import com.cobblemon.mod.common.net.messages.client.editor.EditorRouteResultS2CPacket
import com.cobblemon.mod.common.net.serverhandling.editor.EditorRouteRequestHandler
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.Runs
import io.mockk.unmockkObject
import io.mockk.verify
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

@BootstrapMinecraft
class EditorRouteRequestCleanupTest {
    @Test
    fun `result and close failures release ownership and allow remaining routes to finish`() {
        val handler = EditorRouteRequestHandler
        val jobsField = handler.javaClass.getDeclaredField("jobs").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val jobs = jobsField.get(handler) as MutableMap<UUID, Any>
        val constructor = handler.javaClass.declaredClasses.single { it.simpleName == "Job" }
            .declaredConstructors.single { it.parameterCount == 6 }.apply { isAccessible = true }
        val server = mockk<MinecraftServer>(relaxed = true)
        val level = mockk<ServerLevel>()
        val players = List(2) { mockk<ServerPlayer>(relaxed = true) }
        val previews = List(2) { mockk<WaypointRoutePreview>(relaxed = true) }
        mockkObject(CobblemonNetwork)
        try {
            every { CobblemonNetwork.sendPacketToPlayer(any(), any()) } just Runs
            every { CobblemonNetwork.sendPacketToPlayer(players[0], any()) } throws IllegalStateException("send failed")
            players.forEachIndexed { index, player ->
                val id = UUID.randomUUID()
                every { player.uuid } returns id
                every { player.serverLevel() } returns level
                every { player.hasPermissions(2) } returns true
                every { player.hasDisconnected() } returns false
                val preview = previews[index]
                val playback = mockk<WaypointPlayback>(relaxed = true)
                every { preview.done } returns true
                every { preview.frames } returns mutableListOf()
                every { preview.playback } returns playback
                every { playback.error } returns null
                every { playback.finished } returns true
                jobs[id] = constructor.newInstance(UUID.randomUUID(), player, mockk<NativeWaypointMotor>(relaxed = true), preview, level, System.nanoTime())
            }
            every { previews[0].close() } throws IllegalStateException("close failed")
            handler.javaClass.getDeclaredMethod("tick", MinecraftServer::class.java).apply { isAccessible = true }.invoke(handler, server)
            assertTrue(jobs.isEmpty())
            verify(exactly = 1) { previews[0].close() }
            verify(exactly = 1) { previews[1].close() }
            verify(exactly = 1) { CobblemonNetwork.sendPacketToPlayer(players[1], any<EditorRouteResultS2CPacket>()) }
        } finally {
            jobs.clear()
            unmockkObject(CobblemonNetwork)
        }
    }
}
