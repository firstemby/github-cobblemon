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

object SpeciesMoLangFunctions : AbstractMoLangFunctionHolder<Species> {
    override val holder: MutableList<(Species) -> MutableMap<String, (MoParams) -> Any>> = mutableListOf(
        { species ->
            val map = hashMapOf<String, (MoParams) -> Any>()

            map["identifier"] = { StringValue(species.resourceIdentifier.toString()) }
            map["name"] = { StringValue(species.name) }
            map["primary_type"] = { StringValue(species.primaryType.showdownId) }
            map["secondary_type"] = { StringValue(species.secondaryType?.showdownId ?: "null") }
            map["experience_group"] = { StringValue(species.experienceGroup.name) }
            map["height"] = { DoubleValue(species.height) }
            map["weight"] = { DoubleValue(species.weight) }
            map["base_scale"] = { DoubleValue(species.baseScale) }
            map["hitbox_width"] = { DoubleValue(species.hitbox.width) }
            map["hitbox_height"] = { DoubleValue(species.hitbox.height) }
            map["hitbox_fixed"] = { DoubleValue(species.hitbox.fixed) }
            map["catch_rate"] = { DoubleValue(species.catchRate) }
            map["labels"] = put@{ return@put species.labels.asArrayValue { StringValue(it) } }
            map["has_label"] = { params -> DoubleValue(species.labels.contains(params.getString(0))) }
            map
        }
    )
}
