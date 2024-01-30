package com.cobblemon.mod.common.pokemon.transformation.form

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.pokemon.feature.SpeciesFeature
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.pokemon.transformation.Transformation
import com.cobblemon.mod.common.api.pokemon.transformation.evolution.PreEvolution
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemon.mod.common.pokemon.transformation.evolution.Evolution
import com.google.gson.annotations.SerializedName

/**
 * A form of a [Species] that is noninterchangeable with the [StandardForm]. These forms behave like a different species,
 * and can have a unique set of interchangeable [TemporaryForm]s, but share the same dex number. They are a variation
 * determined by [Evolution] or [SpeciesFeature].
 *
 * For example:
 * https://bulbapedia.bulbagarden.net/wiki/Regional_form
 * https://bulbapedia.bulbagarden.net/wiki/Lycanroc#Forms
 *
 * @author Segfault Guy
 * @since October 21st, 2023
 */
open class PermanentForm (
    @SerializedName("evolutions")
    private val _evolutions: MutableSet<Evolution>? = null,
    @SerializedName("preEvolution")
    private val _preEvolution: PreEvolution? = null,
    @SerializedName("temporaryForms")
    private val _temporaryForms: MutableList<TemporaryForm>? = null,
) : FormData() {

    /**
     * Contains the evolutions of this form.
     * Do not access this property immediately after a species is loaded, it requires all species in the game to be loaded.
     * To be aware of this gamestage subscribe to [PokemonSpecies.observable].
     */
    val evolutions: MutableSet<Evolution>
        get() = _evolutions ?: mutableSetOf()

    val preEvolution: PreEvolution?
        get() = _preEvolution ?: species.preEvolution

    val temporaryForms: MutableList<TemporaryForm>
        get() = _temporaryForms ?: mutableListOf()

    open val forms: List<FormData> get() = temporaryForms

    open val transformations: List<Transformation> get() = temporaryForms + evolutions

    override fun initialize(species: Species, parent: FormData?): FormData {
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