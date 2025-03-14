/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.pokemon

import com.cobblemon.mod.common.api.molang.MoLangFunctions.addFunctions
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asStruct
import com.cobblemon.mod.common.api.pokemon.helditem.HeldItemManager
import com.cobblemon.mod.common.api.pokemon.helditem.HeldItemProvider
import com.cobblemon.mod.common.battles.actor.MultiPokemonBattleActor
import com.cobblemon.mod.common.battles.actor.PokemonBattleActor
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.net.messages.client.battle.BattleUpdateTeamPokemonPacket
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.properties.BattleCloneProperty
import com.cobblemon.mod.common.pokemon.properties.UncatchableProperty
import com.cobblemon.mod.common.util.battleLang
import com.cobblemon.mod.common.util.server
import net.minecraft.network.chat.MutableComponent
import java.util.function.Function

open class BattlePokemon(
    val originalPokemon: Pokemon,
    val effectedPokemon: Pokemon = originalPokemon,
    val postBattleEntityOperation: (PokemonEntity) -> Unit = {}
) : AbstractBattlePokemon() {
    override val uuid = effectedPokemon.uuid
    override var health = effectedPokemon.currentHealth
    override val maxHealth = effectedPokemon.maxHealth
    override val nature = effectedPokemon.nature
    override val moveSet = effectedPokemon.moveSet
    override val entity = effectedPokemon.entity
    override val heldItemManager: HeldItemManager by lazy {
        HeldItemProvider.provide(this)
    }

    var willBeSwitchedIn = false

    override fun canBeSentOut(): Boolean {
        return if (actor.request?.side?.pokemon?.any { it.reviving } == true) {
            !isSentOut() && !willBeSwitchedIn && health <= 0
        } else {
            !isSentOut() && !willBeSwitchedIn && health > 0
        }
    }

    override fun getName(): MutableComponent {
        val displayPokemon = ((getIllusion() ?: this) as BattlePokemon).effectedPokemon
        return if (actor is PokemonBattleActor || actor is MultiPokemonBattleActor) {
            displayPokemon.getDisplayName()
        } else {
            battleLang("owned_pokemon", actor.getName(), displayPokemon.getDisplayName())
        }
    }

    override fun sendUpdate() {
        actor.sendUpdate(BattleUpdateTeamPokemonPacket(effectedPokemon))
    }

    init {
        struct.addFunctions(mutableMapOf(
            "pokemon" to Function { effectedPokemon.asStruct() },
            "original_pokemon" to Function {
                originalPokemon.asStruct()
            },
        ))
    }

    companion object {
        fun safeCopyOf(pokemon: Pokemon): BattlePokemon {
            //TOOD figure out a closer registry access (might have to break some method signatures for this (1.7?)
            val effectedPokemon = pokemon.clone(registryAccess = server()?.registryAccess() ?: throw IllegalStateException("No registry access available"))
            BattleCloneProperty.isBattleClone().apply(effectedPokemon)
            UncatchableProperty.uncatchable().apply(effectedPokemon)
            return BattlePokemon(
                originalPokemon = pokemon,
                effectedPokemon = effectedPokemon,
                postBattleEntityOperation = { it.recallWithAnimation() }
            )
        }

        fun playerOwned(pokemon: Pokemon): BattlePokemon = BattlePokemon(
            originalPokemon = pokemon,
            effectedPokemon = pokemon,
            postBattleEntityOperation = { entity ->
                entity.effects.wipe()
            }
        )
    }
}