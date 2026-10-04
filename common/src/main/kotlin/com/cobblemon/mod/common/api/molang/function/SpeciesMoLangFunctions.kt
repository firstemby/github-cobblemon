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
        val functions = hashMapOf<String, (MoParams) -> Any>()

        functions["identifier"] = { StringValue(species.resourceIdentifier.toString()) }
        functions["name"] = { StringValue(species.name) }
        functions["primary_type"] = { StringValue(species.primaryType.showdownId) }
        functions["secondary_type"] = { StringValue(species.secondaryType?.showdownId ?: "null") }
        functions["experience_group"] = { StringValue(species.experienceGroup.name) }
        functions["height"] = { DoubleValue(species.height) }
        functions["weight"] = { DoubleValue(species.weight) }
        functions["base_scale"] = { DoubleValue(species.baseScale) }
        functions["hitbox_width"] = { DoubleValue(species.hitbox.width) }
        functions["hitbox_height"] = { DoubleValue(species.hitbox.height) }
        functions["hitbox_fixed"] = { DoubleValue(species.hitbox.fixed) }
        functions["catch_rate"] = { DoubleValue(species.catchRate) }
        functions["labels"] = { species.labels.asArrayValue { StringValue(it) } }
        functions["has_label"] = { params -> DoubleValue(species.labels.contains(params.getString(0))) }

        return functions
    }
}
