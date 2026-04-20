/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.trade

import com.cobblemon.mod.common.pokemon.Pokemon
import java.util.UUID

class ActiveTrade(val player1: TradeParticipant, val player2: TradeParticipant) {
    val player1Offer = TradeOffer()
    val player2Offer = TradeOffer()

    fun getTradeParticipant(uuid: UUID) = if (player1.uuid == uuid) player1 else player2
    fun getOffer(tradeParticipant: TradeParticipant) = if (tradeParticipant == player1) player1Offer else player2Offer
    fun getOpposingOffer(tradeParticipant: TradeParticipant) = if (tradeParticipant == player1) player2Offer else player1Offer

    fun updateOffer(tradeParticipant: TradeParticipant, pokemon: List<Pokemon>) {
        getOffer(tradeParticipant).updateOffer(pokemon)
        getOffer(getOppositePlayer(tradeParticipant)).accepted = false
        player1.updateOffer(this, tradeParticipant, pokemon)
        player2.updateOffer(this, tradeParticipant, pokemon)
    }

    fun updateAcceptance(tradeParticipant: TradeParticipant, acceptance: Boolean) {
        val offer = getOpposingOffer(tradeParticipant)
        if (offer.accepted != acceptance) {
            offer.accepted = acceptance
            val firstId = offer.pokemon.firstOrNull()?.uuid ?: java.util.UUID.randomUUID()
            getOppositePlayer(tradeParticipant).changeTradeAcceptance(this, firstId, acceptance)
            tradeParticipant.changeTradeAcceptance(this, firstId, acceptance)
        }

        if (offer.accepted && getOffer(tradeParticipant).accepted) {
            //performTrade
            player1.startTradeProcess(this, tradeParticipant == player1)
            player2.startTradeProcess(this, tradeParticipant == player2)
        }
    }

    fun getOppositePlayer(tradeParticipant: TradeParticipant) = if (tradeParticipant == player1) player2 else player1

    fun performTrade(tradeParticipant: TradeParticipant) {
        val offer = getOpposingOffer(tradeParticipant)

        if (offer.accepted && getOffer(tradeParticipant).accepted) {
            TradeManager.performTrade(
                activeTrade = this,
                player1 = player1,
                player2 = player2,
                pokemon1 = player1Offer.pokemon,
                pokemon2 = player2Offer.pokemon
            )
        }
    }

    fun cancelTrade() {
        player1.cancelTrade(this)
        player2.cancelTrade(this)
        TradeManager.removeActiveTrade(this)
    }

    fun completeTrade() {
        val uuids1 = player1Offer.pokemon.map { it.uuid }
        val uuids2 = player2Offer.pokemon.map { it.uuid }
        player1.completeTrade(this, uuids1, uuids2)
        player2.completeTrade(this, uuids2, uuids1)

        player1Offer.pokemon = emptyList()
        player1Offer.accepted = false
        player2Offer.pokemon = emptyList()
        player2Offer.accepted = false
    }
}