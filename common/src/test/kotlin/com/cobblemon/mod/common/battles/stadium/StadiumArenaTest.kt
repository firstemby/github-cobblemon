package com.cobblemon.mod.common.battles.stadium

import net.minecraft.network.chat.contents.TranslatableContents
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class StadiumArenaTest {
    @Test fun `arena bounds include their edges and reject invalid sizes and distant corners`() {
        val origin = Vec3(100.0, 64.0, -200.0)
        val arena = StadiumArena(origin, 16.0, 12.0)
        assertTrue(arena.valid(origin))
        assertTrue(arena.contains(origin.add(8.0, 2.0, 6.0)))
        assertFalse(arena.contains(origin.add(8.01, 0.0, 0.0)))
        assertFalse(arena.copy(width = Double.NaN).valid(origin))
        assertFalse(arena.copy(depth = 1.0).valid(origin))
        assertFalse(arena.copy(center = origin.add(90.0, 0.0, 0.0)).valid(origin))
    }

    @Test fun `custom trainer and pokemon counts persist independently and enforce their placement limits`() {
        val data = StadiumBlockData.createDefault().apply {
            arena = StadiumArena(Vec3.ZERO, 24.0, 16.0)
            cinematicLayout = "custom"
            setLayout("custom", StadiumBlockLayout.createDefault().apply {
                trainerSlots = 3; pokemonSlots = 5
                side1.actors.add(StadiumBlockPlacement(Vec3(-8.0, 0.0, 0.0)))
                repeat(5) { side1.pokemon.add(StadiumBlockPlacement(Vec3(-4.0, 0.0, it.toDouble()))) }
            })
        }
        val restored = StadiumBlockData.fromNbt(data.toNbt())
        assertEquals(data, restored)
        assertEquals(3, restored.slots("custom", "actors"))
        assertEquals(5, restored.slots("custom", "pokemon"))
        assertEquals(2, restored.slots("doubles", "pokemon"))
        assertTrue(StadiumConfiguration.errors(restored, Vec3.ZERO).isEmpty())
        restored.layout("custom")!!.pokemonSlots = 4
        assertTrue(StadiumConfiguration.errors(restored, Vec3.ZERO).any { (it.contents as TranslatableContents).key == "cobblemon.stadium.validation.excess_placements" })
        assertEquals(5, data.layout("custom")!!.pokemonSlots)
    }

    @Test fun `shrinking the area rejects existing placements across every layout`() {
        for (id in StadiumConfiguration.layoutIds) {
            val data = StadiumBlockData.createDefault().apply {
                arena = StadiumArena(Vec3.ZERO, 10.0, 10.0)
                setLayout(id, StadiumBlockLayout.createDefault().apply {
                    side2.pokemon.add(StadiumBlockPlacement(Vec3(6.0, 0.0, 0.0)))
                })
            }
            assertTrue(StadiumConfiguration.errors(data, Vec3.ZERO).any { (it.contents as TranslatableContents).key == "cobblemon.stadium.validation.outside_arena" }, id)
            data.arena = data.arena!!.copy(width = 12.0)
            assertTrue(StadiumConfiguration.errors(data, Vec3.ZERO).isEmpty(), id)
        }
    }

    @Test fun `unconfigured layouts allow an absent arena and unsupported battle types remain rejected`() {
        val unconfigured = StadiumBlockData.createDefault().toNbt().apply {
            remove("Arena"); remove("AdditionalLayouts"); remove("CinematicLayout")
        }
        val data = StadiumBlockData.fromNbt(unconfigured)
        assertNull(data.arena)
        assertTrue(data.additionalLayouts.isEmpty())
        assertTrue(StadiumConfiguration.errors(data, Vec3.ZERO).isEmpty())
        assertTrue(StadiumConfiguration.formatErrors(StadiumConfiguration.template().copy(battleTypeName = "5v5")).isNotEmpty())
    }
}
