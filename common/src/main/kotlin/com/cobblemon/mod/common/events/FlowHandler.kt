/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.events

import com.cobblemon.mod.common.CobblemonFlows
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.util.cobblemonResource

/**
 * Handles the registration of the default Cobblemon event hooks into flows.
 */
object FlowHandler {
    fun setup() {
        CobblemonEvents.POKEMON_CAPTURED.subscribe { CobblemonFlows.run(cobblemonResource("pokemon_captured"), it.context) }
        CobblemonEvents.BATTLE_VICTORY.subscribe { CobblemonFlows.run(cobblemonResource("battle_victory"), it.context) }
        CobblemonEvents.POKEDEX_DATA_CHANGED_PRE.subscribe { CobblemonFlows.run(cobblemonResource("pokedex_data_changed_pre"), it.getContext(), it) }
        CobblemonEvents.POKEDEX_DATA_CHANGED_POST.subscribe { CobblemonFlows.run(cobblemonResource("pokedex_data_changed_post"), it.getContext()) }
    }
}


