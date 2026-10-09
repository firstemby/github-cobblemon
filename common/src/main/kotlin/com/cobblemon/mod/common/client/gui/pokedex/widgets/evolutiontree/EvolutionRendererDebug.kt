/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.pokedex.widgets.evolutiontree

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

fun debugEvolutionRequirementRendering() {
    if (!Cobblemon.config.enableDebugKeys) {
        return
    }

    val gaps = mutableListOf<Pair<String, String>>()

    for (species in PokemonSpecies.implemented) {
        val evolutions = species.evolutions + species.forms.flatMap { it.evolutions }
        for (evolution in evolutions) {
            if (renderEvolutionData(evolution) == null) {
                gaps.add("Evolution: ${evolution::class.simpleName}" to species.showdownId())
            }
            for (requirement in evolution.requirements) {
                if (resolveRequirementData(requirement) == null) {
                    gaps.add("Requirement: ${requirement::class.simpleName}" to species.showdownId())
                }
            }
        }
    }

    val player = Minecraft.getInstance().player
    if (gaps.isEmpty()) {
        player?.sendSystemMessage(Component.literal("Evolution render debug: no gaps found."))
        return
    }

    val byType = gaps.groupBy({ it.first }, { it.second })
    println("Evolution render debug found ${gaps.size} gap(s) across ${byType.size} type(s):")
    byType.forEach { (type, species) -> println("  $type: ${species.distinct().joinToString(", ")}") }

    player?.sendSystemMessage(Component.literal("Evolution render debug: ${gaps.size} gap(s) across ${byType.size} type(s), see log for details."))
}
