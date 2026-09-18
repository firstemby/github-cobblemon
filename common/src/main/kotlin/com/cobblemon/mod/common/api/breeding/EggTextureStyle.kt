/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.breeding

/**
 * The style of visibility applied to Pokémon eggs. Essentially it represents how much information about the contents
 * of the egg is given away with its apperance. Species makes it aware of the species and form, type makes it aware
 * of the dominant type, and mystery is no information at all.
 *
 * @author Hiroku
 * @since April 8th, 2026
 */
enum class EggTextureStyle {
    SPECIES,
    TYPE,
    MYSTERY
}