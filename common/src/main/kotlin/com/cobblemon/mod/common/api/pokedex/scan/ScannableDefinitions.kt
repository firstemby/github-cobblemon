package com.cobblemon.mod.common.api.pokedex.scan

import com.cobblemon.mod.common.api.data.JsonDataRegistry
import com.cobblemon.mod.common.api.reactive.SimpleObservable
import com.cobblemon.mod.common.net.messages.client.data.ScannableRegistrySyncPacket
import com.cobblemon.mod.common.util.adapters.IdentifierAdapter
import com.cobblemon.mod.common.util.cobblemonResource
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.packs.PackType
import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item

/**
 * The universal, datapack-driven allow-list of what can be scanned, per bucket. Inclusion-only:
 * an id/tag must be covered here to be scannable (except code-defined providers like berries/eggs,
 * which define their own scanning and bypass this registry). Synced to clients so renderers can
 * resolve scannability locally.
 */
object ScannableDefinitions : JsonDataRegistry<ScannableDefinition> {
    override val id: ResourceLocation = cobblemonResource("scannable_definitions")
    override val type: PackType = PackType.SERVER_DATA
    override val observable = SimpleObservable<ScannableDefinitions>()
    override val typeToken: TypeToken<ScannableDefinition> = TypeToken.get(ScannableDefinition::class.java)
    override val resourcePath: String = "scannable_definitions"
    override val gson = GsonBuilder()
        .registerTypeAdapter(ResourceLocation::class.java, IdentifierAdapter)
        .create()

    private val definitions = mutableListOf<ScannableDefinition>()

    override fun reload(data: Map<ResourceLocation, ScannableDefinition>) {
        setAll(data.values)
    }

    fun receiveSync(entries: Collection<ScannableDefinition>) {
        setAll(entries)
    }

    private fun setAll(entries: Collection<ScannableDefinition>) {
        definitions.clear()
        definitions.addAll(entries)
        observable.emit(this)
    }

    fun all(): List<ScannableDefinition> = definitions.toList()

    override fun sync(player: ServerPlayer) {
        ScannableRegistrySyncPacket(definitions.toList()).sendToPlayer(player)
    }

    /** The bucket an item is scannable in (matched by id or item tag), or null if not scannable. */
    fun itemBucket(item: Item): String? {
        val holder = item.builtInRegistryHolder()
        val itemId = BuiltInRegistries.ITEM.getKey(item)
        for (def in definitions) {
            if (!def.category.equals(ScannedDataCategory.ITEMS.id, ignoreCase = true)) continue
            val matches = itemId in def.entries ||
                def.tags.any { holder.`is`(TagKey.create(Registries.ITEM, it)) }
            if (matches) return def.bucketPath()
        }
        return null
    }

    /** Whether [id] is explicitly allow-listed in [bucket] (id matching only; for non-item categories). */
    fun isAllowed(bucket: String, id: ResourceLocation): Boolean =
        definitions.any { it.bucketPath() == bucket && id in it.entries }
}
