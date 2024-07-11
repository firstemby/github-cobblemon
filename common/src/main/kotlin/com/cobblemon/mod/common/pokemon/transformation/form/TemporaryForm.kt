package com.cobblemon.mod.common.pokemon.transformation.form

import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.api.pokemon.transformation.Transformation
import com.cobblemon.mod.common.api.pokemon.transformation.requirement.TransformationRequirement
import com.cobblemon.mod.common.api.pokemon.transformation.trigger.TransformationTrigger
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.Species
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
    /** The [MoveTemplate] of the signature attack of the G-Max form. This is always null on any form aside G-Max. */
    val gigantamaxMove: MoveTemplate? = null,
    /** Whether this form is triggered during a battle. */
    val battleOnly: Boolean = false,
    /**
     * When populated by form name(s) in [showdownId] format, overrides the [parentForm] that this form may transform from
     * (e.g. Necrozma's 'Ultra' from is special can transform from its 'Dawn-Wings' or 'Dusk-Mane forms').
     */
    val parentOverrides: List<String> = emptyList(),
    /** The [TransformationTrigger] to attempt this transformation OUTSIDE of battle. */
    override val trigger: TransformationTrigger = ItemInteractionTrigger(),
    /** The [TransformationRequirement]s that need to be satisfied for this transformation OUTSIDE of battle. */
    override val requirements: Set<TransformationRequirement> = mutableSetOf()
) : FormData(), Transformation {

    @delegate:Transient
    override val parentForm: PermanentForm by lazy { parentFormInitializer ?: throw IllegalStateException("Unable to find parent form of ${this.name}") }

    override fun forceStart(pokemon: Pokemon) {

        // TODO: execute this after form change effect
        pokemon.form = this

        super.forceStart(pokemon)
    }
}
