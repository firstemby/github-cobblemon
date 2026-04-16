/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.atlas

import com.cobblemon.mod.common.util.cobblemonResource

object CobblemonAtlases {
    val atlases = mutableSetOf<CobblemonAtlas>()

    val BERRY_SPRITE_ATLAS = register("textures/atlas/berries.png", "berries")
    fun register(atlasId: String, sourcePath: String): CobblemonAtlas {
        val atlas = CobblemonAtlas(
            cobblemonResource(atlasId),
            cobblemonResource(sourcePath)
        )
        atlases.add(atlas)
        return atlas
    }

}
