package com.cobblemon.mod.common.api.cinematic.runtime

import com.cobblemon.mod.common.api.cinematic.model.ActorDeclaration
import com.cobblemon.mod.common.api.cinematic.model.ActorDirection
import com.cobblemon.mod.common.api.cinematic.model.ActorKind
import com.cobblemon.mod.common.api.cinematic.model.Beat
import com.cobblemon.mod.common.api.cinematic.model.Scene
import com.cobblemon.mod.common.junit.BootstrapMinecraft
import com.cobblemon.mod.common.mixin.accessor.ChunkMapAccessor
import com.cobblemon.mod.common.mixin.accessor.TrackedEntityAccessor
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ChunkMap
import net.minecraft.server.level.ServerChunkCache
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import sun.misc.Unsafe
import java.util.UUID

@BootstrapMinecraft
class ActorDirectorWorldScopeTest {
    private val unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null) as Unsafe
    private val viewer = UUID.randomUUID()
    private val level = mockk<ServerLevel>()
    private val entity = mockk<Entity>(relaxed = true, moreInterfaces = arrayOf(CinematicViewerScopedEntity::class))
    private val scoped = entity as CinematicViewerScopedEntity
    private var scope: Set<UUID>? = null
    private val director = ActorDirector(
        Scene("scope", actors = mapOf("prop" to ActorDeclaration("prop", ActorKind.SynthesizedBedrock)),
            startBeatId = "main", beats = mapOf("main" to beat(true))),
        emptyMap(), SceneNetworkSync(UUID.randomUUID(), mutableSetOf(viewer))
    )

    init {
        every { scoped.cobblemonSceneViewers } answers { scope }
        every { scoped.cobblemonSceneViewers = any() } answers { scope = firstArg() }
    }

    @Test fun `world actor scope exists before tracker registration`() {
        director.onBeatEnter(beat(true))
        every { level.addFreshEntity(entity) } answers {
            assertEquals(setOf(viewer), scope)
            true
        }

        assertSame(entity, director.spawnWorldActor("prop", entity, level))
        verify(exactly = 0) { entity.remove(any()) }
    }

    @Test fun `rejected and partially failed spawns are discarded without returning an actor`() {
        director.onBeatEnter(beat(true))
        every { level.addFreshEntity(entity) } returns false
        assertNull(director.spawnWorldActor("prop", entity, level))
        every { level.addFreshEntity(entity) } throws IllegalStateException("tracker failed")
        assertThrows(IllegalStateException::class.java) { director.spawnWorldActor("prop", entity, level) }
        verify(exactly = 2) { entity.remove(Entity.RemovalReason.DISCARDED) }
    }

    @Test fun `hidden world actors stay hidden when membership changes and can be shown again`() {
        val entities = ActorDirector::class.java.getDeclaredField("spawnedEntities").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        (entities.get(director) as MutableMap<String, Entity>)["prop"] = entity
        director.onBeatEnter(beat(true))
        assertEquals(setOf(viewer), scope)

        director.onBeatEnter(beat(false))
        assertEquals(emptySet<UUID>(), scope)
        director.updateSynthActorViewerScope(setOf(viewer, UUID.randomUUID()))
        assertEquals(emptySet<UUID>(), scope)

        director.onBeatEnter(beat(true))
        assertEquals(setOf(viewer), scope)
        director.forceHide("prop")
        assertEquals(emptySet<UUID>(), scope)
    }

    @Test fun `scope changes refresh existing tracker without moving the actor`() {
        val chunkMap = mockk<ChunkMap>(moreInterfaces = arrayOf(ChunkMapAccessor::class))
        val chunks = mockk<ServerChunkCache>()
        val realLevel = unsafe.allocateInstance(ServerLevel::class.java) as ServerLevel
        val tracker = mockk<TrackedEntityAccessor>(relaxed = true)
        val players = listOf(mockk<ServerPlayer>())
        setField(realLevel, ServerLevel::class.java, "chunkSource", chunks)
        setField(realLevel, ServerLevel::class.java, "players", players)
        every { entity.level() } returns realLevel
        every { entity.id } returns 42
        ServerChunkCache::class.java.getField("chunkMap").apply { isAccessible = true }.set(chunks, chunkMap)
        every { (chunkMap as ChunkMapAccessor).entityMap } returns Int2ObjectOpenHashMap<Any>().apply { put(42, tracker) }

        val entities = ActorDirector::class.java.getDeclaredField("spawnedEntities").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        (entities.get(director) as MutableMap<String, Entity>)["prop"] = entity
        director.onBeatEnter(beat(true))
        director.forceHide("prop")
        director.forceHide("prop")

        assertEquals(emptySet<UUID>(), scope)
        verify(exactly = 2) { tracker.`cobblemon$updatePlayers`(players) }
    }

    private fun setField(instance: Any, owner: Class<*>, name: String, value: Any) {
        val field = owner.getDeclaredField(name)
        unsafe.putObject(instance, unsafe.objectFieldOffset(field), value)
    }

    private fun beat(visible: Boolean) = Beat("main", actorDirections = mapOf("prop" to ActorDirection(visible = visible)))
}
