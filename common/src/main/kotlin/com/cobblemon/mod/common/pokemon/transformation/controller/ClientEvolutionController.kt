/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.transformation.controller

import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.pokemon.transformation.proxy.PreProcessor
import com.cobblemon.mod.common.api.text.green
import com.cobblemon.mod.common.net.messages.server.pokemon.update.evolution.AcceptEvolutionPacket
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.transformation.evolution.EvolutionDisplay
import com.cobblemon.mod.common.util.asTranslated
import com.mojang.serialization.Codec
import net.minecraft.client.Minecraft

/**
 * Controller for managing evolutions on the client.
 * From the client's perspective, evolutions are visual only in the form of [EvolutionDisplay]s.
 *
 * @author Licious
 * @since April 28th, 2022
 */
class ClientEvolutionController(
    pokemon: Pokemon,
    evolutions: Set<EvolutionDisplay>,
) : CobblemonClientTransformationController<EvolutionDisplay, ClientEvolutionController.Intermediate>(pokemon, evolutions) {

    override fun notification() {
        Minecraft.getInstance().player?.sendSystemMessage("cobblemon.ui.evolve.hint".asTranslated(this.pokemon.getDisplayName()).green())
        Minecraft.getInstance().player?.playSound(CobblemonSounds.EVOLUTION_NOTIFICATION, 1F, 1F)
    }

    override fun acceptPacket(pokemon: Pokemon, evolution: EvolutionDisplay) = AcceptEvolutionPacket(pokemon, evolution)

    override fun asIntermediate(): Intermediate = Intermediate(this.transformations)

    data class Intermediate(val evolutions: Set<EvolutionDisplay>): PreProcessor {
        override fun create(pokemon: Pokemon): ClientEvolutionController = ClientEvolutionController(pokemon, this.evolutions)
    }

    companion object {
        @JvmStatic
        val CODEC: Codec<Intermediate> = EvolutionDisplay.CODEC.listOf()
            .xmap(
                { displays -> Intermediate(displays.toSet()) },
                { controller -> controller.evolutions.toMutableList() }
            )
    }
}