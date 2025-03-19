/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.berry

/**
 * Represents the different flavors associated with berries and other Pokémon consumables.
 * See the [Bulbapedia](https://bulbapedia.bulbagarden.net/wiki/Flavor) article for more information.
 *
 * @author Licious
 * @since November 28th, 2022
 */
enum class Flavor(val colour: Int) {
    SPICY(0x00FF00),
    DRY(0xFF0000),
    SWEET(0x0000FF),
    BITTER(0xFFFF00),
    SOUR(0xFF00FF);
}