/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.stadium

import net.minecraft.network.chat.contents.TranslatableContents
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StadiumConfigurationTest {
    @Test fun `default names remain translatable after persistence and custom names remain literal`() {
        val data = StadiumBlockData.fromNbt(StadiumBlockData.createDefault().toNbt())
        assertEquals("", data.name)
        assertEquals("cobblemon.stadium.default_name", (data.displayName.contents as TranslatableContents).key)
        for (type in listOf("singles", "doubles")) {
            val format = data.formatConfigs.getValue(type)
            assertEquals("", format.formatName)
            assertEquals("cobblemon.stadium.layout.$type", (format.displayName.contents as TranslatableContents).key)
            assertEquals(Component.literal("My format"), format.copy(formatName = "My format").displayName)
        }
        data.name = "My stadium"
        assertEquals(Component.literal("My stadium"), StadiumBlockData.fromNbt(data.toNbt()).displayName)
        data.name = "   "
        assertTrue(StadiumConfiguration.errors(data, Vec3.ZERO).any { (it.contents as TranslatableContents).key == "cobblemon.stadium.validation.name" })
        val whitespace = StadiumConfiguration.template().copy(formatName = "   ")
        assertTrue(StadiumConfiguration.formatErrors(whitespace).any { (it.contents as TranslatableContents).key == "cobblemon.stadium.validation.format_name" })
    }

    @Test fun `local item rules persist and do not require simulation integration`() {
        val config = StadiumConfiguration.template().copy(allowHeldItems = false, uniqueHeldItems = true)
        assertEquals(config, StadiumFormatConfig.fromNbt(config.toNbt()))
        assertTrue(StadiumConfiguration.formatErrors(config).isEmpty())
        assertTrue(StadiumConfiguration.engineRequirements(config).isEmpty())
    }

    @Test fun `new stadium has usable templates but is not enabled`() {
        val data = StadiumBlockData.createDefault()
        assertEquals("singles", data.activeFormat)
        assertFalse(data.isConfigured)
        assertTrue(StadiumConfiguration.errors(data, Vec3.ZERO).isEmpty())
        assertFalse(StadiumConfiguration.readiness(data).isEmpty())
        assertTrue(data.formatConfigs.values.all { StadiumConfiguration.engineRequirements(it).isEmpty() })
    }

    @Test fun `draft and scene survive NBT roundtrip without aliasing`() {
        val data = StadiumBlockData.createDefault()
        data.cinematicScene = ResourceLocation.parse("cobblemon:colosseum_battle")
        data.formatConfigs.getValue("singles").bannedMoves.add("batonpass")
        val copy = StadiumBlockData.fromNbt(data.toNbt())
        assertEquals(data, copy)
        copy.formatConfigs.getValue("singles").bannedMoves.clear()
        assertEquals(setOf("batonpass"), data.formatConfigs.getValue("singles").bannedMoves)
    }

    @Test fun `ready layout requires exact active slots on both sides`() {
        val data = StadiumBlockData.createDefault()
        data.singlesLayout = layout(1)
        assertTrue(StadiumConfiguration.readiness(data).isEmpty())
        data.activeFormat = "doubles"
        data.doublesLayout = layout(1)
        assertFalse(StadiumConfiguration.readiness(data).isEmpty())
        data.doublesLayout = layout(2)
        assertTrue(StadiumConfiguration.readiness(data).isEmpty())
    }

    @Test fun `unsupported rules can be drafted but cannot be declared ready`() {
        val data = StadiumBlockData.createDefault()
        data.singlesLayout = layout(1)
        data.formatConfigs["singles"] = StadiumConfiguration.template().copy(clauses = mutableSetOf("sleep_clause"))
        assertTrue(StadiumConfiguration.errors(data, Vec3.ZERO).isEmpty())
        assertTrue(StadiumConfiguration.readiness(data).any { (it.contents as TranslatableContents).key == "cobblemon.stadium.validation.simulation_required" })
    }

    @Test fun `invalid numbers and distant placements are rejected`() {
        val data = StadiumBlockData.createDefault()
        data.singlesLayout = layout(1)
        data.singlesLayout!!.side1.actors[0].position = Vec3(Double.NaN, 0.0, 0.0)
        assertFalse(StadiumConfiguration.errors(data, Vec3.ZERO).isEmpty())
        assertFalse(StadiumConfiguration.validPosition(Vec3(97.0, 0.0, 0.0), Vec3.ZERO))
        assertFalse(StadiumConfiguration.validPosition(Vec3(0.0, Double.POSITIVE_INFINITY, 0.0), Vec3.ZERO))
    }

    @Test fun `rule bounds reject invalid and unsupported battle types`() {
        val config = StadiumConfiguration.template(true).copy(pokemonLimit = 1, levelCap = 101, moveTimer = 0, matchTimerMinutes = 999)
        assertEquals(4, StadiumConfiguration.formatErrors(config).size)
        assertFalse(StadiumConfiguration.formatErrors(StadiumConfiguration.template().copy(battleTypeName = "triples")).isEmpty())
    }

    @Test fun `ban lists reject Minecraft namespaces instead of silently treating them as Showdown ids`() {
        val format = StadiumConfiguration.template().copy(bannedItems = mutableSetOf("cobblemon:leftovers"))
        assertFalse(StadiumConfiguration.formatErrors(format).isEmpty())
        assertTrue(StadiumConfiguration.formatErrors(format.copy(bannedItems = mutableSetOf("leftovers"))).isEmpty())
    }

    @Test fun `copied format owns its restriction sets`() {
        val original = StadiumConfiguration.template()
        val copy = StadiumConfiguration.copy(original)
        copy.allowedGimmicks.clear()
        copy.bannedPokemon.add("mewtwo")
        assertEquals(StadiumConfiguration.gimmicks, original.allowedGimmicks)
        assertTrue(original.bannedPokemon.isEmpty())
    }

    @Test fun `overlapping actor and pokemon placements are not ready`() {
        val data = StadiumBlockData.createDefault()
        data.singlesLayout = layout(1)
        data.singlesLayout!!.side1.pokemon[0].position = data.singlesLayout!!.side1.actors[0].position
        assertTrue(StadiumConfiguration.readiness(data).any { (it.contents as TranslatableContents).key == "cobblemon.stadium.validation.overlapping_placements" })
    }

    @Test fun `world placements become runtime offsets exactly once`() {
        val layout = layout(1)
        val origin = Vec3(-125.0, 63.0, 400.0)
        val actor = layout.side1.actors[0]
        actor.position = Vec3(-128.5, 64.0, 401.5)
        actor.lookAt = Vec3(-120.5, 64.0, 401.5)
        actor.yaw = 90f
        val converted = StadiumConfiguration.toBattleLayout(layout, origin).side1.actors[0]
        assertEquals(Vec3(-3.5, 1.0, 1.5), converted.position)
        assertEquals(actor.position, origin.add(converted.position))
        assertEquals(actor.lookAt, origin.add(converted.lookAt!!))
        assertEquals(90f, converted.yaw)
        assertEquals(Vec3(-128.5, 64.0, 401.5), actor.position)
    }

    private fun layout(slots: Int): StadiumBlockLayout = StadiumBlockLayout(
        StadiumBlockSide(mutableListOf(StadiumBlockPlacement(Vec3(-4.0, 0.0, 0.0))), MutableList(slots) { StadiumBlockPlacement(Vec3(-2.0, 0.0, it.toDouble())) }),
        StadiumBlockSide(mutableListOf(StadiumBlockPlacement(Vec3(4.0, 0.0, 0.0))), MutableList(slots) { StadiumBlockPlacement(Vec3(2.0, 0.0, it.toDouble())) })
    )
}
