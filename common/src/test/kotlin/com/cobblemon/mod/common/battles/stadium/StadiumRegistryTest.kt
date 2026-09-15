/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.stadium

import com.google.gson.JsonParser
import io.mockk.every
import io.mockk.mockk
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.Resource
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.function.Predicate

class StadiumRegistryTest {
    private val resourceId = ResourceLocation.parse("cobblemon:stadium_battles/test.json")

    private fun load(source: String?) {
        val manager = mockk<ResourceManager>()
        val resources = if (source == null) emptyMap() else mapOf(resourceId to mockk<Resource> {
            every { open() } answers { source.byteInputStream(Charsets.UTF_8) }
        })
        every { manager.listResources("stadium_battles", any<Predicate<ResourceLocation>>()) } returns resources
        StadiumBattleRegistry.reload(manager)
    }

    @AfterEach fun cleanup() {
        StadiumBattleRegistry.clearDynamic()
        load(null)
    }

    private fun fixture() = JsonParser.parseString("""{
        "id":"arena", "origin":{"x":1,"y":64,"z":2},
        "singles":{
            "side1":{"actors":[{"position":{"x":-3,"y":64,"z":0}}],"pokemon":[]},
            "side2":{"actors":[],"pokemon":[]}
        }
    }""").asJsonObject

    @Test fun `removing a dynamic stadium restores its resource definition across reloads`() {
        load(fixture().toString())
        val file = checkNotNull(StadiumBattleRegistry.get("arena"))
        val uuid = UUID.randomUUID()
        val dynamic = file.copy(origin = Vec3(12.0, 70.0, 15.0))
        StadiumBattleRegistry.registerDynamic(uuid, dynamic)
        assertSame(dynamic, StadiumBattleRegistry.get("arena"))
        load(fixture().apply { getAsJsonObject("origin").addProperty("y", 80) }.toString())
        assertSame(dynamic, StadiumBattleRegistry.get("arena"))
        StadiumBattleRegistry.unregisterDynamic(uuid, dynamic)
        assertEquals(80.0, StadiumBattleRegistry.get("arena")!!.origin.y)
        assertEquals(setOf("arena"), StadiumBattleRegistry.ids())
    }

    @Test fun `changing a dynamic id removes its old lookup and preserves other overrides`() {
        load(fixture().toString())
        val file = checkNotNull(StadiumBattleRegistry.get("arena"))
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        val firstConfig = file.copy(origin = Vec3(10.0, 64.0, 0.0))
        StadiumBattleRegistry.registerDynamic(first, firstConfig)
        val replacement = file.copy(origin = Vec3(20.0, 64.0, 0.0))
        StadiumBattleRegistry.registerDynamic(second, replacement)
        StadiumBattleRegistry.unregisterDynamic(first, firstConfig)
        assertSame(replacement, StadiumBattleRegistry.get("arena"))
        StadiumBattleRegistry.registerDynamic(second, replacement.copy(id = "renamed"))
        assertSame(file, StadiumBattleRegistry.get("arena"))
        assertEquals(setOf("arena", "renamed"), StadiumBattleRegistry.ids())
        StadiumBattleRegistry.clearDynamic()
        assertEquals(listOf(file), StadiumBattleRegistry.all().toList())
    }

    @Test fun `nonfinite resource coordinates and rotations never enter the registry`() {
        val invalidOrigin = fixture().apply { getAsJsonObject("origin").addProperty("x", "NaN") }
        load(invalidOrigin.toString())
        assertNull(StadiumBattleRegistry.get("arena"))
        for (field in listOf("yaw", "pitch")) {
            val invalidRotation = fixture().apply {
                getAsJsonObject("singles").getAsJsonObject("side1").getAsJsonArray("actors")[0].asJsonObject
                    .addProperty(field, "Infinity")
            }
            load(invalidRotation.toString())
            assertNull(StadiumBattleRegistry.get("arena"))
        }
    }

    @Test fun `file arena and spectator bounds resolve relative to stadium origin`() {
        val source = fixture().apply {
            add("arena", JsonParser.parseString("""{"center":{"x":2,"y":0,"z":3},"width":32,"depth":24}"""))
            add("spectator_areas", JsonParser.parseString("""[{"min":{"x":-4,"y":0,"z":-20},"max":{"x":4,"y":4,"z":-16}}]"""))
        }

        load(source.toString())

        val config = checkNotNull(StadiumBattleRegistry.get("arena"))
        assertEquals(StadiumArena(Vec3(3.0, 64.0, 5.0), 32.0, 24.0), config.arena)
        assertEquals(listOf(StadiumSpectatorArea(Vec3(-3.0, 64.0, -18.0), Vec3(5.0, 68.0, -14.0))), config.spectatorAreas)
    }

    @Test fun `file format options preserve every supported battle rule`() {
        val source = fixture().apply {
            addProperty("active_format", "tournament")
            add("formats", JsonParser.parseString("""{"tournament":{
                "format_name":"Tournament","battle_type":"doubles","clauses":["species_clause"],
                "level_cap":50,"team_preview":true,"pokemon_limit":4,"move_timer":60,"match_timer_minutes":20,
                "banned_pokemon":["mewtwo"],"banned_moves":["batonpass"],"banned_abilities":["moody"],
                "banned_items":["leftovers"],"allowed_gimmicks":["mega_evolution"],
                "allow_held_items":false,"unique_held_items":true
            }}"""))
        }

        load(source.toString())

        val config = checkNotNull(StadiumBattleRegistry.get("arena"))
        assertEquals("tournament", config.activeFormat)
        assertEquals(StadiumFormatConfig("Tournament", "doubles", mutableSetOf("species_clause"), 50, true, 4, 60, 20,
            mutableSetOf("mewtwo"), mutableSetOf("batonpass"), mutableSetOf("moody"), mutableSetOf("leftovers"),
            mutableSetOf("mega_evolution"), false, true), config.formats.getValue("tournament"))
    }

    @Test fun `omitted file options use templates while malformed authored values are rejected`() {
        val source = fixture().apply {
            addProperty("active_format", "custom")
            add("formats", JsonParser.parseString("""{"custom":{"battle_type":"singles"}}"""))
        }
        load(source.toString())
        assertEquals(StadiumConfiguration.template(), StadiumBattleRegistry.get("arena")!!.formats.getValue("custom"))
        val invalidOptions = mapOf(
            "level_cap" to "50.5", "pokemon_limit" to "2147483648", "move_timer" to "0",
            "team_preview" to "\"true\"", "allowed_gimmicks" to "null", "banned_moves" to "[\"batonpass\",\"batonpass\"]",
            "unknown_rule" to "true", "battle_type" to "\"triples\""
        )
        invalidOptions.forEach { (field, value) ->
            val invalid = source.deepCopy()
            invalid.getAsJsonObject("formats").getAsJsonObject("custom").add(field, JsonParser.parseString(value))
            load(invalid.toString())
            assertNull(StadiumBattleRegistry.get("arena"), field)
        }
        source.remove("active_format")
        load(source.toString())
        assertNull(StadiumBattleRegistry.get("arena"))
    }

    @Test fun `invalid file arena or spectator geometry is rejected instead of discarded`() {
        val invalidArenas = listOf("null", "[]",
            """{"center":{"x":0,"y":0,"z":0},"width":129,"depth":16}""",
            """{"center":{"x":0,"y":0,"z":0},"width":"16","depth":16}""")
        invalidArenas.forEach { value ->
            load(fixture().apply { add("arena", JsonParser.parseString(value)) }.toString())
            assertNull(StadiumBattleRegistry.get("arena"), value)
        }
        load(fixture().apply {
            add("spectator_areas", JsonParser.parseString("""[{"min":{"x":0,"y":0,"z":0},"max":{"x":4,"y":4,"z":4}}]"""))
        }.toString())
        assertNull(StadiumBattleRegistry.get("arena"))
        load(fixture().apply {
            add("arena", JsonParser.parseString("""{"center":{"x":0,"y":0,"z":0},"width":32,"depth":24}"""))
            add("spectator_areas", JsonParser.parseString("""[{"min":{"x":0,"y":0,"z":0},"max":{"x":4,"y":1,"z":4}}]"""))
        }.toString())
        assertNull(StadiumBattleRegistry.get("arena"))
    }
}
