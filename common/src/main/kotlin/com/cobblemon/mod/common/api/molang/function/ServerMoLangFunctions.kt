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

object ServerMoLangFunctions : AbstractMoLangFunctionHolder<MinecraftServer>() {
    override fun MinecraftServer.moLangFunctions(): MutableMap<String, (MoParams) -> Any> {
        val server = this
        val map = mutableMapOf<String, (MoParams) -> Any>()

        map["get_world"] = getWorld@{ params ->
            val world = server.getLevel(
                ResourceKey.create(
                    Registries.DIMENSION,
                    params.getString(0).asIdentifierDefaultingNamespace(namespace = "minecraft")
                )
            ) ?: return@getWorld DoubleValue.ZERO

            return@getWorld world
                .registryAccess()
                .registryOrThrow(Registries.DIMENSION)
                .wrapAsHolder(world)
                .asWorldMoLangValue()
        }

        map["broadcast"] = { params ->
            val message = params.getString(0)
            server.playerList.broadcastSystemMessage(message.text(), params.getBooleanOrNull(1) == true)
            DoubleValue.ONE
        }

        map["get_player_by_uuid"] = getPlayerByUuid@{ params ->
            val uuid = UUID.fromString(params.getString(0))
            val player = server.playerList.getPlayer(uuid) ?: return@getPlayerByUuid DoubleValue.ZERO
            return@getPlayerByUuid player.asMoLangValue()
        }

        map["get_player_by_username"] = getPlayerByUsername@{ params ->
            val username = params.getString(0)
            val player = server.playerList.getPlayerByName(username) ?: return@getPlayerByUsername DoubleValue.ZERO
            return@getPlayerByUsername player.asMoLangValue()
        }

        map["data"] = { params ->
            Cobblemon.molangData.load(
                UUID(0L, 0L),
                params.getStringOrNull(0)
            )
        }

        map["save_data"] = { params ->
            Cobblemon.molangData.save(
                UUID(0L, 0L),
                params.getStringOrNull(index = 0)
            )
            DoubleValue.ONE
        }

        // Maybe later...
//            map.put("stop") { params ->
//                Cobblemon.LOGGER.warn("Server is being stopped from a MoLang script.")
//                server.stopServer()
//            }

        return map
    }
}
