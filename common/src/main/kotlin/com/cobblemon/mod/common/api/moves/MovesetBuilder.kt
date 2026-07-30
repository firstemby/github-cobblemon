/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.moves

import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.resources.ResourceLocation

/**
 * Builders a moveset for a particular [FormData] and level. Probably only really need [DefaultMovesetBuilder] though.
 *
 * @author Hiroku
 * @since December 5th, 2025
 */
interface MovesetBuilder {
    /** The ID of this builder. It's autofilled from the registry loader. */
    var id: ResourceLocation
    /** The subtype designation for this moveset builder. Generally unused once read from JSON. */
    val type: ResourceLocation

    /** Builds a brand new moveset for the [form] and [level]. */
    fun build(form: FormData, level: Int): MoveSet

    companion object {
        /** Weight multiplier for moves that are in the form's signatureMoves list. Makes them way more likely to be chosen. */
        var signatureMoveWeightMultiplier = 5F
        /** Subtypes of MovesetBuilder. I personally doubt you'll need any other subtypes, but hey. */
        val movesetBuilderTypes = mutableMapOf<ResourceLocation, Class<out MovesetBuilder>>(
            cobblemonResource("default") to DefaultMovesetBuilder::class.java
        )
    }
}