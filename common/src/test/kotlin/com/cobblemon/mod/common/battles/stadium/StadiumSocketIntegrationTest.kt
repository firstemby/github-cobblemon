package com.cobblemon.mod.common.battles.stadium

import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.battles.runner.ShowdownService
import com.cobblemon.mod.common.battles.runner.socket.SocketShowdownService
import io.mockk.every
import io.mockk.mockk
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class StadiumSocketIntegrationTest {
    @Test fun `socket adapter uses acknowledged newline protocol and drains only accepted starts`() {
        ServerSocket(0).use { server ->
            server.soTimeout = 10000
            val executor = Executors.newSingleThreadExecutor()
            val id = UUID.randomUUID()
            val accepted = """{"protocolVersion":1,"ok":true,"errors":[]}"""
            val rejected = """{"protocolVersion":1,"ok":false,"errors":[{"code":"banned_move","field":"moves","message":"Banned é🐉"}]}"""
            val peer = executor.submit {
                server.accept().use { socket ->
                    socket.soTimeout = 10000
                    val input = socket.getInputStream().bufferedReader(Charsets.UTF_8)
                    val output = socket.getOutputStream()
                    fun frame(value: String) {
                        val bytes = value.toByteArray(Charsets.UTF_8)
                        output.write(bytes.size.toString().padStart(8, '0').toByteArray())
                        bytes.forEach { output.write(it.toInt() and 255); output.flush() }
                    }
                    assertEquals(">getStadiumCapabilities", input.readLine())
                    frame("""{"protocolVersion":1,"features":["team-validation","structured-startup","mechanic-restrictions"],"gameTypes":["singles","doubles"],"clauses":["species_clause"]}""")
                    assertEquals(">validateStadiumBattle {}", input.readLine())
                    frame(rejected)
                    assertEquals(">startStadiumBattle $id {}", input.readLine())
                    frame(rejected)
                    assertEquals(">startStadiumBattle $id {}", input.readLine())
                    frame(accepted)
                    assertEquals(">readStadiumBattle $id", input.readLine())
                    output.write("00000000".toByteArray()); output.flush()
                    assertEquals("$id~>p1 move 1", input.readLine())
                    output.write("00000000".toByteArray()); output.flush()
                    assertEquals(">sendStadiumChoice $id {}", input.readLine())
                    frame(rejected)
                    assertEquals(">readStadiumBattle $id", input.readLine())
                    output.write("00000000".toByteArray()); output.flush()
                    assertEquals(">resolveStadiumTimeout $id {}", input.readLine())
                    frame(accepted)
                    assertEquals(">readStadiumBattle $id", input.readLine())
                    output.write("00000000".toByteArray()); output.flush()
                    assertEquals(">endStadiumBattle $id", input.readLine())
                    frame(accepted)
                }
            }
            val service = SocketShowdownService("127.0.0.1", server.localPort)
            val battle = mockk<PokemonBattle>()
            every { battle.battleId } returns id
            ShowdownService.stadiumOutput.begin(id)
            try {
                service.openConnection()
                assertTrue(service.getStadiumCapabilities().supports(StadiumConfiguration.template()))
                assertTrue(service.getStadiumCapabilities().supports(StadiumConfiguration.template(true)))
                assertEquals("Banned é🐉", service.validateStadiumBattle("{}").summary)
                assertFalse(service.startStadiumBattle(battle, "{}").ok)
                assertTrue(service.startStadiumBattle(battle, "{}").ok)
                service.send(id, arrayOf(">p1 move 1"))
                assertFalse(service.sendStadiumChoice(id, "{}").ok)
                assertTrue(service.resolveStadiumTimeout(id, "{}").ok)
                service.endBattle(battle)
                peer.get(15, TimeUnit.SECONDS)
            } finally {
                service.closeConnection()
                ShowdownService.stadiumOutput.discard(id)
                executor.shutdownNow()
            }
        }
    }
}
