/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.trade

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.interaction.RequestManager
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.pokemon.TradeEvent
import com.cobblemon.mod.common.api.interaction.ServerPlayerActionRequest
import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.api.storage.party.PartyStore
import com.cobblemon.mod.common.api.text.aqua
import com.cobblemon.mod.common.net.messages.client.trade.TradeOfferExpiredPacket
import com.cobblemon.mod.common.net.messages.client.trade.TradeOfferNotificationPacket
import com.cobblemon.mod.common.net.messages.client.trade.TradeStartedPacket
import com.cobblemon.mod.common.net.messages.client.trade.TradeStartedPacket.TradeablePokemon
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.evolution.variants.TradeEvolution
import com.cobblemon.mod.common.util.canInteractWith
import com.cobblemon.mod.common.util.party
import java.util.UUID
import net.minecraft.server.level.ServerPlayer

/**
 * Responsible for managing [ActiveTrade]s and the respective [TradeRequest]s that create them.
 *
 * @author Hiroku
 * @since March 4th, 2023
 */
object TradeManager : RequestManager<TradeManager.TradeRequest>() {

    init {
        register(this)
    }

    /**
     * Represents an interaction request between players to trade.
     *
     * @param sender The player sending this request.
     * @param receiver The player receiving this request.
     * @param expiryTime How long (in seconds) this request is active.
     */
    data class TradeRequest(
        override val sender: ServerPlayer,
        override val receiver: ServerPlayer,
        override val expiryTime: Int = 20
    ) : ServerPlayerActionRequest {
        override val key: String = "trade"
        override val requestID: UUID = UUID.randomUUID()
    }

    private val activeTrades = mutableListOf<ActiveTrade>()

    fun removeActiveTrade(trade: ActiveTrade) = activeTrades.remove(trade)

    fun getActiveTrade(playerId: UUID) = activeTrades.find { it.player1.uuid == playerId || it.player2.uuid == playerId }

    override fun expirationPacket(request: TradeRequest): NetworkPacket<*> = TradeOfferExpiredPacket(request)

    override fun notificationPacket(request: TradeRequest): NetworkPacket<*> = TradeOfferNotificationPacket(request)

    override fun onAccept(request: TradeRequest) {
        val trade = ActiveTrade(PlayerTradeParticipant(request.receiver), PlayerTradeParticipant(request.sender))
        activeTrades.add(trade)
        request.sendToSender(TradeStartedPacket(request.sender.uuid, request.receiver.name.plainCopy(), trade.player1.party.mapNullPreserving(::TradeablePokemon)))
        request.sendToReceiver(TradeStartedPacket(request.receiver.uuid, request.sender.name.plainCopy(), trade.player2.party.mapNullPreserving(::TradeablePokemon)))
    }

    override fun canAccept(request: TradeRequest): Boolean {
        if (request.sender.party().none()) {
            request.notifySender(true, "error.insufficient_pokemon.self")
            request.notifyReceiver(true, "error.insufficient_pokemon.other", request.sender.name.copy().aqua())
        }
        else if (request.receiver.party().none()) {
            request.notifySender(true, "error.insufficient_pokemon.other", request.sender.name.copy().aqua())
            request.notifyReceiver(true, "error.insufficient_pokemon.self")
        }
        // isBusy already checks if sender is in an active trade
        else return true
        return false
    }

    override fun isValidInteraction(player: ServerPlayer, target: ServerPlayer): Boolean = player.canInteractWith(target, Cobblemon.config.tradeMaxDistance)

    override fun onLogoff(player: ServerPlayer) {
        super.onLogoff(player)
        val trade = this.getActiveTrade(player.uuid)
        if (trade != null) {
            val tradeParticipant = trade.getTradeParticipant(player.uuid)
            val oppositeParticipant = trade.getOppositePlayer(tradeParticipant)
            oppositeParticipant.cancelTrade(trade)
            this.removeActiveTrade(trade)
        }
    }

    fun performTrade(
        activeTrade: ActiveTrade,
        player1: TradeParticipant,
        pokemon1: List<Pokemon>,
        player2: TradeParticipant,
        pokemon2: List<Pokemon>
    ) {
        val party1 = player1.party
        val party2 = player2.party

        if (pokemon1.any { it !in party1 } || pokemon2.any { it !in party2 }) {
            Cobblemon.LOGGER.warn("Attempted to perform a trade with Pokémon that aren't in the party anymore. Could be attempted duping between ${player1.name} and ${player2.name}")
            activeTrade.cancelTrade()
            return
        }

        if (pokemon1.isEmpty() || pokemon2.isEmpty()) {
            Cobblemon.LOGGER.warn("Attempted to perform a trade where one side offered no Pokémon between ${player1.name} and ${player2.name}. This should never happen.")
            activeTrade.cancelTrade()
            return
        }

        CobblemonEvents.TRADE_EVENT_PRE.postThen(
            TradeEvent.Pre(player1, pokemon2, player2, pokemon1)
        ) {
            pokemon1.forEach { party1.remove(it) }
            pokemon2.forEach { party2.remove(it) }

            pokemon1.forEach { it.setFriendship(it.form.baseFriendship) }
            pokemon2.forEach { it.setFriendship(it.form.baseFriendship) }

            pokemon1.forEach { party2.add(it) }
            pokemon2.forEach { party1.add(it) }

            pokemon1.forEach { p1 ->
                p1.lockedEvolutions.filterIsInstance<TradeEvolution>().firstOrNull {
                    it.attemptEvolution(p1, pokemon2.first())
                }
            }

            pokemon2.forEach { p2 ->
                p2.lockedEvolutions.filterIsInstance<TradeEvolution>().firstOrNull {
                    it.attemptEvolution(p2, pokemon1.first())
                }
            }

            val compactParty = { party: PartyStore ->
                var writeIdx = 0
                for (readIdx in 0 until party.size()) {
                    if (party.get(readIdx) != null) {
                        if (readIdx != writeIdx) {
                            party.swap(readIdx, writeIdx)
                        }
                        writeIdx++
                    }
                }
            }
            compactParty(party1)
            compactParty(party2)

            activeTrade.completeTrade()

            CobblemonEvents.TRADE_EVENT_POST.post(
                TradeEvent.Post(player1, pokemon2, player2, pokemon1)
            )
        }
    }
}