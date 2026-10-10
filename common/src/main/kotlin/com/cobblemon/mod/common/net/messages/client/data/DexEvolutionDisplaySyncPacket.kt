/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.data

import com.cobblemon.mod.common.api.pokedex.entry.CobblemonBiomeDexEvolutionRequirementDisplay
import com.cobblemon.mod.common.api.pokedex.entry.CobblemonDexEvolutionRequirementDisplay
import com.cobblemon.mod.common.api.pokedex.entry.DexEntries
import com.cobblemon.mod.common.api.pokedex.entry.DexEvolutionDisplay
import com.cobblemon.mod.common.api.pokedex.entry.DexEvolutionRequirementDisplay
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.readIdentifier
import com.cobblemon.mod.common.util.readNullable
import com.cobblemon.mod.common.util.readString
import com.cobblemon.mod.common.util.readText
import com.cobblemon.mod.common.util.writeIdentifier
import com.cobblemon.mod.common.util.writeNullable
import com.cobblemon.mod.common.util.writeString
import com.cobblemon.mod.common.util.writeText
import com.google.common.collect.Lists
import com.google.common.collect.Sets
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.util.ExtraCodecs

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
        buffer.writeCollection(entry.requirements) { _, req ->
            when (req) {
                is CobblemonBiomeDexEvolutionRequirementDisplay -> {
                    buffer.writeByte(1)
                    buffer.writeIdentifier(BuiltInRegistries.ITEM.getKey(req.icon))
                    buffer.writeText(req.tooltip)
                    buffer.writeNullable(req.condition) { b, cond ->
                        b.writeIdentifier(cond.id)
                        b.writeBoolean(cond.tag)
                    }
                    buffer.writeNullable(req.antiCondition) { b, cond ->
                        b.writeIdentifier(cond.id)
                        b.writeBoolean(cond.tag)
                    }
                }
                else -> {
                    buffer.writeByte(0)
                    buffer.writeIdentifier(BuiltInRegistries.ITEM.getKey(req.icon))
                    buffer.writeText(req.tooltip)
                }
            }
        }
    }

    override fun decodeEntry(buffer: RegistryFriendlyByteBuf): DexEvolutionDisplay? {
        val fromSpecies = buffer.readIdentifier()
        val fromForm = buffer.readString()
        val fromAspects = buffer.readCollection(Sets::newHashSetWithExpectedSize) { buffer.readString() }
        val toSpecies = buffer.readIdentifier()
        val toForm = buffer.readString()
        val toAspects = buffer.readCollection(Sets::newHashSetWithExpectedSize) { buffer.readString() }
        val requirements = buffer.readCollection(Lists::newArrayListWithExpectedSize) {
            val type = buffer.readByte().toInt()
            val icon = BuiltInRegistries.ITEM.get(buffer.readIdentifier())
            val tooltip = buffer.readText().copy()
            if (type == 1) {
                val condition = buffer.readNullable { b ->
                    val id = b.readIdentifier()
                    val tag = b.readBoolean()
                    ExtraCodecs.TagOrElementLocation(id, tag)
                }
                val antiCondition = buffer.readNullable { b ->
                    val id = b.readIdentifier()
                    val tag = b.readBoolean()
                    ExtraCodecs.TagOrElementLocation(id, tag)
                }
                CobblemonBiomeDexEvolutionRequirementDisplay(icon, tooltip, condition, antiCondition)
            } else {
                CobblemonDexEvolutionRequirementDisplay(icon, tooltip)
            }
        }.toList()
        return DexEvolutionDisplay(fromSpecies, fromForm, fromAspects, toSpecies, toForm, toAspects, requirements)
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