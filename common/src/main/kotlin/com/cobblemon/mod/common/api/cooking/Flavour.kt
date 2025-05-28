/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cooking

import net.minecraft.ChatFormatting
import net.minecraft.util.StringRepresentable

/**
 * Represents the different flavors associated with berries and other Pokémon consumables.
 * See the [Bulbapedia](https://bulbapedia.bulbagarden.net/wiki/Flavor) article for more information.
 *
 * @author Licious
 * @since November 28th, 2022
 */
enum class Flavour(val colour: Int, val chatFormatting: ChatFormatting): StringRepresentable {
    SPICY(0x00FF00, ChatFormatting.RED),
    DRY(0xFF0000, ChatFormatting.DARK_AQUA),
    SWEET(0x0000FF, ChatFormatting.LIGHT_PURPLE),
    BITTER(0xFFFF00, ChatFormatting.GREEN),
    SOUR(0xFF00FF, ChatFormatting.YELLOW),
    MILD(0xFF00FF, ChatFormatting.DARK_PURPLE);

    override fun getSerializedName() = name
    companion object {
        val CODEC = StringRepresentable.fromEnum<Flavour> { Flavour.entries.toTypedArray() }
    }
}