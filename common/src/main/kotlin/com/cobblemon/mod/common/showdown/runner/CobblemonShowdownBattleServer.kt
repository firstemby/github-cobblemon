package com.cobblemon.mod.common.showdown.runner

/**
 * Functionality specific to the Cobblemon fork of Pokemon Showdown
 */
interface CobblemonShowdownBattleServer : ShowdownBattleServer {
    fun registerSpecies()
    fun indicateSpeciesInitialized() {}
    fun registerBagItems() {}
}