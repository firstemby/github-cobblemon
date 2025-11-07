/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.pokemon.evolution

import com.cobblemon.mod.common.Cobblemon.playerDataManager
import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.advancement.CobblemonCriteria
import com.cobblemon.mod.common.advancement.criterion.EvolvePokemonContext
import com.cobblemon.mod.common.api.drop.DropTable
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.pokemon.PokemonGainedEvent
import com.cobblemon.mod.common.api.events.pokemon.evolution.EvolutionCompleteEvent
import com.cobblemon.mod.common.api.events.pokemon.evolution.EvolutionTestedEvent
import com.cobblemon.mod.common.api.moves.BenchedMove
import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.requirement.Requirement
import com.cobblemon.mod.common.api.tags.CobblemonItemTags
import com.cobblemon.mod.common.api.text.green
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.item.PokeBallItem
import com.cobblemon.mod.common.net.messages.client.animation.PlayPosableAnimationPacket
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.activestate.ShoulderedState
import com.cobblemon.mod.common.pokemon.evolution.controller.ServerEvolutionController
import com.cobblemon.mod.common.pokemon.evolution.variants.ItemInteractionEvolution
import com.cobblemon.mod.common.pokemon.evolution.variants.LevelUpEvolution
import com.cobblemon.mod.common.pokemon.evolution.variants.TradeEvolution
import com.cobblemon.mod.common.util.isInBattle
import com.cobblemon.mod.common.util.lang
import com.cobblemon.mod.common.util.party
import com.cobblemon.mod.common.world.gamerules.CobblemonGameRules
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.ItemStack

/**
 * Represents an evolution of a [Pokemon], this is the server side counterpart of [EvolutionDisplay].
 * Following Pokémon these can be triggered by 3 possible events, level ups, trades or using an item.
 * For the default implementations see [LevelUpEvolution], [TradeEvolution] & [ItemInteractionEvolution].
 * Also see [PassiveEvolution] & [ContextEvolution].
 *
 * @author Licious
 * @since March 19th, 2022
 */
interface Evolution : EvolutionLike {

    /**
     * The result of this evolution.
     */
    val result: PokemonProperties

    /**
     * The shed result of this evolution.
     */
    val shedder: PokemonProperties?

    /**
     * If this evolution allows the user to choose when to start it or not.
     */
    var optional: Boolean

    /**
     * If this [Evolution] will consume the [Pokemon.heldItem]
     */
    var consumeHeldItem: Boolean

    /**
     * The [Requirement]s behind this evolution.
     */
    val requirements: MutableSet<Requirement>

    /**
     * The [MoveTemplate]s that will be offered to be learnt upon evolving.
     */
    val learnableMoves: MutableSet<MoveTemplate>

    /**
     * The items that will drop once the evolution finishes.
     */
    val drops: DropTable

    /**
     * Checks if the given [Pokemon] passes all the conditions and is ready to evolve.
     *
     * @param pokemon The [Pokemon] being queried.
     * @return If the [Evolution] can start.
     */
    fun test(pokemon: Pokemon): Boolean {
        val result = this.requirements.all { requirement -> requirement.check(pokemon) }
        val event = EvolutionTestedEvent(pokemon, this, result, result)
        CobblemonEvents.EVOLUTION_TESTED.post(event)
        return event.result
    }

    /**
     * Starts this evolution or queues it if [optional] is true.
     * Side effects may occur based on [consumeHeldItem].
     *
     * @param pokemon The [Pokemon] being evolved.
     */
    fun evolve(pokemon: Pokemon): Boolean {
        val serverController = pokemon.evolutionProxy.server() as ServerEvolutionController
        if (pokemon.getOwnerPlayer()?.isInBattle() == true) return false
        if (pokemon.entity?.isBusy == true) return false

        if (this.consumeHeldItem) pokemon.swapHeldItem(ItemStack.EMPTY)

        // All the networking is handled under the hood, see EvolutionController.
        if (this.optional) return serverController.add(this)
        else {
            if (pokemon.entity != null) {
                // delay necessary for chained forced evolutions for their particles not break
                pokemon.entity?.after(0.5F) { serverController.start(this) }
            } else {
                serverController.start(this)
            }
            return true
        }
    }

    fun shed(pokemon: Pokemon): Boolean {
        val innerShedder = shedder ?: return false

        val owner = pokemon.getOwnerPlayer() ?: return false
        // If the player has at least one Pokeball in their inventory.
        var pokeballStack: ItemStack? = null
        if (!owner.hasInfiniteMaterials()) {
            for (i in 0 until owner.inventory.containerSize) {
                val stackI = owner.inventory.getItem(i)
                if (stackI.`is`(CobblemonItemTags.POKE_BALLS)) {
                    pokeballStack = stackI
                }
            }
            if (pokeballStack == null) return false
        }
        // If the player has at least one empty spot in their party.
        owner.party().getFirstAvailablePosition() ?: return false

        // Add shed Pokemon to player's party
        val shedPokemon = pokemon.clone(registryAccess = owner.registryAccess())
        shedPokemon.removeHeldItem()
        innerShedder.apply(shedPokemon)
        shedPokemon.caughtBall = ((pokeballStack?.item ?: CobblemonItems.POKE_BALL) as PokeBallItem).pokeBall
        pokemon.storeCoordinates.get()?.store?.add(shedPokemon)
        CobblemonCriteria.EVOLVE_POKEMON.trigger(owner, EvolvePokemonContext(pokemon.preEvolution!!.species.resourceIdentifier, shedPokemon.species.resourceIdentifier, playerDataManager.getGenericData(owner).advancementData.totalEvolvedCount))
        // Consume one of the balls (if the player isn't creative)
        pokeballStack?.consume(1, owner)

        return true
    }

    /**
     * Starts this evolution as soon as possible.
     * This will not present a choice to the client regardless of [optional].
     *
     * @param pokemon The [Pokemon] being evolved.
     */
    fun forceEvolve(pokemon: Pokemon, optional: Boolean = true) {
        // This is a switch to enable/disable the evolution effect while we get particles improved
        val useEvolutionEffect = true

        if (pokemon.state is ShoulderedState) {
            pokemon.tryRecallWithAnimation()
        }

        val preEvoName = pokemon.getDisplayName()
        val pokemonEntity = pokemon.entity
        val player = pokemon.getOwnerPlayer()
        if (pokemonEntity == null || !useEvolutionEffect) {
            player?.playNotifySound(CobblemonSounds.EVOLUTION_UI, SoundSource.PLAYERS, 1F, 1F)
            evolutionMethod(pokemon)
            player?.sendSystemMessage(lang("ui.evolve.into", preEvoName, pokemon.species.translatedName))
        } else {
            pokemonEntity.entityData.set(PokemonEntity.EVOLVING, true)
            if (optional)
            {
                player?.playNotifySound(CobblemonSounds.EVOLUTION_UI, SoundSource.PLAYERS, 1F, 1F)
                player?.sendSystemMessage(lang("ui.evolve.forced", pokemon.getDisplayName()).green())
            }
            pokemonEntity.navigation.stop()
            pokemonEntity.after(1F) {
                evolutionAnimation(pokemonEntity)
            }
            pokemonEntity.after(11.2F) {
                evolutionMethod(pokemon)
            }
            pokemonEntity.after(seconds = 12F) {
                cryAnimation(pokemonEntity)
                pokemon.getOwnerPlayer()?.sendSystemMessage(lang("ui.evolve.into", preEvoName, pokemon.species.translatedName))
                pokemonEntity.entityData.set(PokemonEntity.EVOLVING, false)
            }
        }
    }

    private fun evolutionAnimation(pokemon: Entity) {
        val playPoseableAnimationPacket = PlayPosableAnimationPacket(pokemon.id, setOf("q.bedrock_stateful('evolution', 'evolution', 'endures_primary_animations');"), listOf())
        playPoseableAnimationPacket.sendToPlayersAround(pokemon.x, pokemon.y, pokemon.z, 128.0, pokemon.level().dimension())
    }

    private fun cryAnimation(pokemon: Entity) {
        val playPoseableAnimationPacket = PlayPosableAnimationPacket(pokemon.id, setOf("cry"), emptyList())
        playPoseableAnimationPacket.sendToPlayersAround(pokemon.x, pokemon.y, pokemon.z, 128.0, pokemon.level().dimension())
    }

    fun evolutionMethod(pokemon: Pokemon) {
        // This ensures the Pokémon doesn't lose moves during evolution
        // (e.g., Oshawott evolving at level 17 knowing Razor Shell, while Dewott only learns it at level 18).
        val previousSpeciesLearnableMoves = pokemon.relearnableMoves

        val sourcePokemon = pokemon.clone()
        
        this.result.apply(pokemon)

        val movesToLearn = previousSpeciesLearnableMoves + this.learnableMoves
        // This adds moves the BenchedMoves piecemeal, so unless we add doWithoutEmitting this will send a *lot* of BenchedMovesUpdate packets.
        pokemon.benchedMoves.doWithoutEmitting {
            movesToLearn.forEach { move ->
                val couldAddMove =
                    if (pokemon.moveSet.hasSpace()) {
                        pokemon.moveSet.add(move.create())
                    } else {
                        pokemon.benchedMoves.add(BenchedMove(move, 0))
                    }

                val previousSpeciesKnewMove = previousSpeciesLearnableMoves.any { move.name == it.name }

                if (couldAddMove && !previousSpeciesKnewMove) {
                    pokemon.getOwnerPlayer()?.sendSystemMessage(lang("experience.learned_move", pokemon.getDisplayName(), move.displayName))
                }
            }
        }
        pokemon.benchedMoves.update()

        // we want to instantly tick for example you might only evolve your Bulbasaur at level 34 so Venusaur should be immediately available
        pokemon.evolutions.filterIsInstance<PassiveEvolution>().forEach { evolution -> evolution.attemptEvolution(pokemon) }
        pokemon.lockedEvolutions.filterIsInstance<PassiveEvolution>().forEach { evolution -> evolution.attemptEvolution(pokemon) }

        this.shed(pokemon)

        val ownerPlayer = pokemon.getOwnerPlayer()
        if (ownerPlayer != null && ownerPlayer.level().gameRules.getBoolean(CobblemonGameRules.DO_POKEMON_LOOT)) {
            drops.drop(
                pokemon.entity,
                ownerPlayer.level() as ServerLevel,
                pokemon.entity?.position() ?: ownerPlayer.position(),
                ownerPlayer,
                pokemon = pokemon
            )
        }

        CobblemonEvents.EVOLUTION_COMPLETE.post(EvolutionCompleteEvent(pokemon, sourcePokemon, this))
        CobblemonEvents.POKEMON_GAINED.post(PokemonGainedEvent(pokemon.getOwnerUUID()!!, pokemon))
    }

    fun applyTo(pokemon: Pokemon) {
        result.apply(pokemon)
    }
}
