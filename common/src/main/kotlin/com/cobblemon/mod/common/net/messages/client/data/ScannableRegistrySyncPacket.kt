package com.cobblemon.mod.common.net.messages.client.data

import com.cobblemon.mod.common.api.pokedex.scan.ScannableDefinition
import com.cobblemon.mod.common.api.pokedex.scan.ScannableDefinitions
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.readString
import com.cobblemon.mod.common.util.writeString
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

class ScannableRegistrySyncPacket(definitions: Collection<ScannableDefinition>) :
    DataRegistrySyncPacket<ScannableDefinition, ScannableRegistrySyncPacket>(definitions) {

    override val id = ID

    override fun encodeEntry(buffer: RegistryFriendlyByteBuf, entry: ScannableDefinition) {
        buffer.writeString(entry.category)
        buffer.writeString(entry.subCategory ?: "")
        buffer.writeCollection(entry.entries) { _, rl -> buffer.writeString(rl.toString()) }
        buffer.writeCollection(entry.tags) { _, rl -> buffer.writeString(rl.toString()) }
    }

    override fun decodeEntry(buffer: RegistryFriendlyByteBuf): ScannableDefinition {
        val category = buffer.readString()
        val sub = buffer.readString().ifEmpty { null }
        val entries = buffer.readList { ResourceLocation.parse(buffer.readString()) }
        val tags = buffer.readList { ResourceLocation.parse(buffer.readString()) }
        return ScannableDefinition(category, sub, entries, tags)
    }

    override fun synchronizeDecoded(entries: Collection<ScannableDefinition>) {
        ScannableDefinitions.receiveSync(entries)
    }

    companion object {
        val ID = cobblemonResource("scannable_sync")
        fun decode(buffer: RegistryFriendlyByteBuf) =
            ScannableRegistrySyncPacket(emptyList()).apply { decodeBuffer(buffer) }
    }
}
