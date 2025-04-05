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

object StadiumCameras: JsonDataRegistry<StadiumCameraEntry> {
    override val id = cobblemonResource("stadium_cameras")
    override val type = PackType.SERVER_DATA

    override val gson: Gson = GsonBuilder()
        .disableHtmlEscaping()
        .setPrettyPrinting()
        .registerTypeAdapter(ResourceLocation::class.java, IdentifierAdapter)
        .registerTypeAdapter(StadiumCameraEntry.CameraKeyFrame::class.java, StadiumCameraEntry.CameraKeyFrameAdapter)
        .create()

    override val typeToken: TypeToken<StadiumCameraEntry> = TypeToken.get(StadiumCameraEntry::class.java)
    override val resourcePath = "stadium_cameras"

    val cameras = mutableMapOf<ResourceLocation, StadiumCameraEntry>()

    override fun reload(data: Map<ResourceLocation, StadiumCameraEntry>) {
        cameras.clear()
        data.forEach { location, camera ->
            camera.id = location
            cameras[location] = camera
        }
        observable.emit(this)
    }

    override val observable = SimpleObservable<StadiumCameras>()

    override fun sync(player: ServerPlayer) {
        StadiumCamerasSyncPacket(cameras.values).sendToPlayer(player)
    }
}