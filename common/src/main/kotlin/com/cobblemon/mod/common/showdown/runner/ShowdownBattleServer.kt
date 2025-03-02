package com.cobblemon.mod.common.showdown.runner

import com.cobblemon.mod.common.api.moves.MoveTemplate

/**
 * A service that controls the creation of individual Pokemon battles with Pokemon Showdown
 */
interface ShowdownBattleServer {
    fun connect()
    fun disconnect()
    fun initializeNewBattle(battleId: String, messages: Array<String>): ShowdownBattle
    fun sendServerMessage(msg: String)
    //Ideally this should be different per-battle but since we only refresh the registries at world load, that will be challenging
    fun getAbilityIds(): Collection<String>
    fun getMoves(): Collection<MoveTemplate>
    fun getItemIds(): Collection<String>
    fun onTick() {}
    fun onServerStarted()
}