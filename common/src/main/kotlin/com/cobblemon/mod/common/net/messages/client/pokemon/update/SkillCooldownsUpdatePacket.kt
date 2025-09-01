/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.pokemon.update

import com.cobblemon.mod.common.net.IntSize
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.readSizedInt
import com.cobblemon.mod.common.util.writeSizedInt
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

/**
 * Packet that adds a single skill cooldown to a Pokémon's skill cooldowns map.
 *
 * @author Hiroku
 * @since August 31st, 2025
 */
class SkillCooldownsUpdatePacket(
    pokemon: () -> Pokemon?,
    val skillId: ResourceLocation,
    val cooldownTicks: Int
) : SingleUpdatePacket<Pair<ResourceLocation, Int>, SkillCooldownsUpdatePacket>(pokemon, skillId to cooldownTicks) {
    override val id: ResourceLocation = ID

    override fun encodeValue(buffer: RegistryFriendlyByteBuf) {
        buffer.writeResourceLocation(skillId)
        buffer.writeSizedInt(IntSize.U_SHORT, cooldownTicks)
    }

    override fun set(pokemon: Pokemon, value: Pair<ResourceLocation, Int>) {
        val (skillId, cooldownTicks) = value
        pokemon.skillCooldownsTicks[skillId] = cooldownTicks
    }

    companion object {
        val ID = cobblemonResource("skill_cooldowns_update")
        fun decode(buffer: RegistryFriendlyByteBuf): SkillCooldownsUpdatePacket {
            val pokemon = decodePokemon(buffer)
            val skillId = buffer.readResourceLocation()
            val cooldownTicks = buffer.readSizedInt(IntSize.U_SHORT)
            return SkillCooldownsUpdatePacket(pokemon, skillId, cooldownTicks)
        }
    }
}