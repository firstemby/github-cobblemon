/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.stadium

import com.cobblemon.mod.common.battles.runner.StadiumOutputGate
import com.cobblemon.mod.common.battles.runner.socket.ShowdownSocketReader
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.nio.charset.CharacterCodingException
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class StadiumTransportTest {
    private fun fragmented(value: String) = ShowdownSocketReader(object : ByteArrayInputStream(value.toByteArray(Charsets.UTF_8)) {
        override fun read(bytes: ByteArray, offset: Int, length: Int) = super.read(bytes, offset, minOf(length, 1))
    })
    private fun header(length: Int) = length.toString().padStart(8, '0')

    @Test fun `fragmented byte JSON and UTF16 messages can share a stream`() {
        val json = "{\"name\":\"é🐉\"}"
        val message = "update\né🐉"
        val reader = fragmented(header(json.toByteArray().size) + json + header(1) + header(message.length) + message + "ACK")
        assertEquals(json, reader.json())
        assertEquals(listOf(message), reader.messages())
        assertEquals("ACK", reader.characters(3))
    }

    @Test fun `broken lengths UTF8 and truncated frames are rejected`() {
        assertThrows(EOFException::class.java) { fragmented("00000005abc").message() }
        assertThrows(IllegalArgumentException::class.java) { fragmented("99999999").message() }
        assertThrows(IllegalArgumentException::class.java) { fragmented("0000000x").message() }
        assertThrows(IllegalArgumentException::class.java) { fragmented("00000001🐉").message() }
        val invalid = ShowdownSocketReader(ByteArrayInputStream("00000001".toByteArray() + byteArrayOf(255.toByte())))
        assertThrows(CharacterCodingException::class.java) { invalid.json() }
    }

    @Test fun `startup output is delivered only after acceptance`() {
        val id = UUID.randomUUID()
        val output = mutableListOf<String>()
        val gate = StadiumOutputGate { _, message -> output.add(message) }
        gate.begin(id)
        gate.receive(id, "initial")
        assertTrue(output.isEmpty())
        gate.commit(id)
        gate.receive(id, "turn")
        assertEquals(listOf("initial", "turn"), output)
        gate.begin(id)
        gate.receive(id, "rejected")
        gate.discard(id)
        gate.commit(id)
        assertEquals(listOf("initial", "turn"), output)
    }

    @Test fun `discarding during delivery stops the cancelled startup queue`() {
        val id = UUID.randomUUID()
        val output = mutableListOf<String>()
        lateinit var gate: StadiumOutputGate
        gate = StadiumOutputGate { _, message ->
            output.add(message)
            gate.discard(id)
        }
        gate.begin(id)
        gate.receive(id, "first")
        gate.receive(id, "cancelled")
        gate.commit(id)
        assertEquals(listOf("first"), output)
    }

    @Test fun `replacing a queue during delivery preserves the replacement until its own commit`() {
        val id = UUID.randomUUID()
        val output = mutableListOf<String>()
        lateinit var gate: StadiumOutputGate
        gate = StadiumOutputGate { _, message ->
            output.add(message)
            if (message == "first") {
                gate.discard(id)
                gate.begin(id)
                gate.receive(id, "replacement")
            }
        }
        gate.begin(id)
        gate.receive(id, "first")
        gate.receive(id, "cancelled")
        gate.commit(id)
        assertEquals(listOf("first"), output)
        gate.commit(id)
        assertEquals(listOf("first", "replacement"), output)
    }

    @Test fun `reentrant output preserves startup ordering`() {
        val id = UUID.randomUUID()
        val output = mutableListOf<String>()
        lateinit var gate: StadiumOutputGate
        gate = StadiumOutputGate { _, message ->
            output.add(message)
            if (message == "first") gate.receive(id, "reentrant")
        }
        gate.begin(id)
        gate.receive(id, "first")
        gate.receive(id, "second")
        gate.commit(id)
        assertEquals(listOf("first", "second", "reentrant"), output)
    }
}
