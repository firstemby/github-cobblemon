package com.cobblemon.mod.common.pokemon.transformation.form

import com.cobblemon.mod.common.api.pokemon.transformation.Transformation
import com.cobblemon.mod.common.api.pokemon.transformation.evolution.PreEvolution
import com.cobblemon.mod.common.api.pokemon.transformation.requirement.TransformationRequirement
import com.cobblemon.mod.common.api.pokemon.transformation.trigger.TransformationTrigger
import com.cobblemon.mod.common.api.pokemon.transformation.trigger.TriggerContext
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemon.mod.common.pokemon.transformation.evolution.Evolution
import com.cobblemon.mod.common.pokemon.transformation.triggers.ItemInteractionTrigger

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
    /** The [TransformationTrigger] to attempt this transformation OUTSIDE of battle. */
    override val trigger: TransformationTrigger = ItemInteractionTrigger(),
    /** The [TransformationRequirement]s that need to be satisfied for this transformation OUTSIDE of battle. */
    override val requirements: Set<TransformationRequirement> = mutableSetOf(),
    override val optional: Boolean = false,
    override val reevaluate: Boolean = true,
) : FormData(), Transformation {

    override val id: String get() = this.formOnlyShowdownId()

    /* Temporary forms DO NOT have their own preevo/evolutions/forms and have to fallback on the parent. Because parentForm
        is not initialized until AFTER species registration, none of these  properties are safe to access until [PokemonSpecies.observable] */

    // preevo is the same as the parent
    override val preEvolution: PreEvolution? get() = parentForm.preEvolution

    // allows preevos to have their own form changes
    override val evolutions: MutableSet<Evolution> get() = parentForm.evolutions

    // allows for adjacent form changes
    override val changeableForms: Collection<TemporaryForm> get() = parentForm.changeableForms  // technically this includes itself

    @delegate:Transient
    override val parentForm: PermanentForm by lazy { parentFormInitializer ?:
        throw IllegalStateException("Unable to find parent form of ${this.name}") } // temp forms MUST have a parent

    // TODO FORM_TESTED event
    override fun test(pokemon: Pokemon, context: TriggerContext?) = pokemon.form !== this && super.test(pokemon, context)

    /**
     * Starts this form change or queues it if [toggleable] is true.
     *
     * @param pokemon The [Pokemon] changing to this form.
     * @param context The optional [TriggerContext] needed to change form.
     * @return Whether the [TemporaryForm] was successful.
     */
    override fun start(pokemon: Pokemon, context: TriggerContext?): Boolean {
        if (this.test(pokemon, context)) {
            return if (this.optional)
                pokemon.formProxy.server().add(this)
            else {
                this.forceStart(pokemon)
                true
            }
        }
        return false
    }

    // TODO FORM_COMPLETE event
    override fun forceStart(pokemon: Pokemon) {

        // TODO: execute this after form change effect
        pokemon.form = this

        // TODO: keep toggleable forms in controller instead of having to re-add?

        super.forceStart(pokemon)
    }

    // TODO FORM_DISPLAY event
    override fun convertToDisplay(pokemon: Pokemon): FormDisplay {
        val result = pokemon.clone().also { it.form = this }
        val expectedDisplay = FormDisplay(this.id, result)
        return expectedDisplay
    }
}
