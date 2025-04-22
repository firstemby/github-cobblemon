package com.cobblemon.mod.common.stadium

import com.cobblemon.mod.common.api.data.JsonDataRegistry
import com.cobblemon.mod.common.api.reactive.SimpleObservable
import com.cobblemon.mod.common.util.adapters.IdentifierAdapter
import com.cobblemon.mod.common.util.cobblemonResource
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.packs.PackType

object BattleCameras: JsonDataRegistry<BattleCameraEntry> {
    override val id = cobblemonResource("stadium_cameras")
    override val type = PackType.SERVER_DATA

    override val gson: Gson = GsonBuilder()
        .disableHtmlEscaping()
        .setPrettyPrinting()
        .registerTypeAdapter(ResourceLocation::class.java, IdentifierAdapter)
        .registerTypeAdapter(BattleCameraEntry::class.java, BattleCameraEntry.CameraEntryAdapter)
        .create()

    override val typeToken: TypeToken<BattleCameraEntry> = TypeToken.get(BattleCameraEntry::class.java)
    override val resourcePath = "stadium_cameras"

    val cameras = mutableMapOf<ResourceLocation, BattleCameraEntry>()

    override fun reload(data: Map<ResourceLocation, BattleCameraEntry>) {
        cameras.clear()
        data.forEach { location, camera ->
            camera.id = location
            cameras[location] = camera
        }
        observable.emit(this)
    }

    override val observable = SimpleObservable<BattleCameras>()

    override fun sync(player: ServerPlayer) {
        BattleCamerasSyncPacket(cameras.values).sendToPlayer(player)
    }

    fun getMoveCamera(move: String): BattleCameraEntry? {
        return cameras.values.firstOrNull { it.move == move }
    }
}