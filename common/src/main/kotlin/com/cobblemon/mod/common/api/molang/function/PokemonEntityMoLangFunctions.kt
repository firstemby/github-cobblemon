/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molang.function

import com.bedrockk.molang.runtime.MoLangRuntime
import com.bedrockk.molang.runtime.MoParams
import com.bedrockk.molang.runtime.value.DoubleValue
import com.bedrockk.molang.runtime.value.MoValue
import com.bedrockk.molang.runtime.value.StringValue
import com.cobblemon.mod.common.CobblemonActivities
import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.molang.MoLangFunctions.setup
import com.cobblemon.mod.common.api.moves.animations.ActionEffectContext
import com.cobblemon.mod.common.api.moves.animations.ActionEffects
import com.cobblemon.mod.common.api.moves.animations.TargetsProvider
import com.cobblemon.mod.common.api.storage.party.PartyStore
import com.cobblemon.mod.common.api.tags.CobblemonItemTags
import com.cobblemon.mod.common.entity.pokemon.PokemonBehaviourFlag
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.entity.pokemon.ai.PokemonMoveControl
import com.cobblemon.mod.common.util.asIdentifierDefaultingNamespace
import com.cobblemon.mod.common.util.cloneFrom
import com.cobblemon.mod.common.util.getStringOrNull
import com.cobblemon.mod.common.util.withQueryValue

object PokemonEntityMoLangFunctions : AbstractMoLangFunctionHolder<PokemonEntity>() {
    override fun PokemonEntity.moLangFunctions(): MutableMap<String, (MoParams) -> Any> {
        val pokemonEntity = this
        val map = mutableMapOf<String, (MoParams) -> Any>()
        map["is_busy"] = { DoubleValue(pokemonEntity.isBusy) }
        map["in_battle"] = { DoubleValue(pokemonEntity.isBattling) }
        map["is_moving"] = { DoubleValue((pokemonEntity.moveControl as? PokemonMoveControl)?.hasWanted() == true) }
        map["is_flying"] = { DoubleValue(pokemonEntity.getBehaviourFlag(PokemonBehaviourFlag.FLYING)) }
        map["get_riding_state"] = { params ->
            val name = params.getStringOrNull(0)
            pokemonEntity.ifRidingAvailableSupply(DoubleValue.ZERO) { behaviour, settings, state ->
                val moValue = behaviour.asMoLangValue(settings, state, pokemonEntity)
                return@ifRidingAvailableSupply moValue.functions[name]?.apply(MoParams.EMPTY) as? MoValue ?: moValue
            }
        }
        map["is_gliding"] = {
            pokemonEntity.ifRidingAvailableSupply(DoubleValue.ZERO) { behaviour, settings, state ->
                val moValue = behaviour.asMoLangValue(settings, state, pokemonEntity)
                return@ifRidingAvailableSupply moValue.functions["gliding"]?.apply(MoParams.EMPTY) as? MoValue
                    ?: DoubleValue.ZERO
            }
        }
        map["is_sprinting"] = {
            pokemonEntity.ifRidingAvailableSupply(DoubleValue.ZERO) { behaviour, settings, state ->
                val moValue = behaviour.asMoLangValue(settings, state, pokemonEntity)
                return@ifRidingAvailableSupply moValue.functions["sprinting"]?.apply(MoParams.EMPTY) as? MoValue
                    ?: DoubleValue.ZERO
            }
        }
        map["is_drifting"] = {
            pokemonEntity.ifRidingAvailableSupply(DoubleValue.ZERO) { behaviour, settings, state ->
                val moValue = behaviour.asMoLangValue(settings, state, pokemonEntity)
                return@ifRidingAvailableSupply moValue.functions["drifting"]?.apply(MoParams.EMPTY) as? MoValue
                    ?: DoubleValue.ZERO
            }
        }
        map["is_powered_drifting"] = {
            pokemonEntity.ifRidingAvailableSupply(DoubleValue.ZERO) { behaviour, settings, state ->
                val moValue = behaviour.asMoLangValue(settings, state, pokemonEntity)
                return@ifRidingAvailableSupply moValue.functions["powered_drifting"]?.apply(MoParams.EMPTY) as? MoValue
                    ?: DoubleValue.ZERO
            }
        }
        map["in_air"] = {
            pokemonEntity.ifRidingAvailableSupply(DoubleValue.ZERO) { behaviour, settings, state ->
                val moValue = behaviour.asMoLangValue(settings, state, pokemonEntity)
                return@ifRidingAvailableSupply moValue.functions["in_air"]?.apply(MoParams.EMPTY) as? MoValue
                    ?: DoubleValue.ZERO
            }
        }
        map["is_wild"] = { DoubleValue(pokemonEntity.ownerUUID == null) }
        map["is_in_party"] = { DoubleValue(pokemonEntity.pokemon.storeCoordinates.get()?.store is PartyStore) }
        map["is_ridden"] = { DoubleValue(pokemonEntity.hasControllingPassenger()) }
        map["has_aspect"] = { DoubleValue(it.getString(0) in pokemonEntity.aspects) }
        map["is_pokemon"] = { DoubleValue.ONE }
        map["is_holding_item"] = {
            DoubleValue(!pokemonEntity.entityData.get(PokemonEntity.SHOWN_HELD_ITEM).let {
                it.isEmpty || it.`is`(CobblemonItemTags.WEARABLE_HAT_ITEMS) || it.`is`(CobblemonItemTags.WEARABLE_FACE_ITEMS)
            })
        }
        map["riding_style"] = {
            StringValue(pokemonEntity.ifRidingAvailableSupply("") { behaviour, settings, state ->
                behaviour.getRidingStyle(settings, state).name
            })
        }
        map["is_wearing_hat"] = {
            DoubleValue(
                pokemonEntity.entityData
                    .get(PokemonEntity.SHOWN_HELD_ITEM)
                    .`is`(CobblemonItemTags.WEARABLE_HAT_ITEMS)
            )
        }
        map["is_wearing_face"] = {
            DoubleValue(
                pokemonEntity.entityData
                    .get(PokemonEntity.SHOWN_HELD_ITEM)
                    .`is`(CobblemonItemTags.WEARABLE_FACE_ITEMS)
            )
        }
        map["is_pastured"] = {
            DoubleValue((pokemonEntity.tethering != null))
        }
        map["pasture_conflict_enabled"] = {
            DoubleValue(pokemonEntity.getBehaviourFlag(PokemonBehaviourFlag.PASTURE_CONFLICT))
        }
        map["run_action_effect"] = runActionEffect@{ params ->
            val runtime = MoLangRuntime().setup()
            runtime.environment.cloneFrom(params.environment)
            runtime.withQueryValue("entity", pokemonEntity.struct)
            val actionEffect = ActionEffects.actionEffects[params.getString(0).asIdentifierDefaultingNamespace()]
            if (actionEffect != null) {
                val context = ActionEffectContext(
                    actionEffect = actionEffect,
                    providers = mutableListOf(TargetsProvider(pokemonEntity)),
                    runtime = runtime,
                    level = pokemonEntity.level()
                )
                // This was taken from NPC run_action_effect but this variable isn't used, so I just commented it out.
                // pokemonEntity.actionEffect = context
                pokemonEntity.brain.setMemory(CobblemonMemories.ACTIVE_ACTION_EFFECT, context)
                pokemonEntity.brain.setActiveActivityIfPossible(CobblemonActivities.ACTION_EFFECT)
                actionEffect.run(context).thenRun {
                    val pokemonActionEffect =
                        pokemonEntity.brain.getMemory(CobblemonMemories.ACTIVE_ACTION_EFFECT).orElse(null)
                    if (pokemonActionEffect == context && pokemonEntity.brain.isActive(CobblemonActivities.ACTION_EFFECT)) {
                        pokemonEntity.brain.eraseMemory(CobblemonMemories.ACTIVE_ACTION_EFFECT)
                        //pokemonEntity.actionEffect = null
                    }
                }

                return@runActionEffect DoubleValue.ONE
            }
            return@runActionEffect DoubleValue.ZERO
        }
        return map
    }
}
