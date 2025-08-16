/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai

import com.bedrockk.molang.runtime.value.DoubleValue
import com.cobblemon.mod.common.api.molang.ObjectValue
import com.cobblemon.mod.common.util.getDoubleOrNull

class CobblemonAvoidTargetData {
    var walkSpeed: Float? = null

    @Transient
    val struct = ObjectValue<CobblemonAvoidTargetData>(this).also {
        it.addFunction("walk_speed") { DoubleValue(walkSpeed) }
        it.addFunction("set_walk_speed") { walkSpeed = it.getDoubleOrNull(0)?.toFloat() }
    }
}