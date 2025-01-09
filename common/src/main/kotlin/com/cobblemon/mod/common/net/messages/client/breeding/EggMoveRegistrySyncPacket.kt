package com.cobblemon.mod.common.net.messages.client.breeding

import com.cobblemon.mod.common.api.breeding.EggMove
import com.cobblemon.mod.common.api.breeding.EggMoves
import com.cobblemon.mod.common.net.messages.client.data.DataRegistrySyncPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

class EggMoveRegistrySyncPacket(eggMoves: List<EggMove>) :
        DataRegistrySyncPacket<EggMove, EggMoveRegistrySyncPacket>(eggMoves) {

    companion object {
        val ID = cobblemonResource("egg_moves")
        fun decode(buffer: RegistryFriendlyByteBuf) =
                EggMoveRegistrySyncPacket(emptyList()).apply { decodeBuffer(buffer) }
    }

    override val id = ID

    override fun encodeEntry(buffer: RegistryFriendlyByteBuf, entry: EggMove) {
        buffer.writeResourceLocation(entry.baby)

        // Write Parent1 Rules
        encodeParentRules(buffer, entry.parent1rules)

        // Write Parent2 Rules
        encodeParentRules(buffer, entry.parent2rules)

        buffer.writeResourceLocation(entry.eggMove)
    }

    override fun decodeEntry(buffer: RegistryFriendlyByteBuf): EggMove {
        val baby = buffer.readResourceLocation()

        // Read Parent1 Rules
        val parent1rules = decodeParentRules(buffer)

        // Read Parent2 Rules
        val parent2rules = decodeParentRules(buffer)

        val eggMove = buffer.readResourceLocation()

        return EggMove(baby, parent1rules, parent2rules, eggMove)
    }

    private fun encodeParentRules(buffer: RegistryFriendlyByteBuf, rules: EggMove.ParentRules) {
        buffer.writeResourceLocation(rules.species)
        buffer.writeUtf(rules.gender)
        buffer.writeNullableIdentifier(rules.hasMove)
        buffer.writeNullableIdentifier(rules.hasItem)
    }

    private fun decodeParentRules(buffer: RegistryFriendlyByteBuf): EggMove.ParentRules {
        val species = buffer.readResourceLocation()
        val gender = buffer.readUtf()
        val hasMove = buffer.readNullableIdentifier()
        val hasItem = buffer.readNullableIdentifier()

        return EggMove.ParentRules(species, gender, hasMove, hasItem)
    }

    override fun synchronizeDecoded(entries: Collection<EggMove>) {
        EggMoves.reload(entries.associateBy { it.baby })
    }
}

// Extension functions for optional ResourceLocation handling
fun RegistryFriendlyByteBuf.writeNullableIdentifier(identifier: ResourceLocation?) {
    if (identifier == null) {
        writeBoolean(false)
    } else {
        writeBoolean(true)
        writeResourceLocation(identifier)
    }
}

fun RegistryFriendlyByteBuf.readNullableIdentifier(): ResourceLocation? {
    return if (readBoolean()) readResourceLocation() else null
}