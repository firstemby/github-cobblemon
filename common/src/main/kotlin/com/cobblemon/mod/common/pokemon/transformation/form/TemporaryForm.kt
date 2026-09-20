/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.transformation.form

import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.pokemon.form.FormCompleteEvent
import com.cobblemon.mod.common.api.events.pokemon.form.FormDisplayEvent
import com.cobblemon.mod.common.api.events.pokemon.form.FormTestedEvent
import com.cobblemon.mod.common.api.moves.animations.ActionEffectContext
import com.cobblemon.mod.common.api.moves.animations.ActionEffectTimeline
import com.cobblemon.mod.common.api.moves.animations.ActionEffects
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.pokemon.transformation.Transformation
import com.cobblemon.mod.common.api.pokemon.transformation.evolution.PreEvolution
import com.cobblemon.mod.common.api.pokemon.transformation.requirement.TransformationRequirement
import com.cobblemon.mod.common.api.pokemon.transformation.trigger.ContextTrigger
import com.cobblemon.mod.common.api.pokemon.transformation.trigger.TransformationTrigger
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemon.mod.common.pokemon.activestate.ShoulderedState
import com.cobblemon.mod.common.pokemon.transformation.evolution.Evolution
import com.cobblemon.mod.common.pokemon.transformation.triggers.LevelUpTrigger
import com.cobblemon.mod.common.util.asIdentifierDefaultingNamespace
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.resources.ResourceLocation

/**
 * A form of a [Species] that is interchangeable with a parent [PermanentForm]. This type of form is temporary and may be toggled
 * OUTSIDE of battle by a [TransformationTrigger] when the respective [TransformationRequirement]s are satisfied.
 *
 * Sample forms:
 * https://bulbapedia.bulbagarden.net/wiki/Mega_Evolution
 * https://bulbapedia.bulbagarden.net/wiki/Rotom#Forms
 *
 * Note: triggering this type of form INSIDE a battle is instructed by Showdown.
 *
 * @author Segfault Guy
 * @since October 21st, 2023
 */
open class TemporaryForm (
    /** Whether this form is triggered during a battle. */
    val battleOnly: Boolean = false,
    /** Whether this form can be freely toggled after requirements are initially met. */
    val toggleable: Boolean = false,
    /** The [TransformationTrigger] to change to this form OUTSIDE of battle. */
    override val trigger: TransformationTrigger = LevelUpTrigger(),
    /** The [TransformationRequirement]s that need to be satisfied for this form OUTSIDE of battle. */
    override val requirements: Set<TransformationRequirement> = mutableSetOf(),
    /** If this form can be deferred and started whenever rather than immediately on trigger. */
    override val optional: Boolean = false,
    /** If this form needs its requirements reevaluated to remain valid. */
    override val reevaluate: Boolean = true,
) : FormData(), Transformation {

    override val id: String get() = this.formOnlyShowdownId()

    var actionEffectIdentifier: ResourceLocation? = null

    val actionEffect: ActionEffectTimeline
        get() = ActionEffects.actionEffects[actionEffectIdentifier ?: cobblemonResource("form_${name.lowercase()}")]
            ?: ActionEffects.actionEffects["generic_form".asIdentifierDefaultingNamespace()]
            ?: throw Exception()

    val revertActionEffect: ActionEffectTimeline
        get() = actionEffectIdentifier?.withSuffix("_revert")?.let(ActionEffects.actionEffects::get)
            ?: ActionEffects.actionEffects[cobblemonResource("form_${name.lowercase()}_revert")]
            ?: ActionEffects.actionEffects["generic_form_revert".asIdentifierDefaultingNamespace()]
            ?: throw Exception()

    /* Temporary forms DO NOT have their own preevo/evolutions/forms and have to fallback on the parent. Because parentForm
       is not initialized until AFTER species registration, none of these properties are safe to access until [PokemonSpecies.observable] */

    /**
     * The [PermanentForm] that precedes this form or this form.
     *
     * For example the parent of Toxtricity's 'Gmax' form can be either its  'Low-Key' or 'Standard' form.
     * The parent of Toxtricity's 'Low-Key' form is its 'Standard' form.
     * And the parent of Toxtricity's 'Standard' form is null (does not exist).
     *
     * This is initialized during species registration, and is not safe to access until [PokemonSpecies.observable] is emitted.
     */
    lateinit var parentForm: PermanentForm

    // preevo is the same as the parent
    override val preEvolution: PreEvolution? get() = parentForm.preEvolution

    // allows preevos to have their own form changes
    override val evolutions: MutableSet<Evolution> get() = parentForm.evolutions

    // allows for form changes between adjacent temporary forms belonging to the same parent
    override val changeableForms: Collection<TemporaryForm> get() = parentForm.changeableForms  // technically this includes itself

    override fun initialize(species: Species, parent: PermanentForm?) {
        super.initialize(species, parent)
        if (parent == null) {
            throw IllegalArgumentException("Temporary form $name lacks a parent form - that is meant to be impossible")
        }
        this.parentForm = parent
    }

    override fun getBaseForm() = parentForm.getBaseForm()

    override fun test(pokemon: Pokemon, context: ContextTrigger.Context?): Boolean {
        val baseResult = pokemon.form !== this && super.test(pokemon, context)
        val event = FormTestedEvent(pokemon, this, baseResult)
        CobblemonEvents.FORM_TESTED.post(event)
        return event.result
    }

    /**
     * Starts this form change or queues it if [toggleable] is true.
     *
     * @param pokemon The [Pokemon] changing to this form.
     * @param context The optional [TriggerContext] needed to change form.
     * @return Whether the [TemporaryForm] was successful.
     */
    override fun tryStart(pokemon: Pokemon, context: ContextTrigger.Context?): Boolean {
        if (this.test(pokemon, context)) {
            return if (this.optional)
                pokemon.formProxy.server().add(this)
            else {
                this.perform(pokemon)
                true
            }
        }
        return false
    }

    override fun perform(pokemon: Pokemon) {
        if (pokemon.state is ShoulderedState) {
            pokemon.recall()
        }

        val pokemonEntity = pokemon.entity ?: run {
            pokemon.form = this
            fulfill(pokemon)
            return
        }

        val context = ActionEffectContext.fromPokemonEntity(
            actionEffect = actionEffect,
            pokemonEntity = pokemonEntity
        )

        this.perform(pokemonEntity, context)
    }

    override fun fulfill(pokemon: Pokemon) {
        super.fulfill(pokemon)
        pokemon.form = this
        CobblemonEvents.FORM_COMPLETE.post(FormCompleteEvent(pokemon, this))
    }

    private fun doActionEffect(pokemonEntity: PokemonEntity, context: ActionEffectContext, afterAction: () -> Unit) {
        context.tracker = pokemonEntity // Bind all of this to an entity so recalling the entity just kills all of this
        pokemonEntity.entityData.set(PokemonEntity.TRANSFORMATION_STARTED, true)
        val finishTransformation: () -> Unit = {
            if (pokemonEntity.isTransforming) {
                afterAction()
                pokemonEntity.entityData.set(PokemonEntity.TRANSFORMATION_STARTED, false)
            }
        }
        context.holdRemovalCallbacks["transformation"] = finishTransformation
        val future = context.actionEffect.run(context)
        future.thenRun(finishTransformation)
    }

    fun perform(pokemonEntity: PokemonEntity, context: ActionEffectContext) {
        doActionEffect(pokemonEntity, context) { fulfill(pokemonEntity.pokemon) }
    }

    fun performRevert(pokemonEntity: PokemonEntity, context: ActionEffectContext) {
        doActionEffect(pokemonEntity, context) { revert(pokemonEntity.pokemon) }
    }

    fun revert(pokemon: Pokemon) {
        pokemon.form = parentForm
    }

    override fun convertToDisplay(pokemon: Pokemon): FormDisplay {
        val species = pokemon.species
        val aspects = pokemon.aspects.toMutableSet()
        aspects.removeAll(pokemon.form.aspects.toSet())
        aspects.addAll(this.aspects)
        val expectedDisplay = FormDisplay(this.id, species, aspects)
        val event = FormDisplayEvent(pokemon, expectedDisplay, this)
        CobblemonEvents.FORM_DISPLAY.post(event)
        return event.display
    }
}
