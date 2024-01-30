package com.cobblemon.mod.common.pokemon.transformation.form

import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.api.pokemon.transformation.Transformation
import com.cobblemon.mod.common.api.pokemon.transformation.requirement.TransformationRequirement
import com.cobblemon.mod.common.api.pokemon.transformation.trigger.TransformationTrigger
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemon.mod.common.pokemon.transformation.triggers.ItemInteractionTrigger

/**
 * A form of a [Species] that is interchangeable with a parent [PermanentForm]. These forms are temporary and may be toggled
 * OUTSIDE of battle by its [TransformationTrigger] when the [TransformationRequirement]s are satisfied.
 *
 * For example:
 * https://bulbapedia.bulbagarden.net/wiki/Mega_Evolution
 * https://bulbapedia.bulbagarden.net/wiki/Rotom#Forms
 *
 * Note: triggering of forms INSIDE a battle is forced by Showdown.
 *
 * @author Segfault Guy
 * @since October 21st, 2023
 */
class TemporaryForm (
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
) : FormData(), Transformation