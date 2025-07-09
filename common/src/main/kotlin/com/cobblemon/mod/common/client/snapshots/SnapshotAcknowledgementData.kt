/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.snapshots

import com.cobblemon.mod.common.config.CobblemonConfig
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.FileInputStream
import java.io.FileWriter
import java.io.InputStreamReader
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists

object SnapshotAcknowledgementReader {

    val gson = CobblemonConfig.GSON
    val target: Path = Paths.get("cobblemon").resolve("snapshots.json")

    fun read() : SnapshotAcknowledgementData? {
        if (target.exists()) {
            BufferedReader(InputStreamReader(FileInputStream(target.toFile()))).use { reader ->
                return gson.fromJson(reader, SnapshotAcknowledgementData::class.java)
            }
        }

        return null
    }

    fun write(data: SnapshotAcknowledgementData) {
        target.toFile().parentFile.mkdirs()
        BufferedWriter(FileWriter(target.toFile())).use { writer -> gson.toJson(data, writer) }
    }

}

data class SnapshotAcknowledgementData(val version: String, val dontShowAgain: Boolean)