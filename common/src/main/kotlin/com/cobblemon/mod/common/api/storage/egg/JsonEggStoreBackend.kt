/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.storage.egg

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import java.io.File

class JsonEggStoreBackend : FileEggStoreBackend("json") {
    override val ops = JsonOps.INSTANCE
    override fun readFromFile(file: File): Any = file.reader().use { JsonParser.parseReader(it) }
    override fun writeToFile(file: File, data: Any) = file.writeText((data as JsonElement).toString())
}
