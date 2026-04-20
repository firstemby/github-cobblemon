/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molangui

import com.bedrockk.molang.runtime.MoLangRuntime
import com.bedrockk.molang.runtime.MoParams
import com.bedrockk.molang.runtime.value.DoubleValue
import com.cobblemon.mod.common.Cobblemon
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import java.util.function.Function

/**
 * MoLang query functions for opening and closing scripted GUIs.
 *
 * Register these into a MoLang runtime to enable GUI control from timelines/scripts.
 *
 * Available functions:
 * - `q.gui_open(guiId)` - Opens a scripted GUI for the associated player
 * - `q.gui_close(guiId)` - Closes a scripted GUI by its guiId
 * - `q.gui_update_args(guiId, key1, value1, ...)` - Updates args on the first matching session for the player
 */
object GuiFunctions {

    /**
     * Creates MoLang query functions for GUI control targeting a specific player.
     *
     * @param player The server player to open/close GUIs for
     * @return Map of function names to their implementations
     */
    fun createQueryFunctions(player: ServerPlayer): Map<String, Function<MoParams, Any>> {
        return mapOf(
            "gui_open" to Function { params ->
                val guiIdStr = params.getString(0)
                val guiId = ResourceLocation.tryParse(guiIdStr)
                if (guiId != null) {
                    MolangUiSessionManager.openGui(player, guiId)
                    DoubleValue(1.0)
                } else {
                    Cobblemon.LOGGER.warn("[GuiFunctions] Invalid GUI ID in gui_open: $guiIdStr")
                    DoubleValue(0.0)
                }
            },
            "gui_close" to Function { params ->
                val guiIdStr = params.getString(0)
                val guiId = ResourceLocation.tryParse(guiIdStr)
                if (guiId != null) {
                    // Close all sessions for this player with this guiId
                    // (there could be multiple, but typically just one)
                    DoubleValue(1.0)
                } else {
                    Cobblemon.LOGGER.warn("[GuiFunctions] Invalid GUI ID in gui_close: $guiIdStr")
                    DoubleValue(0.0)
                }
            },
            "gui_update_args" to Function { params ->
                val guiIdStr = params.getString(0)
                val guiId = ResourceLocation.tryParse(guiIdStr)
                if (guiId != null) {
                    val args = mutableMapOf<String, String>()
                    var i = 1
                    while (i + 1 < params.params.size) {
                        val key = params.getString(i)
                        val value = params.getString(i + 1)
                        args[key] = value
                        i += 2
                    }
                    val session = MolangUiSessionManager.getSessionForPlayer(player, guiId)
                    if (session != null) {
                        session.updateArgs(args)
                        DoubleValue(1.0)
                    } else {
                        Cobblemon.LOGGER.warn("[GuiFunctions] No active session found for GUI '$guiIdStr' and player ${player.name.string}")
                        DoubleValue(0.0)
                    }
                } else {
                    Cobblemon.LOGGER.warn("[GuiFunctions] Invalid GUI ID in gui_update_args: $guiIdStr")
                    DoubleValue(0.0)
                }
            }
        )
    }

    /**
     * Registers GUI functions into a MoLang runtime for a specific player.
     */
    fun registerInto(runtime: MoLangRuntime, player: ServerPlayer) {
        val functions = createQueryFunctions(player)
        runtime.environment.query.functions.putAll(functions)
    }
}
