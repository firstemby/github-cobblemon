/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.pokemon.evolution

import com.cobblemon.mod.common.pokemon.evolution.variants.*

interface EvolutionVisitor<R> {
    fun visit(evolution: Evolution): R? {
        return when (evolution) {
            is BlockClickEvolution -> visitBlockClickEvolution(evolution)
            is DummyEvolution -> visitDummyEvolution(evolution)
            is ItemInteractionEvolution -> visitItemInteractionEvolution(evolution)
            is LevelUpEvolution -> visitLevelUpEvolution(evolution)
            is TradeEvolution -> visitTradeEvolution(evolution)
            else -> null
        }
    }

    fun visitBlockClickEvolution(blockClickEvolution: BlockClickEvolution): R
    fun visitDummyEvolution(evolution: Evolution): R // TODO: Use dummyEvolution?
    fun visitItemInteractionEvolution(itemInteractionEvolution: ItemInteractionEvolution): R
    fun visitLevelUpEvolution(levelUpEvolution: LevelUpEvolution): R
    fun visitTradeEvolution(tradeEvolution: TradeEvolution): R
}