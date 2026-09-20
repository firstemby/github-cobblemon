/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.events.pokemon.form

import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.transformation.form.TemporaryForm

/**
 * The root of the various [TemporaryForm]-related events.
 */
interface FormEvent {
    /** The Pokémon impacted in this event.*/
    val pokemon: Pokemon
    /*** The form triggering this event.*/
    val form: TemporaryForm
}
