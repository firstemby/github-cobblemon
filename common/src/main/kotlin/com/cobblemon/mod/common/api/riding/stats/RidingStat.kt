/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.riding.stats

import com.cobblemon.mod.common.api.berry.Flavor
import com.cobblemon.mod.common.util.lang
import net.minecraft.network.chat.Component

/**
 * A stat that is used for riding Pokémon. The exact implementation of each of these depends on the [com.cobblemon.mod.common.api.riding.controller.RideController].
 *
 * @author Hiroku
 * @since February 17th, 2025
 */
enum class RidingStat(
    val flavour: Flavor
) {
    SPEED(Flavor.SWEET),
    ACCELERATION(Flavor.SPICY),
    SKILL(Flavor.DRY),
    JUMP(Flavor.BITTER),
    STAMINA(Flavor.SOUR);

    companion object {
        fun getByName(name: String) = RidingStat.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }

    val displayName: Component
        get() = lang("riding.stat.${name.lowercase()}.name")
    val description: Component
        get() = lang("riding.stat.${name.lowercase()}.desc")
}