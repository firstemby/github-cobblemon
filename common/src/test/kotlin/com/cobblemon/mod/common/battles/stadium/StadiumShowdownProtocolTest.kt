/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.stadium

import com.cobblemon.mod.common.battles.runner.ShowdownService
import com.google.gson.JsonParser
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.contents.TranslatableContents

class StadiumShowdownProtocolTest {
    @Test fun `request preserves packed teams and uses normalized effect bans`() {
        val config = StadiumConfiguration.template().copy(bannedMoves = mutableSetOf("baton_pass", "batonpass"))
        val team = "packed|with\"quotes\nand unicode é"
        val json = JsonParser.parseString(StadiumShowdownProtocol.request(config,
            listOf(StadiumShowdownProtocol.Side("p1", "actor", team)))).asJsonObject
        assertEquals(team, json.getAsJsonArray("sides")[0].asJsonObject["team"].asString)
        val format = json.getAsJsonObject("format")
        assertEquals(0, format["maxLevel"].asInt)
        assertEquals(4, format.getAsJsonArray("allowedMechanics").size())
        assertEquals("[\"batonpass\"]", format.getAsJsonObject("bans")["moves"].toString())
    }

    @Test fun `malformed or incompatible responses fail closed`() {
        listOf("null", "{}", "not json", """{"protocolVersion":1.5,"ok":true,"errors":[]}""",
            """{"protocolVersion":1,"ok":"true","errors":[]}""",
            """{"protocolVersion":1,"ok":false,"errors":[]}""",
            """{"protocolVersion":2,"ok":true,"errors":[]}""").forEach {
            assertFalse(StadiumShowdownProtocol.result(it).ok, it)
        }
        assertTrue(StadiumShowdownProtocol.result("""{"protocolVersion":1,"ok":true,"errors":[]}""").ok)
        assertFalse(StadiumShowdownProtocol.capabilities("{}").supports(StadiumConfiguration.template()))
    }

    @Test fun `structured errors retain side and pokemon reference`() {
        val result = StadiumShowdownProtocol.result("""{"protocolVersion":1,"ok":false,"errors":[{"code":"banned_move","field":"moves","message":"Move is banned.","side":"p2","pokemonUuid":"abc","value":"batonpass"}]}""")
        assertEquals("p2: Move is banned.", result.summary)
        assertEquals("abc", result.errors.single().pokemonUuid)
        assertEquals("batonpass", result.errors.single().value)
        val reason = Component.translatable("cobblemon.stadium.engine.banned_move")
        val value = Component.translatable("cobblemon.stadium.engine.value", reason, "batonpass")
        val field = Component.translatable("cobblemon.stadium.engine.field", Component.translatable("cobblemon.stadium.engine.field.moves"), value)
        assertEquals(Component.translatable("cobblemon.stadium.engine.side", "2", field), result.userMessage)
    }

    @Test fun `unknown fields do not appear as raw player text`() {
        val error = StadiumShowdownProtocol.Error("unsupported_field", "clientSecret", "Unknown request field.")
        assertEquals(Component.translatable("cobblemon.stadium.engine.field",
            Component.translatable("cobblemon.stadium.engine.field.unknown"),
            Component.translatable("cobblemon.stadium.engine.unsupported_field")), error.userMessage)
    }

    @Test fun `unknown engine error codes use the translated fallback`() {
        val error = StadiumShowdownProtocol.Error("future_code", "", "Untranslated engine detail")
        assertEquals("cobblemon.stadium.engine.rejected", (error.userMessage.contents as TranslatableContents).key)
    }

    @Test fun `unsupported settings never reach validation transport`() {
        val service = mockk<ShowdownService>()
        assertFalse(StadiumShowdownProtocol.validate(service, StadiumConfiguration.template().copy(clauses = mutableSetOf("sleep_clause")), "{}").ok)
        verify(exactly = 0) { service.getStadiumCapabilities() }
        verify(exactly = 0) { service.validateStadiumBattle(any()) }
    }

    @Test fun `old and unavailable engines do not start stadiums`() {
        val service = mockk<ShowdownService>()
        every { service.getStadiumCapabilities() } returns StadiumShowdownProtocol.Capabilities()
        assertEquals("unsupported_engine", StadiumShowdownProtocol.validate(service, StadiumConfiguration.template(), "{}").errors.single().code)
        every { service.getStadiumCapabilities() } throws IllegalStateException()
        assertEquals("engine_unavailable", StadiumShowdownProtocol.validate(service, StadiumConfiguration.template(), "{}").errors.single().code)
        verify(exactly = 0) { service.validateStadiumBattle(any()) }
    }
}
