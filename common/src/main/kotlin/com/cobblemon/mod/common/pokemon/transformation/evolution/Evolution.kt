/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.transformation.evolution

import com.cobblemon.mod.common.Cobblemon.playerDataManager
import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.advancement.CobblemonCriteria
import com.cobblemon.mod.common.advancement.criterion.EvolvePokemonContext
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.pokemon.PokemonGainedEvent
import com.cobblemon.mod.common.api.events.pokemon.evolution.EvolutionCompleteEvent
import com.cobblemon.mod.common.api.events.pokemon.evolution.EvolutionTestedEvent
import com.cobblemon.mod.common.api.moves.BenchedMove
import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.transformation.Transformation
import com.cobblemon.mod.common.api.pokemon.transformation.evolution.EvolutionDisplay
import com.cobblemon.mod.common.api.pokemon.transformation.evolution.EvolutionLike
import com.cobblemon.mod.common.api.pokemon.transformation.requirement.TransformationRequirement
import com.cobblemon.mod.common.api.pokemon.transformation.trigger.PassiveTrigger
import com.cobblemon.mod.common.api.pokemon.transformation.trigger.TransformationTrigger
import com.cobblemon.mod.common.api.pokemon.transformation.trigger.TriggerContext
import com.cobblemon.mod.common.api.tags.CobblemonItemTags
import com.cobblemon.mod.common.item.PokeBallItem
import com.cobblemon.mod.common.net.messages.client.animation.PlayPosableAnimationPacket
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.activestate.ShoulderedState
import com.cobblemon.mod.common.pokemon.transformation.triggers.ItemInteractionTrigger
import com.cobblemon.mod.common.util.lang
import com.cobblemon.mod.common.util.party
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.ItemStack

/**
 * Represents an evolution of a [Pokemon], this is the server side counterpart of [EvolutionDisplay].
 *
 * @author Licious
 * @since March 19th, 2022
 */
class Evolution(
    override val id: String = "",
    override val requirements: Set<TransformationRequirement> = setOf(),
    override val trigger: TransformationTrigger = ItemInteractionTrigger(),
    /** The result of this evolution. */
    val result: PokemonProperties = PokemonProperties(),
    /** The shed result of this evolution. */
    val shedder: PokemonProperties? = null,
    /** If this evolution allows the user to choose when to start it or not. */
    var optional: Boolean = true,
    /** The [MoveTemplate]s that will be offered to be learnt upon evolving. */
    val learnableMoves: Set<MoveTemplate> = setOf()
) : EvolutionLike, Transformation {

    override fun test(pokemon: Pokemon, context: TriggerContext?): Boolean {
        val result = super.test(pokemon, context)
        val event = EvolutionTestedEvent(pokemon, this, result, result)
        CobblemonEvents.EVOLUTION_TESTED.post(event)
        return event.result
    }

    /**
     * Starts this evolution or queues it if [optional] is true.
     *
     * @param pokemon The [Pokemon] being evolved.
     * @param context The optional [TriggerContext] needed to evolve.
     * @return Whether the [Evolution] was successful.
     */
    override fun start(pokemon: Pokemon, context: TriggerContext?): Boolean {
        if (this.test(pokemon, context)) {
            return if (this.optional) pokemon.evolutionProxy.server().add(this)
            else {
                this.forceStart(pokemon)
                true
            }
        }
        return false
    }

    fun shed(pokemon: Pokemon): Boolean {
        val innerShedder = shedder ?: return false

        val owner = pokemon.getOwnerPlayer() ?: return false
        // If the player has at least one Pokeball in their inventory.
        var pokeballStack: ItemStack? = null
        if (!owner.isCreative) {
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
        val shedPokemon = pokemon.clone()
        shedPokemon.removeHeldItem()
        innerShedder.apply(shedPokemon)
        shedPokemon.caughtBall = ((pokeballStack?.item ?: CobblemonItems.POKE_BALL) as PokeBallItem).pokeBall
        pokemon.storeCoordinates.get()?.store?.add(shedPokemon)
        CobblemonCriteria.EVOLVE_POKEMON.trigger(owner, EvolvePokemonContext(pokemon.preEvolution!!.species.resourceIdentifier, shedPokemon.species.resourceIdentifier, playerDataManager.getGenericData(owner).advancementData.totalEvolvedCount))
        // Consume one of the balls
        pokeballStack?.shrink(1)

        return true
    }

    /**
     * Starts this evolution as soon as possible.
     * This will not present a choice to the client regardless of [optional].
     *
     * @param pokemon The [Pokemon] being evolved.
     */
    override fun forceStart(pokemon: Pokemon) {
        // This is a switch to enable/disable the evolution effect while we get particles improved
        val useEvolutionEffect = true

        if (pokemon.state is ShoulderedState) {
            pokemon.tryRecallWithAnimation()
        }

        val preEvoName = pokemon.getDisplayName()
        val pokemonEntity = pokemon.entity
        if (pokemonEntity == null || !useEvolutionEffect) {
            pokemon.getOwnerPlayer()?.playNotifySound(CobblemonSounds.EVOLUTION_UI, SoundSource.PLAYERS, 1F, 1F)
            evolutionMethod(pokemon)
            super.forceStart(pokemon)
            pokemon.getOwnerPlayer()?.sendSystemMessage(lang("ui.evolve.into", preEvoName, pokemon.species.translatedName))
        } else {
            pokemonEntity.busyLocks.add("evolving")
            pokemonEntity.navigation.stop()
            pokemonEntity.after(1F) {
                evolutionAnimation(pokemonEntity)
            }
            pokemonEntity.after(11.2F) {
                evolutionMethod(pokemon)
                super.forceStart(pokemon)
            }
            pokemonEntity.after( seconds = 12F ) {
                cryAnimation(pokemonEntity)
                pokemonEntity.busyLocks.remove("evolving")
                pokemon.getOwnerPlayer()?.sendSystemMessage(lang("ui.evolve.into", preEvoName, pokemon.species.translatedName))
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
        this.result.apply(pokemon)
        this.learnableMoves.forEach { move ->
            if (pokemon.moveSet.hasSpace()) {
                pokemon.moveSet.add(move.create())
            } else {
                pokemon.benchedMoves.add(BenchedMove(move, 0))
            }
            pokemon.getOwnerPlayer()?.sendSystemMessage(lang("experience.learned_move", pokemon.getDisplayName(), move.displayName))
        }
        // we want to instantly tick for example you might only evolve your Bulbasaur at level 34 so Venusaur should be immediately available
        pokemon.triggerTransformations(PassiveTrigger::class.java)
        this.shed(pokemon)
        CobblemonEvents.EVOLUTION_COMPLETE.post(EvolutionCompleteEvent(pokemon, this))
        CobblemonEvents.POKEMON_GAINED.post(PokemonGainedEvent(pokemon.getOwnerUUID()!!, pokemon))
    }

}