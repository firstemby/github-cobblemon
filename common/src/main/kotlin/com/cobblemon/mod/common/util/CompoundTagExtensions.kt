/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.util

import com.cobblemon.mod.common.CobblemonEntities
import net.minecraft.nbt.CompoundTag
import net.minecraft.core.UUIDUtil
import java.util.UUID

fun CompoundTag.isPokemonEntity() : Boolean {
    return this.getStringOr("id", "") == CobblemonEntities.POKEMON_KEY.toString()
}

fun CompoundTag.putUUID(key: String, uuid: UUID) {
    putIntArray(key, UUIDUtil.uuidToIntArray(uuid))
}

fun CompoundTag.getUUID(key: String): UUID {
    return getIntArray(key)
        .map(UUIDUtil::uuidFromIntArray)
        .orElseThrow { IllegalArgumentException("Missing UUID tag: $key") }
}

fun CompoundTag.hasUUID(key: String): Boolean {
    return getIntArray(key).isPresent
}
