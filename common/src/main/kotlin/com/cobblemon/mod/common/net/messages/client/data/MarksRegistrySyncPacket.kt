package com.cobblemon.mod.common.net.messages.client.data

import com.cobblemon.mod.common.api.pokemon.marks.PokemonMark
import com.cobblemon.mod.common.api.pokemon.marks.PokemonMarks
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.readIdentifier
import com.cobblemon.mod.common.util.readString
import com.cobblemon.mod.common.util.writeIdentifier
import com.cobblemon.mod.common.util.writeString
import net.minecraft.network.RegistryFriendlyByteBuf

class MarksRegistrySyncPacket(marks: List<PokemonMark>): DataRegistrySyncPacket<PokemonMark, MarksRegistrySyncPacket>(marks) {

    companion object {
        val ID = cobblemonResource("marks")
        fun decode(buffer: RegistryFriendlyByteBuf) = MarksRegistrySyncPacket(emptyList()).apply { decodeBuffer(buffer) }
    }

    override val id = ID
    override fun encodeEntry(buffer: RegistryFriendlyByteBuf, entry: PokemonMark) {
        buffer.writeIdentifier(entry.identifier)
        buffer.writeIdentifier(entry.icon)
        buffer.writeString(entry.title)
        buffer.writeString(entry.description)
        buffer.writeFloat(entry.chance)
    }

    override fun decodeEntry(buffer: RegistryFriendlyByteBuf): PokemonMark {
        return PokemonMark(
            id = buffer.readIdentifier(),
            icon = buffer.readIdentifier(),
            title = buffer.readString(),
            description = buffer.readString(),
            chance = buffer.readFloat()
        )
    }

    override fun synchronizeDecoded(entries: Collection<PokemonMark>) {
        PokemonMarks.reload(entries.associateBy { it.identifier })
    }

}
