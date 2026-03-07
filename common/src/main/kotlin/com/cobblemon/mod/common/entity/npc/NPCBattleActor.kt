/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.entity.npc

import com.cobblemon.mod.common.api.battles.model.actor.AIBattleActor
import com.cobblemon.mod.common.api.battles.model.actor.ActorType
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor
import com.cobblemon.mod.common.api.battles.model.actor.EntityBackedBattleActor
import com.cobblemon.mod.common.api.battles.model.ai.BattleAI
import com.cobblemon.mod.common.api.battles.ai.config.BattleAIConfigs
import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.api.storage.party.PartyStore
import com.cobblemon.mod.common.battles.ai.ConfigurableBattleAI
import com.cobblemon.mod.common.battles.ai.StrongBattleAI
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import com.cobblemon.mod.common.net.messages.client.battle.BattleEndPacket
import com.cobblemon.mod.common.util.battleLang
import com.cobblemon.mod.common.util.chainFutures
import com.cobblemon.mod.common.util.effectiveName
import com.cobblemon.mod.common.util.update
import net.minecraft.resources.ResourceLocation
import java.util.concurrent.CompletableFuture

open class NPCBattleActor(
    val npc: NPCEntity,
    pokemonList: List<BattlePokemon>,
    val skill: Int,
    battleAIConfigId: ResourceLocation? = null,
    battleBagId: ResourceLocation? = null
) : AIBattleActor(
    gameId = npc.uuid,
    pokemonList = pokemonList.let { if (npc.npc.randomizePartyOrder) it.shuffled() else it },
    battleAI = createBattleAI(skill, battleAIConfigId, battleBagId)
), EntityBackedBattleActor<NPCEntity> {
    override val entity = npc
    override val type = ActorType.NPC
    override fun getName() = npc.effectiveName().copy()
    override fun nameOwned(name: String) = battleLang("owned_pokemon", this.getName(), name)
    override val initialPos = entity.position()

    constructor(
        npc: NPCEntity,
        party: PartyStore,
        skill: Int,
        battleAIConfigId: ResourceLocation? = null,
        battleBagId: ResourceLocation? = null
    ): this(
        npc,
        party.toBattleTeam(healPokemon = npc.npc.autoHealParty),
        skill,
        battleAIConfigId,
        battleBagId
    )

    override fun sendUpdate(packet: NetworkPacket<*>) {
        super.sendUpdate(packet)
        if (packet is BattleEndPacket) {
            if (npc.isAlive) {
                val allEntities = pokemonList.mapNotNull { it.entity }.toMutableList()
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

    companion object {
        /**
         * Creates the appropriate BattleAI based on whether a config ID is provided.
         * If battleAIConfigId is provided, uses ConfigurableBattleAI with that config.
         * Otherwise, falls back to the legacy StrongBattleAI with skill level.
         *
         * @param skill The NPC skill level (used as fallback for legacy AI)
         * @param battleAIConfigId Optional AI config resource location
         * @param battleBagId Optional battle bag configuration for item usage
         */
        private fun createBattleAI(skill: Int, battleAIConfigId: ResourceLocation?, battleBagId: ResourceLocation?): BattleAI {
            com.cobblemon.mod.common.Cobblemon.LOGGER.info("[NPCBattleActor] createBattleAI: skill=$skill, battleAIConfigId=$battleAIConfigId, battleBagId=$battleBagId")
            return if (battleAIConfigId != null) {
                com.cobblemon.mod.common.Cobblemon.LOGGER.info("[NPCBattleActor] Using ConfigurableBattleAI with config: $battleAIConfigId")
                ConfigurableBattleAI.create(battleAIConfigId, battleBagId)
            } else if (battleBagId != null) {
                // If only battle bag is specified, use skill-based configurable AI with items
                com.cobblemon.mod.common.Cobblemon.LOGGER.info("[NPCBattleActor] Using ConfigurableBattleAI with skill-based config (skill=$skill)")
                ConfigurableBattleAI.fromSkillLevel(skill, battleBagId)
            } else {
                // For backwards compatibility, use the legacy StrongBattleAI
                com.cobblemon.mod.common.Cobblemon.LOGGER.info("[NPCBattleActor] Using legacy StrongBattleAI (skill=$skill)")
                StrongBattleAI(skill)
            }
        }
    }
}