/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.stadium

import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.battles.BattleFormat
import com.cobblemon.mod.common.battles.runner.ShowdownService
import com.cobblemon.mod.common.battles.runner.graal.GraalShowdownService
import com.cobblemon.mod.common.battles.runner.graal.ShowdownBundleInstaller
import com.google.gson.Gson
import io.mockk.every
import io.mockk.mockk
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class StadiumGraalIntegrationTest {
    @TempDir lateinit var temporary: Path

    @Test fun `preview ordering mechanic restrictions and timeout use the bundled engine`() {
        val directory = temporary.resolve("showdown")
        ShowdownBundleInstaller.install(directory,
            checkNotNull(javaClass.getResourceAsStream("/data/cobblemon/showdown.zip")),
            checkNotNull(javaClass.getResourceAsStream("/data/cobblemon/showdown.json")))
        val service = GraalShowdownService()
        service.openConnection(directory)
        val id = UUID.randomUUID()
        val battle = mockk<PokemonBattle>()
        every { battle.battleId } returns id
        ShowdownService.stadiumOutput.begin(id)
        try {
            val config = StadiumConfiguration.template().copy(teamPreview = true, allowedGimmicks = mutableSetOf(), moveTimer = 30)
            assertTrue(service.getStadiumCapabilities().supports(config))
            val teams = (1..2).map { side -> (1..2).map { slot ->
                val pokemonId = UUID.randomUUID()
                "bulbasaur||$pokemonId|${70 + side + slot}||-1||overgrow|tackle,growl|35/35,40/40|timid|0,0,0,252,4,252|M|31,31,31,31,31,31||50|255,pokeball,Ice,G,9,Stellar,"
            } }
            val sides = teams.mapIndexed { index, team -> StadiumShowdownProtocol.Side("p${index + 1}", UUID.randomUUID().toString(), team.joinToString("]")) }
            val started = service.startStadiumBattle(battle, StadiumShowdownProtocol.request(config, sides))
            assertTrue(started.ok, started.summary)
            service.context.eval("js", "var stadiumTestBattle = battleMap.get('$id').battle")
            fun state(expression: String) = service.context.eval("js", "stadiumTestBattle.$expression")
            fun request(side: String) = state("$side.activeRequest.rqid").asInt()
            fun choose(side: String, rqid: Int, choice: String) = service.sendStadiumChoice(id,
                Gson().toJson(mapOf("protocolVersion" to 1, "side" to side, "rqid" to rqid, "choice" to choice)))
            val first = request("p1")
            assertEquals("teampreview", state("requestState").asString())
            assertEquals(0, state("turn").asInt())
            assertFalse(choose("p1", first + 100, "team 2,1").ok)
            assertTrue(choose("p1", first, "team 2,1").ok)
            assertEquals(0, state("turn").asInt())
            assertFalse(choose("p1", first, "team 2,1").ok)
            assertTrue(choose("p2", request("p2"), "team 1,2").ok)
            assertEquals(1, state("turn").asInt())
            assertEquals(73, state("p1.pokemon[0].hp").asInt())
            assertFalse(state("p1.activeRequest.active[0].canTerastallize === 'Stellar'").asBoolean())
            assertFalse(choose("p1", request("p1"), "move 2 terastallize").ok)
            val stale = service.resolveStadiumTimeout(id, """{"protocolVersion":1,"scope":"decision","expiredSides":[{"id":"p1","rqid":$first}]}""")
            assertFalse(stale.ok)
            val timeout = service.resolveStadiumTimeout(id, """{"protocolVersion":1,"scope":"decision","expiredSides":[{"id":"p1","rqid":${request("p1")}}]}""")
            assertTrue(timeout.ok, timeout.summary)
            assertTrue(state("ended").asBoolean())
            assertEquals(sides[1].name, state("winner").asString())
            assertFalse(service.context.eval("js", "battleMap.has('$id')").asBoolean())
            service.endBattle(battle)
        } finally {
            ShowdownService.stadiumOutput.discard(id)
            service.closeConnection()
        }
    }

    @Test fun `bundled engine validates and acknowledges through actual Graal adapter`() {
        val directory = temporary.resolve("showdown")
        ShowdownBundleInstaller.install(directory,
            checkNotNull(javaClass.getResourceAsStream("/data/cobblemon/showdown.zip")),
            checkNotNull(javaClass.getResourceAsStream("/data/cobblemon/showdown.json")))
        val service = GraalShowdownService()
        service.openConnection(directory)
        try {
            val config = StadiumConfiguration.template()
            assertTrue(service.getStadiumCapabilities().supports(config))
            val sides = (1..2).map { index ->
                val pokemonId = "00000000-0000-0000-0000-${index.toString().padStart(12, '0')}"
                val team = "bulbasaur||$pokemonId|71||-1||overgrow|tackle,growl|0/35,40/40|timid|0,0,0,252,4,252|M|31,31,31,31,31,31|S|50|255,pokeball,Ice,G,9,Stellar,"
                StadiumShowdownProtocol.Side("p$index", UUID.randomUUID().toString(), team)
            }
            val request = StadiumShowdownProtocol.request(config, sides)
            val validation = service.validateStadiumBattle(request)
            assertTrue(validation.ok, validation.summary)
            val rejected = service.validateStadiumBattle(StadiumShowdownProtocol.request(
                config.copy(bannedMoves = mutableSetOf("tackle")), sides))
            assertFalse(rejected.ok)
            assertTrue(rejected.errors.any { it.code == "banned_move" && it.side == "p1" })
            assertFalse(service.validateStadiumBattle("{}").ok)

            val battle = mockk<PokemonBattle>()
            val id = UUID.randomUUID()
            every { battle.battleId } returns id
            ShowdownService.stadiumOutput.begin(id)
            try {
                val started = service.startStadiumBattle(battle, request)
                assertTrue(started.ok, started.summary)
                assertEquals(71, service.context.eval("js", "battleMap.get('$id').battle.p1.pokemon[0].hp").asInt())
                assertEquals(0, service.context.eval("js", "battleMap.get('$id').battle.p1.pokemon[0].moveSlots[0].pp").asInt())
                assertEquals(sides[0].name, service.context.eval("js", "battleMap.get('$id').battle.p1.name").asString())
                assertFalse(service.startStadiumBattle(battle, request).ok)
                service.send(id, arrayOf(">p1 move 2", ">p2 move 2"))
                assertEquals(2, service.context.eval("js", "battleMap.get('$id').battle.turn").asInt())
                service.endBattle(battle)
                assertTrue(service.startStadiumBattle(battle, request).ok)
                service.endBattle(battle)
                val doubles = sides.map { side -> side.copy(team = side.team + "]" +
                    side.team.replace(side.team.split('|')[2], UUID.randomUUID().toString())) }
                val doublesStart = service.startStadiumBattle(battle,
                    StadiumShowdownProtocol.request(StadiumConfiguration.template(true), doubles))
                assertTrue(doublesStart.ok, doublesStart.summary)
                assertEquals(2, service.context.eval("js", "battleMap.get('$id').battle.p1.active.length").asInt())
                service.endBattle(battle)
                val legacy = listOf(">start {\"format\":${BattleFormat.GEN_9_SINGLES.toFormatJSON()}}") +
                    sides.map { ">player ${it.id} ${Gson().toJson(mapOf("name" to it.name, "team" to it.team))}" } +
                    listOf(">p1 team 1", ">p2 team 1")
                service.startBattle(battle, legacy.toTypedArray())
                assertTrue(service.context.eval("js", "battleMap.get('$id').battle.started").asBoolean())
                assertEquals("battle_exists", service.startStadiumBattle(battle, request).errors.single().code)
                service.endBattle(battle)
            } finally {
                ShowdownService.stadiumOutput.discard(id)
            }
        } finally {
            service.closeConnection()
        }
    }

    @Test fun `upgrading bundle preserves previous installation and publishes metadata last`() {
        val target = Files.createDirectory(temporary.resolve("showdown"))
        Files.writeString(target.resolve("custom.js"), "previous custom engine")
        Files.writeString(target.resolve("showdown.json"), "{\"showdownVersion\":18}")
        val backup = ShowdownBundleInstaller.install(target,
            checkNotNull(javaClass.getResourceAsStream("/data/cobblemon/showdown.zip")),
            checkNotNull(javaClass.getResourceAsStream("/data/cobblemon/showdown.json")))
        assertNotNull(backup)
        assertEquals("previous custom engine", Files.readString(backup!!.resolve("custom.js")))
        assertFalse(Files.exists(target.resolve("custom.js")))
        assertTrue(Files.isRegularFile(target.resolve("index.js")))
        assertTrue(Files.readString(target.resolve("showdown.json")).contains("20"))
    }

    @Test fun `invalid bundle leaves current installation untouched`() {
        val target = Files.createDirectory(temporary.resolve("showdown"))
        Files.writeString(target.resolve("index.js"), "current")
        assertThrows(Exception::class.java) {
            ShowdownBundleInstaller.install(target, "bad zip".byteInputStream(), "new metadata".byteInputStream())
        }
        assertEquals("current", Files.readString(target.resolve("index.js")))
        assertFalse(Files.exists(target.resolve("showdown.json")))
        assertTrue(temporaryInstallDirectories().isEmpty())
    }

    @Test fun `failed bundle publication restores current installation and removes staging`() {
        val target = Files.createDirectory(temporary.resolve("showdown"))
        Files.writeString(target.resolve("index.js"), "current")

        assertThrows(IOException::class.java) {
            ShowdownBundleInstaller.install(target,
                validBundle().inputStream(), "{}".byteInputStream()) { source, destination ->
                    if (source.fileName.toString().startsWith("showdown-install-")) throw IOException("publish failed")
                    Files.move(source, destination)
                }
        }

        assertEquals("current", Files.readString(target.resolve("index.js")))
        assertTrue(temporaryInstallDirectories().isEmpty())
        assertTrue(Files.list(temporary).use { entries ->
            entries.noneMatch { it.fileName.toString().startsWith("showdown-backup-") }
        })
    }

    @Test fun `restore failure is suppressed under the publication failure and preserves backup`() {
        val target = Files.createDirectory(temporary.resolve("showdown"))
        Files.writeString(target.resolve("index.js"), "current")

        val failure = assertThrows(IOException::class.java) {
            ShowdownBundleInstaller.install(target,
                validBundle().inputStream(), "{}".byteInputStream()) { source, destination ->
                    when {
                        source.fileName.toString().startsWith("showdown-install-") -> throw IOException("publish failed")
                        source.parent.fileName.toString().startsWith("showdown-backup-") -> throw IOException("restore failed")
                        else -> Files.move(source, destination)
                    }
                }
        }

        assertEquals("publish failed", failure.message)
        assertEquals("restore failed", failure.suppressed.single().message)
        assertTrue(temporaryInstallDirectories().isEmpty())
        val backup = Files.list(temporary).use { entries ->
            entries.filter { it.fileName.toString().startsWith("showdown-backup-") }.findFirst().orElseThrow()
        }
        assertEquals("current", Files.readString(backup.resolve("showdown/index.js")))
    }

    private fun temporaryInstallDirectories() = Files.list(temporary).use { entries ->
        entries.filter { it.fileName.toString().startsWith("showdown-install-") }.toList()
    }

    private fun validBundle(): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("index.js"))
            zip.write("engine".toByteArray())
            zip.closeEntry()
        }
        return bytes.toByteArray()
    }
}
