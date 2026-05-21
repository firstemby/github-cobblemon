/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.habitats.dto

import com.cobblemon.mod.common.api.habitats.spawningstyle.NaturalHabitatSpawning
import net.minecraft.network.RegistryFriendlyByteBuf

class NaturalHabitatSettingsDTO() {
    var replaceSpawns = false
    var rangeOfInfluence = 16

    constructor(naturalHabitatSpawning: NaturalHabitatSpawning): this() {
        this.replaceSpawns = naturalHabitatSpawning.replaceSpawns
        this.rangeOfInfluence = naturalHabitatSpawning.rangeOfInfluence
    }

    fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeBoolean(replaceSpawns)
        buffer.writeInt(rangeOfInfluence)
    }

    fun decode(buffer: RegistryFriendlyByteBuf, ) {
        this.replaceSpawns = buffer.readBoolean()
        this.rangeOfInfluence = buffer.readInt()
    }
}