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

object SpeciesMoLangFunctions : AbstractMoLangFunctionHolder<Species>() {
    override fun Species.moLangFunctions(): MutableMap<String, (MoParams) -> Any> {
        val species = this
        val map = mutableMapOf<String, (MoParams) -> Any>(
            "identifier" to { StringValue(species.resourceIdentifier.toString()) },
            "name" to { StringValue(species.name) },
            "primary_type" to { StringValue(species.primaryType.showdownId) },
            "secondary_type" to { StringValue(species.secondaryType?.showdownId ?: "null") },
            "experience_group" to { StringValue(species.experienceGroup.name) },
            "height" to { DoubleValue(species.height) },
            "weight" to { DoubleValue(species.weight) },
            "base_scale" to { DoubleValue(species.baseScale) },
            "hitbox_width" to { DoubleValue(species.hitbox.width) },
            "hitbox_height" to { DoubleValue(species.hitbox.height) },
            "hitbox_fixed" to { DoubleValue(species.hitbox.fixed) },
            "catch_rate" to { DoubleValue(species.catchRate) },
            "labels" to { species.labels.asArrayValue { StringValue(it) } },
            "has_label" to { params -> DoubleValue(species.labels.contains(params.getString(0))) }
        )

        return map
    }
}
