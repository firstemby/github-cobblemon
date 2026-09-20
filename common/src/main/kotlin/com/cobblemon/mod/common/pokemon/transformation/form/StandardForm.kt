/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.transformation.form

import com.cobblemon.mod.common.api.pokemon.transformation.evolution.PreEvolution
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemon.mod.common.pokemon.transformation.evolution.Evolution

/**
 * The [FormData] representation of a [Species].
 *
 * @author Segfault Guy
 * @since December 10th, 2023
 */
class StandardForm(
    preEvolution: PreEvolution?,
    evolutions: MutableSet<Evolution>,
    forms: MutableList<FormData>
) : PermanentForm(preEvolution, evolutions, forms.filterIsInstance<TemporaryForm>().toMutableList()) {

    override val forms: MutableList<FormData> = forms

    /** The [PermanentForm]s that belong to this form. */
    val permanentForms: Collection<PermanentForm> get() = forms.filterIsInstance<PermanentForm>()

    /** Aggregates child forms and their nested forms. */
    fun flattenForms(): Collection<FormData> = this.forms + this.permanentForms.flatMap { it.forms }
}

// TODO remove completely and just have Species be a FormData?