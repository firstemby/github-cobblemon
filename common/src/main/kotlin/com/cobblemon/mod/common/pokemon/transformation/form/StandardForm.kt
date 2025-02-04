package com.cobblemon.mod.common.pokemon.transformation.form

import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemon.mod.common.pokemon.transformation.evolution.Evolution
import com.google.gson.annotations.SerializedName

/**
 * The [FormData] representation of a [Species].
 *
 * @author Segfault Guy
 * @since December 10th, 2023
 */
class StandardForm(
    @SerializedName("evolutions")
    private val _evolutions: MutableSet<Evolution>? = null,
    @SerializedName("temporaryForms")
    private val _temporaryForms: MutableList<TemporaryForm>? = null,
    @SerializedName("permanentForms")
    private val _permanentForms: MutableList<PermanentForm>? = null,
) : PermanentForm(_evolutions = _evolutions, _temporaryForms = _temporaryForms) {

    // needed for gson
    val permanentForms: MutableList<PermanentForm>
        get() = _permanentForms ?: mutableListOf()

    /** The forms this species is a parent of, including its child [TemporaryForm]s and [PermanentForm]s. Excludes itself. */
    override val forms: List<FormData> = this.temporaryForms + this.permanentForms

    /** Aggregates child forms and their nested forms. */
    fun flattenForms(): List<FormData> = this.forms + this.permanentForms.filter { it != this }.flatMap { it.temporaryForms }
}
