package com.cobblemon.mod.common.api.pokedex.scan

import com.cobblemon.mod.common.api.data.JsonDataRegistry
import com.cobblemon.mod.common.api.reactive.SimpleObservable
import com.cobblemon.mod.common.net.messages.client.pokedex.BlockScanRegistrySyncPacket
import com.cobblemon.mod.common.util.adapters.IdentifierAdapter
import com.cobblemon.mod.common.util.cobblemonResource
import com.google.gson.*
import com.google.gson.reflect.TypeToken
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.packs.PackType
import java.lang.reflect.Type

object BlockScanDefinitions : JsonDataRegistry<BlockScanDefinition> {
    override val id: ResourceLocation = cobblemonResource("pokedex_block_scans")
    override val type: PackType = PackType.SERVER_DATA
    override val observable = SimpleObservable<BlockScanDefinitions>()

    override val typeToken: TypeToken<BlockScanDefinition> =
        TypeToken.get(BlockScanDefinition::class.java)

    override val resourcePath: String = "pokedex_scans/block_entities"

    override val gson: Gson = GsonBuilder()
        .registerTypeAdapter(ResourceLocation::class.java, IdentifierAdapter)
        .registerTypeAdapter(BlockScanDefinition.ScanText::class.java, ScanTextAdapter)
        .setPrettyPrinting()
        .create()

    private val byType = mutableMapOf<ResourceLocation, BlockScanDefinition>()

    override fun sync(player: ServerPlayer) {
        BlockScanRegistrySyncPacket(byType.values.toList()).sendToPlayer(player)
    }

    override fun reload(data: Map<ResourceLocation, BlockScanDefinition>) {
        byType.clear()
        // Index by the actual targeted block entity type id in the json
        data.values.forEach { def ->
            byType[def.blockEntityType] = def
        }
    }

    fun reloadEntries(entries: Collection<BlockScanDefinition>) {
        byType.clear()
        entries.forEach { def ->
            byType[def.blockEntityType] = def
        }
    }

    fun get(typeId: ResourceLocation): BlockScanDefinition? = byType[typeId]

    private object ScanTextAdapter :
        JsonDeserializer<BlockScanDefinition.ScanText>,
        JsonSerializer<BlockScanDefinition.ScanText> {

        private val SCAN_TEXT_TYPE: Type =
            object : TypeToken<BlockScanDefinition.ScanText>() {}.type

        override fun deserialize(
            json: JsonElement,
            typeOfT: Type,
            ctx: JsonDeserializationContext
        ): BlockScanDefinition.ScanText {

            if (json.isJsonPrimitive && json.asJsonPrimitive.isString) {
                return BlockScanDefinition.ScanText.Literal(json.asString)
            }

            if (!json.isJsonObject) {
                throw JsonParseException("ScanText must be a string or an object")
            }

            val obj = json.asJsonObject

            if (obj.has("literal")) return BlockScanDefinition.ScanText.Literal(obj["literal"].asString)
            if (obj.has("key")) return BlockScanDefinition.ScanText.Key(obj["key"].asString)

            if (obj.has("translate")) {
                val key = obj["translate"].asString
                val with: List<BlockScanDefinition.ScanText> =
                    if (obj.has("with")) obj.getAsJsonArray("with").map { el ->
                        ctx.deserialize<BlockScanDefinition.ScanText>(el, SCAN_TEXT_TYPE)
                    } else emptyList()
                return BlockScanDefinition.ScanText.Translate(key, with)
            }

            throw JsonParseException("ScanText object must contain one of: literal, key, translate")
        }

        override fun serialize(
            src: BlockScanDefinition.ScanText,
            typeOfSrc: Type,
            ctx: JsonSerializationContext
        ): JsonElement = when (src) {
            is BlockScanDefinition.ScanText.Literal -> JsonPrimitive(src.text)

            is BlockScanDefinition.ScanText.Key ->
                JsonObject().apply { addProperty("key", src.key) }

            is BlockScanDefinition.ScanText.Translate ->
                JsonObject().apply {
                    addProperty("translate", src.key)
                    if (src.with.isNotEmpty()) {
                        add("with", JsonArray().apply {
                            src.with.forEach { st ->
                                add(ctx.serialize(st, SCAN_TEXT_TYPE))
                            }
                        })
                    }
                }
        }
    }
}
