/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.tms

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.api.tms.ObtainMethod
import com.cobblemon.mod.common.api.tms.TechnicalMachine
import com.cobblemon.mod.common.api.tms.TechnicalMachineRecipe
import com.cobblemon.mod.common.api.tms.TechnicalMachines
import com.cobblemon.mod.common.net.messages.client.data.DataRegistrySyncPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.crafting.Ingredient

class TechnicalMachineRegistrySyncPacket(tms: List<TechnicalMachine>) : DataRegistrySyncPacket<TechnicalMachine, TechnicalMachineRegistrySyncPacket>(tms) {

    companion object {
        val ID = cobblemonResource("technical_machines")

        fun decode(buffer: RegistryFriendlyByteBuf): TechnicalMachineRegistrySyncPacket {
            return TechnicalMachineRegistrySyncPacket(emptyList()).apply { decodeBuffer(buffer) }
        }
    }

    override val id = ID

    override fun encodeEntry(buffer: RegistryFriendlyByteBuf, entry: TechnicalMachine) {
        buffer.writeResourceLocation(entry.id)
        buffer.writeUtf(entry.moveName.name)
        if (entry.recipe != null) {
            buffer.writeBoolean(true)
            buffer.writeVarInt(entry.recipe.size)
            entry.recipe.forEach {
                Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, it.ingredient)
                buffer.writeVarInt(it.count)
            }
        } else {
            buffer.writeBoolean(false)
        }
        buffer.writeVarInt(entry.obtainMethods.size)
        entry.obtainMethods.forEach { it.writeToBuffer(buffer) }
        buffer.writeUtf(entry.type)
    }

    override fun decodeEntry(buffer: RegistryFriendlyByteBuf): TechnicalMachine {
        val id = buffer.readResourceLocation()
        val moveName = buffer.readUtf()

        val recipe = if (buffer.readBoolean()) {
            val recipeCount = buffer.readVarInt()
            List(recipeCount) {
                val ingredient = Ingredient.CONTENTS_STREAM_CODEC.decode(buffer)
                val count = buffer.readVarInt()
                TechnicalMachineRecipe(ingredient, count)
            }
        } else null

        val obtainMethods = List(buffer.readVarInt()) { ObtainMethod.readFromBuffer(buffer) }
        val type = buffer.readUtf()

        val moveTemplate = Moves.getByName(moveName)
        if (moveTemplate == null) {
            Cobblemon.LOGGER.error("MoveTemplate not found for move name: $moveName")
            throw IllegalStateException("MoveTemplate not found for move name: $moveName")
        }

        return TechnicalMachine(moveTemplate, recipe, obtainMethods, type).apply { this.id = id }
    }


    override fun synchronizeDecoded(entries: Collection<TechnicalMachine>) {
        TechnicalMachines.reload(entries.associateBy { it.id })
    }
}
