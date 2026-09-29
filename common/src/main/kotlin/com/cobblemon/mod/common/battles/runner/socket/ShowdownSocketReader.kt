/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

/*
 * Copyright (C) 2026 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.runner.socket

import java.io.EOFException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

class ShowdownSocketReader(private val input: InputStream) {
    private fun bytes(size: Int): ByteArray {
        require(size in 0..16777216)
        val bytes = input.readNBytes(size)
        if (bytes.size != size) throw EOFException("Incomplete Showdown response")
        return bytes
    }

    private fun decode(bytes: ByteArray) = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes)).toString()

    fun length(): Int {
        val header = bytes(8).toString(Charsets.US_ASCII)
        require(header.all { it in '0'..'9' })
        return header.toInt().also { require(it <= 16777216) }
    }

    fun json() = decode(bytes(length()))

    fun characters(size: Int): String {
        require(size in 0..16777216)
        val result = StringBuilder()
        while (result.length < size) {
            val first = input.read()
            if (first == -1) throw EOFException("Incomplete Showdown response")
            if (first < 128) {
                result.append(first.toChar())
                continue
            }
            val count = when (first) {
                in 194..223 -> 2
                in 224..239 -> 3
                in 240..244 -> 4
                else -> error("Invalid Showdown UTF-8")
            }
            result.append(decode(byteArrayOf(first.toByte()) + bytes(count - 1)))
        }
        require(result.length == size)
        return result.toString()
    }

    fun message() = characters(length())
    fun messages(): List<String> = List(length().also { require(it <= 4096) }) { message() }
}
