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

object PCMoLangFunctions : AbstractMoLangFunctionHolder<PCStore>() {
    override fun PCStore.moLangFunctions(): MutableMap<String, (MoParams) -> Any> {
        val pc = this
        val functions = hashMapOf<String, (MoParams) -> Any>()

        functions["get_pokemon"] = getPokemon@{ params ->
            val box = params.getInt(0)
            val slot = params.getInt(1)
            val pokemon = pc[PCPosition(box, slot)] ?: return@getPokemon DoubleValue.ZERO
            pokemon.struct
        }
        functions["set_pokemon"] = { params ->
            val box = params.getInt(0)
            val slot = params.getInt(1)
            val pokemon = params.get<ObjectValue<Pokemon>>(2).obj
            pc[PCPosition(box, slot)] = pokemon
            DoubleValue.ONE
        }
        functions["resize"] = { params ->
            val newSize = params.getInt(0)
            val lockNewSize = params.getBooleanOrNull(1) == true
            pc.resize(newSize, lockNewSize)
            DoubleValue.ONE
        }
        functions["get_box_count"] = { DoubleValue(pc.boxes.size.toDouble()) }
        functions["has_unlocked_wallpaper"] = { params ->
            val wallpaper = params.getString(0).asIdentifierDefaultingNamespace()
            DoubleValue(pc.unlockedWallpapers.contains(wallpaper))
        }
        functions["get_unlocked_wallpapers"] = { pc.unlockedWallpapers.asArrayValue { StringValue(it.toString()) } }
        functions["unlock_wallpaper"] = unlockWallpaper@{
            val wallpaper = it.getString(0).asIdentifierDefaultingNamespace()
            val playSound = it.getBooleanOrNull(1) != false
            CobblemonUnlockableWallpapers.unlockableWallpapers[wallpaper] ?: return@unlockWallpaper DoubleValue.ZERO
            DoubleValue(pc.unlockWallpaper(wallpaper, playSound))
        }

        return functions
    }
}
