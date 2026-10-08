/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.data

import com.cobblemon.mod.common.api.pokedex.entry.DexEntries
import com.cobblemon.mod.common.api.pokedex.entry.DexEvolutionDisplay
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.readIdentifier
import com.cobblemon.mod.common.util.readString
import com.cobblemon.mod.common.util.readText
import com.cobblemon.mod.common.util.writeIdentifier
import com.cobblemon.mod.common.util.writeString
import com.cobblemon.mod.common.util.writeText
import com.google.common.collect.Sets
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.RegistryFriendlyByteBuf

class DexEvolutionDisplaySyncPacket(entries: MutableList<DexEvolutionDisplay>)
    : DataRegistrySyncPacket<DexEvolutionDisplay, DexEvolutionDisplaySyncPacket>(entries) {

    override val id = ID

    override fun encodeEntry(buffer: RegistryFriendlyByteBuf, entry: DexEvolutionDisplay) {
        buffer.writeIdentifier(entry.fromSpecies)
        buffer.writeString(entry.fromForm)
        buffer.writeCollection(entry.fromAspects) { _, aspect -> buffer.writeString(aspect) }
        buffer.writeIdentifier(entry.toSpecies)
        buffer.writeString(entry.toForm)
        buffer.writeCollection(entry.toAspects) { _, aspect -> buffer.writeString(aspect) }
        buffer.writeCollection(entry.icons) { _, icon ->
            buffer.writeIdentifier(BuiltInRegistries.ITEM.getKey(icon))
        }
        buffer.writeCollection(entry.toolTips) { _, toolTip -> buffer.writeText(toolTip) }
    }

    override fun decodeEntry(buffer: RegistryFriendlyByteBuf): DexEvolutionDisplay? {
        val fromSpecies = buffer.readIdentifier()
        val fromForm = buffer.readString()
        val fromAspects = buffer.readCollection(Sets::newHashSetWithExpectedSize) { buffer.readString() }
        val toSpecies = buffer.readIdentifier()
        val toForm = buffer.readString()
        val toAspects = buffer.readCollection(Sets::newHashSetWithExpectedSize) { buffer.readString() }
        val icons = buffer.readCollection(Sets::newHashSetWithExpectedSize) { BuiltInRegistries.ITEM.get(buffer.readIdentifier()) }.toList()
        val toolTips = buffer.readCollection(Sets::newHashSetWithExpectedSize) { buffer.readText() }.toList()
        return DexEvolutionDisplay(fromSpecies, fromForm, fromAspects, toSpecies, toForm, toAspects, icons, toolTips)
    }

    override fun synchronizeDecoded(entries: Collection<DexEvolutionDisplay>) {
        DexEntries.evolutionDisplays.clear()
        DexEntries.evolutionDisplays.addAll(entries)
    }

    companion object {
        val ID = cobblemonResource("dex_evolution_display_sync")
        fun decode(buffer: RegistryFriendlyByteBuf): DexEvolutionDisplaySyncPacket =
            DexEvolutionDisplaySyncPacket(mutableListOf()).apply { decodeBuffer(buffer) }
    }
}