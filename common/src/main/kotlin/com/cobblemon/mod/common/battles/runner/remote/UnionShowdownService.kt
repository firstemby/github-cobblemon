package com.cobblemon.mod.common.battles.runner.remote

import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.battles.runner.ShowdownService
import com.google.gson.JsonArray
import net.minecraft.resources.ResourceLocation
import java.util.*

class UnionShowdownService(
    override val id: ResourceLocation,
    val host: String = "localhost",
    val webPort: Int = 8000,
    val socketPort: Int = 8001
) : ShowdownService {

    override fun openConnection() {
        TODO("Not yet implemented")
    }

    override fun closeConnection() {
        TODO("Not yet implemented")
    }

    override fun startBattle(battle: PokemonBattle, messages: Array<String>) {
        TODO("Not yet implemented")
    }

    override fun send(battleId: UUID, messages: Array<String>) {
        TODO("Not yet implemented")
    }

    override fun getAbilityIds(): JsonArray {
        TODO("Not yet implemented")
    }

    override fun getMoves(): JsonArray {
        TODO("Not yet implemented")
    }

    override fun getItemIds(): JsonArray {
        TODO("Not yet implemented")
    }

    override fun registerSpecies() {
        TODO("Not yet implemented")
    }

    override fun registerBagItems() {
        TODO("Not yet implemented")
    }

}
