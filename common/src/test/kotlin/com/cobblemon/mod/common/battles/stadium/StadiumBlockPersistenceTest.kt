package com.cobblemon.mod.common.battles.stadium

import com.cobblemon.mod.common.block.entity.StadiumBlockEntity
import com.cobblemon.mod.common.util.DataKeys
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class StadiumBlockPersistenceTest {
    @AfterEach fun cleanup() { StadiumBattleRegistry.clearDynamic() }

    @Test fun `invalid saved cinematic id disables configuration without losing stadium settings`() {
        val original = StadiumBlockData.createDefault().apply {
            name = "Arena"
            isConfigured = true
            cinematicScene = ResourceLocation.parse("cobblemon:stadium_dynamic")
            arena = StadiumArena(Vec3.ZERO, 24.0, 16.0)
            formatConfigs.getValue("singles").bannedMoves.add("batonpass")
        }
        val tag = original.toNbt().apply { putString("CinematicScene", "Invalid Resource ID") }

        val loaded = StadiumBlockData.fromNbt(tag)

        assertNull(loaded.cinematicScene)
        assertFalse(loaded.isConfigured)
        assertEquals(original.copy(cinematicScene = null, isConfigured = false), loaded)
        assertFalse(StadiumBlockData.fromNbt(loaded.toNbt()).isConfigured)
    }

    @Test fun `loading another saved stadium id removes the previously registered id`() {
        val block = block(StadiumBlockData.createDefault())
        block.registerDynamic()
        val oldId = block.data.stadiumId
        val newId = UUID.randomUUID()
        val tag = block.data.toNbt().apply { putUUID(DataKeys.STADIUM_BLOCK_ID, newId) }

        block.data = StadiumBlockData.fromNbt(tag)
        block.registerDynamic()

        assertNull(StadiumBattleRegistry.get(oldId.toString()))
        assertNotNull(StadiumBattleRegistry.get(newId.toString()))
        block.unregisterDynamic()
        assertFalse(StadiumBattleRegistry.hasDynamic(newId))
    }

    @Test fun `cloned block receives its own id and cannot unregister the original`() {
        val source = block(StadiumBlockData.createDefault())
        source.registerDynamic()
        val originalId = source.data.stadiumId
        val original = StadiumBattleRegistry.get(originalId.toString())
        val clone = block(StadiumBlockData.fromNbt(source.data.toNbt()))

        clone.unregisterDynamic()
        assertSame(original, StadiumBattleRegistry.get(originalId.toString()))
        clone.registerDynamic()

        assertNotEquals(originalId, clone.data.stadiumId)
        assertEquals(clone.data.stadiumId, StadiumBlockData.fromNbt(clone.data.toNbt()).stadiumId)
        assertSame(original, StadiumBattleRegistry.get(originalId.toString()))
        clone.unregisterDynamic()
        assertSame(original, StadiumBattleRegistry.get(originalId.toString()))
    }

    @Test fun `stale block registration cannot remove a newer owner`() {
        val block = block(StadiumBlockData.createDefault())
        block.registerDynamic()
        val id = block.data.stadiumId
        val replacement = checkNotNull(StadiumBattleRegistry.get(id.toString())).copy(origin = Vec3(5.0, 0.0, 0.0))
        StadiumBattleRegistry.registerDynamic(id, replacement)

        block.unregisterDynamic()

        assertSame(replacement, StadiumBattleRegistry.get(id.toString()))
    }

    private fun block(initial: StadiumBlockData): StadiumBlockEntity {
        val block = mockk<StadiumBlockEntity>()
        every { block.data } answers { callOriginal() }
        every { block.data = any() } answers { callOriginal() }
        block.data = initial
        every { block.setChanged() } just Runs
        every { block.registerDynamic() } answers { callOriginal() }
        every { block.unregisterDynamic() } answers { callOriginal() }
        every { block.buildStadiumBattleConfig() } answers {
            StadiumBattleConfig(block.data.stadiumId.toString(), null, Vec3.ZERO, Vec3.ZERO, null, null)
        }
        return block
    }
}
