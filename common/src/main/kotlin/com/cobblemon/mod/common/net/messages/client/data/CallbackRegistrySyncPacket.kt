/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.data

import com.cobblemon.mod.common.CobblemonCallbacks
import com.cobblemon.mod.common.api.molang.ExpressionLike
import com.cobblemon.mod.common.util.asExpressionLike
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.readIdentifier
import com.cobblemon.mod.common.util.readString
import com.cobblemon.mod.common.util.writeIdentifier
import com.cobblemon.mod.common.util.writeString
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.Identifier

/**
 * A packet that synchronizes the callback registry with the client.
 *
 * @author Hiroku
 * @since February 24th, 2024
 */
class CallbackRegistrySyncPacket(entries: Collection<Map.Entry<Identifier, List<ExpressionLike>>>) : DataRegistrySyncPacket<Map.Entry<Identifier, List<ExpressionLike>>, CallbackRegistrySyncPacket>(entries){
    companion object {
        val ID = cobblemonResource("callback_registry_sync")
        fun decode(buffer: RegistryFriendlyByteBuf): CallbackRegistrySyncPacket = CallbackRegistrySyncPacket(emptyList()).apply { decodeBuffer(buffer) }
    }

    override val id = ID

    override fun encodeEntry(buffer: RegistryFriendlyByteBuf, entry: Map.Entry<Identifier, List<ExpressionLike>>) {
        buffer.writeIdentifier(entry.key)
        buffer.writeCollection(entry.value) { _, expression -> buffer.writeString(expression.toString()) }
    }

    override fun decodeEntry(buffer: RegistryFriendlyByteBuf): Map.Entry<Identifier, List<ExpressionLike>> {
        val key = buffer.readIdentifier()
        val value = buffer.readList { buffer.readString().asExpressionLike() }
        return object : Map.Entry<Identifier, List<ExpressionLike>> {
            override val key = key
            override val value = value
        }
    }

    override fun synchronizeDecoded(entries: Collection<Map.Entry<Identifier, List<ExpressionLike>>>) {
        entries.map { (identifier, callbacks) ->
            val existing = CobblemonCallbacks.clientCallbacks.getOrPut(identifier) { mutableListOf() }
            existing += callbacks
        }
    }
}
