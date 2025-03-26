package com.cobblemon.mod.common.api.battles.model.actor

import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import com.cobblemon.mod.common.entity.npc.NPCBattleActor
import com.cobblemon.mod.common.entity.npc.NPCEntity

class UnionBattleActor(npc: NPCEntity, pokemonList: List<BattlePokemon>) : NPCBattleActor(npc, pokemonList.toMutableList(), 1) {

    override fun writeShowdownResponse() {}

    override fun onChoiceRequested() {
        this.mustChoose = false
    }

    override fun turn() {
        super.turn()
        this.mustChoose = false
    }

    override fun upkeep() {
        super.upkeep()
        this.mustChoose = false
    }

}
