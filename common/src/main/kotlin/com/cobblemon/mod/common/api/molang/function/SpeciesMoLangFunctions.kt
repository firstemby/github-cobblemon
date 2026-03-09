/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molang.function

import com.bedrockk.molang.runtime.MoParams
import com.bedrockk.molang.runtime.value.DoubleValue
import com.bedrockk.molang.runtime.value.StringValue
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemon.mod.common.util.asArrayValue

object SpeciesMoLangFunctions {
    val holder: MutableList<(Species) -> HashMap<String, (MoParams) -> Any>> = mutableListOf(
        { species ->
            val map = hashMapOf<String, (MoParams) -> Any>()

            map.put("identifier") { StringValue(species.resourceIdentifier.toString()) }
            map.put("name") { StringValue(species.name) }
            map.put("primary_type") { StringValue(species.primaryType.showdownId) }
            map.put("secondary_type") { StringValue(species.secondaryType?.showdownId ?: "null") }
            map.put("experience_group") { StringValue(species.experienceGroup.name) }
            map.put("height") { DoubleValue(species.height) }
            map.put("weight") { DoubleValue(species.weight) }
            map.put("base_scale") { DoubleValue(species.baseScale) }
            map.put("hitbox_width") { DoubleValue(species.hitbox.width) }
            map.put("hitbox_height") { DoubleValue(species.hitbox.height) }
            map.put("hitbox_fixed") { DoubleValue(species.hitbox.fixed) }
            map.put("catch_rate") { DoubleValue(species.catchRate) }
            map.put("labels") { return@put species.labels.asArrayValue { StringValue(it) } }
            map.put("has_label") { params -> DoubleValue(species.labels.contains(params.getString(0))) }
            map
        }
    )
}
