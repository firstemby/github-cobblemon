package com.cobblemon.mod.common.pokemon.transformation.form

import com.cobblemon.mod.common.api.pokemon.transformation.Transformation
import com.cobblemon.mod.common.api.pokemon.transformation.evolution.PreEvolution
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemon.mod.common.pokemon.transformation.evolution.Evolution
import com.google.gson.annotations.SerializedName

/**
 * A form of a [Species] that is noninterchangeable with the [StandardForm]. These forms behave like a different species,
 * and can have a unique set of interchangeable [TemporaryForm]s, but share the same dex number.
 *
 * For example:
 * https://bulbapedia.bulbagarden.net/wiki/Regional_form
 * https://bulbapedia.bulbagarden.net/wiki/Lycanroc#Forms
 *
 * @author Segfault Guy
 * @since October 21st, 2023
 */
open class PermanentForm (
    @SerializedName("preEvolution")
    private val _preEvolution: PreEvolution? = null,
    @SerializedName("evolutions")
    private val _evolutions: MutableSet<Evolution>? = null,
    @SerializedName("forms")
    private val _temporaryForms: MutableList<TemporaryForm>? = null,  // nested forms can only be temporary
) : FormData() {

    override val preEvolution: PreEvolution?
        get() = _preEvolution ?: species.preEvolution

    override val evolutions: MutableSet<Evolution>
        get() = _evolutions ?: mutableSetOf()

    /** The forms this species is a parent of. */
    open val forms: Collection<FormData>
        get() = _temporaryForms ?: mutableListOf()

    // for pure PermanentForms (not standard), this is equivalent to the forms property
    override val changeableForms: Collection<TemporaryForm>
        get() = this.forms.filterIsInstance<TemporaryForm>()

    /** The [TemporaryForm]s and [Evolution]s this form can transform to. */
    val transformations: List<Transformation> get() = forms.filterIsInstance<TemporaryForm>() + evolutions

    override fun initialize(species: Species, parent: PermanentForm?): FormData {
        super.initialize(species, parent)
        // These properties are lazy, these need all species to be reloaded but SpeciesAdditions is what will eventually trigger this after all species have been loaded
        this.preEvolution?.species
        this.preEvolution?.form
        this.evolutions.size
        // init nested forms
        this.forms.forEach { it.initialize(species, this) }
        return this
    }

    internal fun resolveEvolutionMoves() {
        this.evolutions.forEach { evolution ->
            if (evolution.learnableMoves.isNotEmpty() && evolution.result.species != null) {
                val pokemon = evolution.result.create()
                pokemon.form.moves.evolutionMoves += evolution.learnableMoves
            }
        }
    }
}