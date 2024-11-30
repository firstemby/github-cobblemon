package com.cobblemon.mod.common.api.pokemon.marks

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.data.JsonDataRegistry
import com.cobblemon.mod.common.api.reactive.SimpleObservable
import com.cobblemon.mod.common.net.messages.client.data.MarksRegistrySyncPacket
import com.cobblemon.mod.common.util.adapters.IdentifierAdapter
import com.cobblemon.mod.common.util.cobblemonResource
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.packs.PackType

object PokemonMarks: JsonDataRegistry<PokemonMark> {

    override val id = cobblemonResource("marks")
    override val type = PackType.SERVER_DATA
    override val observable = SimpleObservable<PokemonMarks>()

    override val gson = GsonBuilder()
        .registerTypeAdapter(ResourceLocation::class.java, IdentifierAdapter)
        .create()

    override val typeToken = TypeToken.get(PokemonMark::class.java)
    override val resourcePath = "marks"

    val marks = hashMapOf<ResourceLocation, PokemonMark>()

    override fun reload(data: Map<ResourceLocation, PokemonMark>) {
        this.marks.clear()
        data.forEach { id, mark ->
            try {
                mark.identifier = id
                this.marks[id] = mark
            } catch(e: Exception) {
                Cobblemon.LOGGER.error("Failed to load mark: {}", id, e)
            }
        }
        Cobblemon.LOGGER.info("Loaded {} marks", this.marks.size)
        this.observable.emit(this)
    }

    override fun sync(player: ServerPlayer) {
        MarksRegistrySyncPacket(all()).sendToPlayer(player)
    }

    fun all() = this.marks.values.toList()
}
