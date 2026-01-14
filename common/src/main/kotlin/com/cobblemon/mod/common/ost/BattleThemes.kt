package com.cobblemon.mod.common.ost

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

object BattleThemes : JsonDataRegistry<BattleThemeConfig> {

    override val resourcePath = "battle_themes"


    override val id: ResourceLocation = cobblemonResource(this.resourcePath)
    override val type = PackType.SERVER_DATA
    override val observable = SimpleObservable<BattleThemeConfig>()

    override val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(ResourceLocation::class.java, IdentifierAdapter)
        .create()

    override val typeToken = TypeToken.get(BattleThemeConfig::class.java)

    /** */
    private val battleSoundTrackConfigs = mutableMapOf<ResourceLocation, BattleThemeConfig>()

    override fun reload(data: Map<ResourceLocation, BattleThemeConfig>) {
        TODO("Not yet implemented")
    }

    override fun sync(player: ServerPlayer) {
        TODO("Not yet implemented")
    }

    @JvmStatic
    fun getByIdentifier(identifier: ResourceLocation): BattleThemeConfig? = this.battleSoundTrackConfigs[identifier]
}