/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.config.task

import com.bedrockk.molang.runtime.struct.QueryStruct
import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.ai.BrainConfigurationContext
import com.cobblemon.mod.common.battles.BattleRegistry
import com.cobblemon.mod.common.entity.PosableEntity
import com.cobblemon.mod.common.entity.npc.NPCEntity
import com.cobblemon.mod.common.util.asExpression
import com.cobblemon.mod.common.util.resolveBoolean
import com.cobblemon.mod.common.util.withQueryValue
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.behavior.BehaviorControl
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder
import net.minecraft.world.entity.ai.behavior.declarative.Trigger
import net.minecraft.world.entity.ai.memory.MemoryModuleType

class ExitBattleWhenHurtTaskConfig : SingleTaskConfig {
    var condition = "true".asExpression()
    var includePassiveDamage = true

    override fun createTask(
        entity: LivingEntity,
        brainConfigurationContext: BrainConfigurationContext
    ): BehaviorControl<in LivingEntity>? {
        runtime.withQueryValue("entity", (entity as? PosableEntity)?.struct ?: QueryStruct(hashMapOf()))
        if (!runtime.resolveBoolean(condition)) return null

        if (entity is NPCEntity) {
            fun cancelNPCBattles(npcEntity: NPCEntity): Boolean {
                val battles = npcEntity.battleIds.mapNotNull(BattleRegistry::getBattle)
                battles.forEach { it.end() }
                return battles.isNotEmpty()
            }

            return if (includePassiveDamage) {
                 BehaviorBuilder.create {
                    it.group(it.present(MemoryModuleType.HURT_BY), it.present(CobblemonMemories.NPC_BATTLING))
                        .apply(it) { _, _ -> Trigger { world, entity, _ -> return@Trigger cancelNPCBattles(entity as NPCEntity) } }
                }
            } else {
                BehaviorBuilder.create {
                    it.group(it.present(MemoryModuleType.HURT_BY_ENTITY), it.present(CobblemonMemories.NPC_BATTLING))
                        .apply(it) { _, _ -> Trigger { world, entity, _ -> return@Trigger cancelNPCBattles(entity as NPCEntity) } }
                }
            }
        } else { return null }/* else if (entity is PokemonEntity) { TODO uncomment when pokemon have brain :)
            fun cancelPokemonBattle(pokemonEntity: PokemonEntity): Boolean {
                val battle = BattleRegistry.getBattle(pokemonEntity.battleId ?: return false)
                battle?.end()
                return battle != null
            }

            return if (includePassiveDamage) {
                BehaviorBuilder.create {
                    it.group(it.present(MemoryModuleType.HURT_BY), it.present(CobblemonMemories.POKEMON_BATTLING))
                        .apply(it) { _, _ -> Trigger { world, entity, _ -> return@Trigger cancelPokemonBattle(entity as PokemonEntity) } }
                }
            } else {
                BehaviorBuilder.create {
                    it.group(it.present(MemoryModuleType.HURT_BY_ENTITY), it.present(CobblemonMemories.POKEMON_BATTLING))
                        .apply(it) { _, _ -> Trigger { world, entity, _ -> return@Trigger cancelPokemonBattle(entity as PokemonEntity) } }
                }
            }
        }*/
    }
}