package com.cobblemon.mod.common.client.gui.summary.widgets.screens.transformations

import com.cobblemon.mod.common.api.pokemon.transformation.proxy.TransformationProxy
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.transformation.evolution.Evolution
import com.cobblemon.mod.common.pokemon.transformation.evolution.EvolutionDisplay
import com.cobblemon.mod.common.util.lang

class EvolutionTestSelectScreen(
    x: Int,
    y: Int,
    pokemon: Pokemon
) : EvolutionSelectScreen<EvolutionDisplay, Evolution>(x, y, lang("ui.evolution"), pokemon) {

    override fun getSlots(pokemon: Pokemon): Collection<TransformationSlot<EvolutionDisplay, Evolution>> {
        return pokemon.evolutionProxy.client().map { EvolveSlot(pokemon, it) }
    }

    class EvolveSlot(
        pokemon: Pokemon,
        display: EvolutionDisplay
    ) : TransformationSlot<EvolutionDisplay, Evolution>(pokemon, display, lang("ui.evolve")) {
        override fun getProxy(pokemon: Pokemon): TransformationProxy<EvolutionDisplay, Evolution, *, *> {
            return pokemon.evolutionProxy
        }
    }

}