/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.runtime

import com.cobblemon.mod.common.api.cinematic.model.Beat
import com.cobblemon.mod.common.api.cinematic.model.Scene
import com.cobblemon.mod.common.api.cinematic.runtime.pathfinding.WaypointActorMotion
import com.cobblemon.mod.common.junit.BootstrapMinecraft
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.minecraft.world.entity.Entity
import org.junit.jupiter.api.Test

@BootstrapMinecraft
class ActorDirectorCleanupTest {
    @Test
    fun `one failed restore or entity removal cannot retain the other actors`() {
        val director = director()
        val brokenMotion = mockk<WaypointActorMotion>()
        every { brokenMotion.restore() } throws IllegalStateException("restore failed")
        val healthyMotion = mockk<WaypointActorMotion>(relaxed = true)
        val brokenEntity = mockk<Entity>(relaxed = true)
        every { brokenEntity.remove(Entity.RemovalReason.DISCARDED) } throws IllegalStateException("remove failed")
        val healthyEntity = mockk<Entity>(relaxed = true)
        putState(director, "waypointMotions", "broken" to brokenMotion, "healthy" to healthyMotion)
        putState(director, "spawnedEntities", "broken" to brokenEntity, "healthy" to healthyEntity)
        director.dispose()
        director.dispose()
        verify(exactly = 1) { brokenMotion.restore() }
        verify(exactly = 1) { healthyMotion.restore() }
        verify(exactly = 1) { brokenEntity.remove(Entity.RemovalReason.DISCARDED) }
        verify(exactly = 1) { healthyEntity.remove(Entity.RemovalReason.DISCARDED) }
    }

    @Test
    fun `preview cleanup and viewer departure reach every actor`() {
        val director = director(preview = true)
        val broken = mockk<PreviewActor>(relaxed = true)
        every { broken.despawn() } throws IllegalStateException("despawn failed")
        val healthy = mockk<PreviewActor>(relaxed = true)
        putState(director, "previewActors", "broken" to broken, "healthy" to healthy)
        director.updateSynthActorViewerScope(emptySet())
        verify { broken.retainViewers(emptySet()) }
        verify { healthy.retainViewers(emptySet()) }
        director.dispose()
        director.dispose()
        verify(exactly = 1) { broken.despawn() }
        verify(exactly = 1) { healthy.despawn() }
    }

    private fun director(preview: Boolean = false): ActorDirector {
        val beat = Beat("main")
        return ActorDirector(Scene("cleanup", startBeatId = beat.id, beats = mapOf(beat.id to beat)), emptyMap(), previewMode = preview)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> putState(director: ActorDirector, name: String, vararg entries: Pair<String, T>) {
        val field = ActorDirector::class.java.getDeclaredField(name).apply { isAccessible = true }
        (field.get(director) as MutableMap<String, T>).putAll(entries)
    }
}
