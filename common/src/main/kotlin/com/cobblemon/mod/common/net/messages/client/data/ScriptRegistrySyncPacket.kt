/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.data

import com.cobblemon.mod.common.api.molang.ExpressionLike
import com.cobblemon.mod.common.api.scripting.CobblemonScripts
import com.cobblemon.mod.common.util.*
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.Identifier

class ScriptRegistrySyncPacket(entries: Collection<Map.Entry<Identifier, ExpressionLike>>) : DataRegistrySyncPacket<Map.Entry<Identifier, ExpressionLike>, ScriptRegistrySyncPacket>(entries){
    companion object {
        val ID = cobblemonResource("script_registry_sync")
        fun decode(buffer: RegistryFriendlyByteBuf): ScriptRegistrySyncPacket = ScriptRegistrySyncPacket(emptyList()).apply { decodeBuffer(buffer) }
    }

    override fun encodeEntry(buffer: RegistryFriendlyByteBuf, entry: Map.Entry<Identifier, ExpressionLike>) {
        buffer.writeIdentifier(entry.key)
        buffer.writeString(entry.value.toString())
    }

    override fun decodeEntry(buffer: RegistryFriendlyByteBuf): Map.Entry<Identifier, ExpressionLike> {
        val key = buffer.readIdentifier()
        val value = buffer.readString().asExpressionLike()
        return object : Map.Entry<Identifier, ExpressionLike> {
            override val key = key
            override val value = value
        }
    }

    override fun synchronizeDecoded(entries: Collection<Map.Entry<Identifier, ExpressionLike>>) {
        CobblemonScripts.scripts.putAll(entries.map { it.key to it.value })
    }

    override val id = ID
}
