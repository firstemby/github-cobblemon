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
import java.util.*

object ServerMoLangFunctions : AbstractMoLangFunctionHolder<MinecraftServer> {
    override val holder: MutableList<(MinecraftServer) -> MutableMap<String, (MoParams) -> Any>> = mutableListOf(
        { server ->
            val map = hashMapOf<String, (MoParams) -> Any>()

            map["get_world"] = put@{ params ->
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

            map["broadcast"] = put@{ params ->
                val message = params.getString(0)
                server.playerList.broadcastSystemMessage(message.text(), params.getBooleanOrNull(1) == true)
                return@put DoubleValue.ONE
            }

            map["get_player_by_uuid"] = put@{ params ->
                val uuid = UUID.fromString(params.getString(0))
                val player = server.playerList.getPlayer(uuid) ?: return@put DoubleValue.ZERO
                return@put player.asMoLangValue()
            }

            map["get_player_by_username"] = put@{ params ->
                val username = params.getString(0)
                val player = server.playerList.getPlayerByName(username) ?: return@put DoubleValue.ZERO
                return@put player.asMoLangValue()
            }

            map["data"] = { params ->
                Cobblemon.molangData.load(
                    UUID(0L, 0L),
                    params.getStringOrNull(0)
                )
            }

            map["save_data"] = put@{ params ->
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
