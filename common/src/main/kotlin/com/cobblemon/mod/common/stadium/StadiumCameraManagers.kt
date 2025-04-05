package com.cobblemon.mod.common.stadium

import com.bedrockk.molang.Expression
import com.cobblemon.mod.common.api.data.JsonDataRegistry
import com.cobblemon.mod.common.api.reactive.SimpleObservable
import com.cobblemon.mod.common.util.adapters.ExpressionAdapter
import com.cobblemon.mod.common.util.adapters.IdentifierAdapter
import com.cobblemon.mod.common.util.cobblemonResource
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.packs.PackType

object StadiumCameraManagers: JsonDataRegistry<StadiumCameraManagerEntry> {
    override val id = cobblemonResource("stadium_managers")
    override val type = PackType.SERVER_DATA

    override val gson: Gson = GsonBuilder()
        .disableHtmlEscaping()
        .setPrettyPrinting()
        .registerTypeAdapter(ResourceLocation::class.java, IdentifierAdapter)
        .registerTypeAdapter(Expression::class.java, ExpressionAdapter)
        .registerTypeAdapter(StadiumCameraManagerEntry::class.java, StadiumCameraManagerAdapter)
        .create()

    override val typeToken: TypeToken<StadiumCameraManagerEntry> = TypeToken.get(StadiumCameraManagerEntry::class.java)
    override val resourcePath = "stadium_managers"

    val managers = mutableMapOf<ResourceLocation, StadiumCameraManagerEntry>()

    override fun reload(data: Map<ResourceLocation, StadiumCameraManagerEntry>) {
        managers.clear()
        data.forEach { location, manager ->
            manager.id = location
            managers[location] = manager
        }
        observable.emit(this)
    }

    override val observable = SimpleObservable<StadiumCameraManagers>()

    override fun sync(player: ServerPlayer) {
        StadiumCameraManagersSyncPacket(managers.values).sendToPlayer(player)
    }
}