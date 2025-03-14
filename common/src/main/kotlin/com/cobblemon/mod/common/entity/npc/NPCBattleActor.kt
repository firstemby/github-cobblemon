/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.entity.npc

import com.cobblemon.mod.common.api.battles.model.actor.*
import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.api.storage.party.PartyStore
import com.cobblemon.mod.common.net.messages.client.battle.BattleEndPacket
import com.cobblemon.mod.common.util.battleLang
import com.cobblemon.mod.common.util.chainFutures
import com.cobblemon.mod.common.util.effectiveName
import com.cobblemon.mod.common.util.update
import java.util.concurrent.CompletableFuture

class NPCBattleActor(
    val npc: NPCEntity,
    pokemonProvider: BattlePokemonProvider
) : BattleActor(npc.uuid, pokemonProvider), EntityBackedBattleActor<NPCEntity> {
    override val entity = npc
    override val type = ActorType.NPC
    override fun getName() = npc.effectiveName().copy()
    override fun nameOwned(name: String) = battleLang("owned_pokemon", this.getName(), name)
    override val initialPos = entity.position()

    constructor(
        npc: NPCEntity,
        party: PartyStore
    ): this(
        npc,
        StaticBattlePokemonProvider(party.toBattleTeam(healPokemon = npc.npc.autoHealParty))
    )

    override fun sendUpdate(packet: NetworkPacket<*>) {
        super.sendUpdate(packet)
        if (packet is BattleEndPacket) {
            if (npc.isAlive) {
                val allEntities = battlePokemonProvider.getKnownPokemon().mapNotNull { it.entity }.toMutableList()
                val finalFuture = CompletableFuture<Unit>()
                chainFutures(allEntities.map { pokemonEntity -> { pokemonEntity.recallWithAnimation() } }.iterator(), finalFuture)
                if (allEntities.isEmpty()) {
                    finalFuture.complete(Unit)
                }
                finalFuture.thenApply {
                    // Delay because losing animations can take a second
                    npc.after(seconds = 3F) {
                        entity.entityData.update(NPCEntity.BATTLE_IDS) { it - battle.battleId }
                    }
                }
            } else {
                entity.entityData.update(NPCEntity.BATTLE_IDS) { it - battle.battleId }
            }
        }
    }

    override fun win(otherWinners: List<BattleActor>, losers: List<BattleActor>) {
        super.win(otherWinners, losers)
        npc.playAnimation(NPCEntity.WIN_ANIMATION)
    }

    override fun lose(winners: List<BattleActor>, otherLosers: List<BattleActor>) {
        super.lose(winners, otherLosers)
        npc.playAnimation(NPCEntity.LOSE_ANIMATION)
//        winners.forEach {
//            rewards.forEach {
//                winner give reward :))
//            }
//        }
    }
}