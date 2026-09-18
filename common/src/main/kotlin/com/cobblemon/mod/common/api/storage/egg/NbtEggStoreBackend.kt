/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.storage.egg

import java.io.File
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.NbtOps

class NbtEggStoreBackend : FileEggStoreBackend("nbt") {
    override val ops = NbtOps.INSTANCE
    override fun readFromFile(file: File): Any = NbtIo.read(file.toPath())!!
    override fun writeToFile(file: File, data: Any) = NbtIo.write(data as CompoundTag, file.toPath())
}
