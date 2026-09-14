/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.model

import com.cobblemon.mod.common.api.cinematic.model.sources.FovSource
import com.cobblemon.mod.common.api.cinematic.model.sources.Lens
import com.cobblemon.mod.common.api.cinematic.model.sources.PositionSource
import com.cobblemon.mod.common.api.cinematic.model.sources.RollSource
import com.cobblemon.mod.common.api.cinematic.model.sources.RotationSource
import com.cobblemon.mod.common.api.cinematic.model.sources.ShotModifier

data class Shot(
    val id: String,
    val position: PositionSource,
    val rotation: RotationSource,
    val fov: FovSource? = null,
    val roll: RollSource? = null,
    val lens: Lens? = null,
    val modifiers: List<ShotModifier> = emptyList()
) {
    init {
        require(id.isNotBlank()) { "Shot id must be non-blank" }
    }
}
