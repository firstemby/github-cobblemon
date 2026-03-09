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
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMoLangValue
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asWorldMoLangValue
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.util.asIdentifierDefaultingNamespace
import com.cobblemon.mod.common.util.getBooleanOrNull
import com.cobblemon.mod.common.util.getStringOrNull
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import java.util.UUID
import java.util.function.Function

object ServerMoLangFunctions {
    val holder: MutableList<(MinecraftServer) -> HashMap<String, (MoParams) -> Any>> = mutableListOf(
        { server ->
            val map = hashMapOf<String, (MoParams) -> Any>()

            map.put("get_world") { params ->
                val world = server.getLevel(
                    ResourceKey.create(
                        Registries.DIMENSION,
                        params.getString(0).asIdentifierDefaultingNamespace(namespace = "minecraft")
                    )
                ) ?: return@put DoubleValue.ZERO

                return@put world
                    .registryAccess()
                    .registryOrThrow(Registries.DIMENSION)
                    .wrapAsHolder(world)
                    .asWorldMoLangValue()
            }

            map.put("broadcast") { params ->
                val message = params.getString(0)
                server.playerList.broadcastSystemMessage(message.text(), params.getBooleanOrNull(1) == true)
                return@put DoubleValue.ONE
            }

            map.put("get_player_by_uuid") { params ->
                val uuid = UUID.fromString(params.getString(0))
                val player = server.playerList.getPlayer(uuid) ?: return@put DoubleValue.ZERO
                return@put player.asMoLangValue()
            }

            map.put("get_player_by_username") { params ->
                val username = params.getString(0)
                val player = server.playerList.getPlayerByName(username) ?: return@put DoubleValue.ZERO
                return@put player.asMoLangValue()
            }

            map.put("data") { params ->
                Cobblemon.molangData.load(
                    UUID(0L, 0L),
                    params.getStringOrNull(0)
                )
            }

            map.put("save_data") { params ->
                Cobblemon.molangData.save(
                    UUID(0L, 0L),
                    params.getStringOrNull(index = 0)
                )
                return@put DoubleValue.ONE
            }

            // Maybe later...
//            map.put("stop") { params ->
//                Cobblemon.LOGGER.warn("Server is being stopped from a MoLang script.")
//                server.stopServer()
//            }

            return@mutableListOf map
        }
    )
}
