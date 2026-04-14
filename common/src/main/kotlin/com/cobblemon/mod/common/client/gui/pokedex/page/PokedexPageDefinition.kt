/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.pokedex.page

data class PokedexPageDefinition(
    val pageType: String = "",
    val titleKey: String = "ui.pokedex.page.pokedex",
    val order: Int = 0,
    val enabled: Boolean = true
)