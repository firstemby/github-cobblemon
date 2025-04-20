/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.breeding

import com.cobblemon.mod.common.api.breeding.BreedingRule
import com.cobblemon.mod.common.api.breeding.BreedingRules
import com.cobblemon.mod.common.net.messages.client.data.DataRegistrySyncPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

class BreedingRuleRegistrySyncPacket(breedingRules: List<BreedingRule>) :
        DataRegistrySyncPacket<BreedingRule, BreedingRuleRegistrySyncPacket>(breedingRules) {

    companion object {
        val ID = cobblemonResource("breeding_rules")

        fun decode(buffer: RegistryFriendlyByteBuf) =
                BreedingRuleRegistrySyncPacket(emptyList()).apply {
                    decodeBuffer(buffer)
                }
    }

    override val id = ID

    // ---------------------------------------------------------------------------------------------
    // ENCODE
    // ---------------------------------------------------------------------------------------------
    override fun encodeEntry(buffer: RegistryFriendlyByteBuf, entry: BreedingRule) {
        // Encode parent1rules
        encodeParentRules(buffer, entry.parent1rules)

        // Encode parent2rules
        encodeParentRules(buffer, entry.parent2rules)

        // Baby data (all strings now)
        buffer.writeNullableString(entry.species)
        buffer.writeNullableString(entry.form)
        buffer.writeNullableString(entry.nature)
        buffer.writeNullableString(entry.ability)

        // Encode moves
        encodeMoves(buffer, entry.moves)

        // Encode ivs
        encodeIvs(buffer, entry.ivs)

        // Encode gender (string)
        buffer.writeNullableString(entry.gender)

        // Encode pokeball (string)
        buffer.writeNullableString(entry.pokeball)
    }

    private fun encodeParentRules(
            buffer: RegistryFriendlyByteBuf,
            rules: BreedingRule.ParentRules
    ) {
        // species, form, ability, gender, hasMove => strings
        buffer.writeNullableString(rules.species)
        buffer.writeNullableString(rules.form)
        buffer.writeNullableString(rules.ability)
        buffer.writeNullableString(rules.gender)
        buffer.writeNullableString(rules.hasMove)

        // hasItem => ResourceLocation?
        buffer.writeNullableIdentifier(rules.hasItem)
    }

    private fun encodeMoves(buffer: RegistryFriendlyByteBuf, moves: BreedingRule.Moves) {
        // move1..move4 => strings
        buffer.writeNullableString(moves.move1)
        buffer.writeNullableString(moves.move2)
        buffer.writeNullableString(moves.move3)
        buffer.writeNullableString(moves.move4)
    }

    private fun encodeIvs(buffer: RegistryFriendlyByteBuf, ivs: BreedingRule.Ivs) {
        buffer.writeNullableInt(ivs.hp)
        buffer.writeNullableInt(ivs.attack)
        buffer.writeNullableInt(ivs.defence)
        buffer.writeNullableInt(ivs.special_attack)
        buffer.writeNullableInt(ivs.special_defence)
        buffer.writeNullableInt(ivs.speed)
    }

    // ---------------------------------------------------------------------------------------------
    // DECODE
    // ---------------------------------------------------------------------------------------------
    override fun decodeEntry(buffer: RegistryFriendlyByteBuf): BreedingRule {
        // Decode parent1rules
        val parent1rules = decodeParentRules(buffer)
        // Decode parent2rules
        val parent2rules = decodeParentRules(buffer)

        // Baby data
        val species = buffer.readNullableString()
        val form = buffer.readNullableString()
        val nature = buffer.readNullableString()
        val ability = buffer.readNullableString()

        // Moves
        val moves = decodeMoves(buffer)

        // IVs
        val ivs = decodeIvs(buffer)

        // Gender (string)
        val gender = buffer.readNullableString()

        // Pokeball (string)
        val pokeball = buffer.readNullableString()

        return BreedingRule(
                parent1rules = parent1rules,
                parent2rules = parent2rules,
                species = species,
                form = form,
                nature = nature,
                ability = ability,
                moves = moves,
                ivs = ivs,
                gender = gender,
                pokeball = pokeball
        )
    }

    private fun decodeParentRules(buffer: RegistryFriendlyByteBuf): BreedingRule.ParentRules {
        val species = buffer.readNullableString()
        val form = buffer.readNullableString()
        val ability = buffer.readNullableString()
        val gender = buffer.readNullableString()
        val hasMove = buffer.readNullableString()
        val hasItem = buffer.readNullableIdentifier()

        return BreedingRule.ParentRules(
                species = species,
                form = form,
                ability = ability,
                gender = gender,
                hasMove = hasMove,
                hasItem = hasItem
        )
    }

    private fun decodeMoves(buffer: RegistryFriendlyByteBuf): BreedingRule.Moves {
        return BreedingRule.Moves(
                move1 = buffer.readNullableString(),
                move2 = buffer.readNullableString(),
                move3 = buffer.readNullableString(),
                move4 = buffer.readNullableString()
        )
    }

    private fun decodeIvs(buffer: RegistryFriendlyByteBuf): BreedingRule.Ivs {
        return BreedingRule.Ivs(
                hp = buffer.readNullableInt(),
                attack = buffer.readNullableInt(),
                defence = buffer.readNullableInt(),
                special_attack = buffer.readNullableInt(),
                special_defence = buffer.readNullableInt(),
                speed = buffer.readNullableInt()
        )
    }

    // ---------------------------------------------------------------------------------------------
    // REGISTRY SYNC
    // ---------------------------------------------------------------------------------------------
    override fun synchronizeDecoded(entries: Collection<BreedingRule>) {
        // Filter out entries that do not specify a species
        val validEntries = entries.filter { it.species != null }

        val data = validEntries.associateBy { rule ->
            cobblemonResource(rule.species!!.lowercase())
        }

        // Now data is a Map<ResourceLocation, BreedingRule>
        BreedingRules.reload(data)
    }


}

fun RegistryFriendlyByteBuf.writeNullableIdentifier(identifier: ResourceLocation?) {
    if (identifier == null) {
        writeBoolean(false)
    } else {
        writeBoolean(true)
        writeResourceLocation(identifier)
    }
}

fun RegistryFriendlyByteBuf.readNullableIdentifier(): ResourceLocation? {
    return if (readBoolean()) {
        readResourceLocation()
    } else {
        null
    }
}

fun RegistryFriendlyByteBuf.writeNullableString(value: String?) {
    if (value == null) {
        writeBoolean(false)
    } else {
        writeBoolean(true)
        writeUtf(value)
    }
}

fun RegistryFriendlyByteBuf.readNullableString(): String? {
    return if (readBoolean()) {
        readUtf()
    } else {
        null
    }
}

fun RegistryFriendlyByteBuf.writeNullableInt(value: Int?) {
    if (value == null) {
        writeBoolean(false)
    } else {
        writeBoolean(true)
        writeVarInt(value)
    }
}

fun RegistryFriendlyByteBuf.readNullableInt(): Int? {
    return if (readBoolean()) {
        readVarInt()
    } else {
        null
    }
}
