/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molang.function

import com.bedrockk.molang.runtime.MoParams
import com.bedrockk.molang.runtime.value.DoubleValue
import com.bedrockk.molang.runtime.value.StringValue
import com.cobblemon.mod.common.CobblemonUnlockableWallpapers
import com.cobblemon.mod.common.api.molang.ObjectValue
import com.cobblemon.mod.common.api.storage.pc.PCPosition
import com.cobblemon.mod.common.api.storage.pc.PCStore
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.asArrayValue
import com.cobblemon.mod.common.util.asIdentifierDefaultingNamespace
import com.cobblemon.mod.common.util.getBooleanOrNull
import java.util.HashMap
import java.util.function.Function

object PCMoLangFunctions {
    val holder = mutableListOf<(PCStore) -> HashMap<String, (MoParams) -> Any>>(
        { pc ->
            val map = hashMapOf<String, (MoParams) -> Any>()
            map.put("get_pokemon") { params ->
                val box = params.getInt(0)
                val slot = params.getInt(1)
                val pokemon = pc[PCPosition(box, slot)] ?: return@put DoubleValue.ZERO
                return@put pokemon.struct
            }
            map.put("set_pokemon") { params ->
                val box = params.getInt(0)
                val slot = params.getInt(1)
                val pokemon = params.get<ObjectValue<Pokemon>>(2).obj
                pc.set(PCPosition(box, slot), pokemon)
                return@put DoubleValue.ONE
            }
            map.put("resize") { params ->
                val newSize = params.getInt(0)
                val lockNewSize = params.getBooleanOrNull(1) == true
                pc.resize(newSize, lockNewSize)
                return@put DoubleValue.ONE
            }
            map.put("get_box_count") { _ -> DoubleValue(pc.boxes.size.toDouble()) }
            map.put("has_unlocked_wallpaper") { params ->
                val wallpaper = params.getString(0).asIdentifierDefaultingNamespace()
                return@put DoubleValue(pc.unlockedWallpapers.contains(wallpaper))
            }
            map.put("get_unlocked_wallpapers") { pc.unlockedWallpapers.asArrayValue { StringValue(it.toString()) } }
            map.put("unlock_wallpaper") {
                val wallpaper = it.getString(0).asIdentifierDefaultingNamespace()
                val playSound = it.getBooleanOrNull(1) != false
                CobblemonUnlockableWallpapers.unlockableWallpapers[wallpaper] ?: return@put DoubleValue.ZERO
                return@put DoubleValue(pc.unlockWallpaper(wallpaper, playSound))
            }
            return@mutableListOf map
        }
    )
}
