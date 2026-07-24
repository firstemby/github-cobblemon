/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.spawning.detail

import com.cobblemon.mod.common.api.spawning.position.SpawnablePosition
import com.cobblemon.mod.common.api.spawning.selection.SpawnSelectionData
import com.cobblemon.mod.common.entity.projectile.FallingStarResultType

class FallingStarSpawnDetail : SpawnDetail() {
    companion object {
        const val TYPE = "falling_star"
    }

    override val type = TYPE
    var resultType: FallingStarResultType = FallingStarResultType.MINIOR

    override fun createSpawnAction(
        spawnablePosition: SpawnablePosition,
        bucket: String,
        selectionData: SpawnSelectionData
    ) = FallingStarSpawnAction(spawnablePosition, bucket, this)
}
