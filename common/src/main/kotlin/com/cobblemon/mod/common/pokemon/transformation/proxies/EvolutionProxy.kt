/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.transformation.proxies

import com.cobblemon.mod.common.api.pokemon.transformation.controller.TransformationController
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.transformation.controller.ClientEvolutionController
import com.cobblemon.mod.common.pokemon.transformation.controller.ClientTransformationController
import com.cobblemon.mod.common.pokemon.transformation.controller.ServerEvolutionController
import com.cobblemon.mod.common.pokemon.transformation.controller.ServerTransformationController
import com.cobblemon.mod.common.pokemon.transformation.evolution.Evolution
import com.cobblemon.mod.common.pokemon.transformation.evolution.EvolutionDisplay

/**
 * Holder of the client and server [TransformationController]s for evolutions.
 *
 * @author Licious
 * @since June 18th, 2022
 */
class EvolutionProxy(
    private val pokemon: Pokemon,
) : TransformationProxy<EvolutionDisplay, Evolution, ServerEvolutionController.Intermediate>(pokemon) {

    override var clientController: ClientTransformationController<EvolutionDisplay> =
        ClientEvolutionController(this.pokemon, emptySet())
    override var serverController: ServerTransformationController<Evolution, ServerEvolutionController.Intermediate> =
        ServerEvolutionController(this.pokemon, emptySet(), emptySet())
}