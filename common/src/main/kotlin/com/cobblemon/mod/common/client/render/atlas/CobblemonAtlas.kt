/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.atlas

import net.minecraft.client.renderer.texture.TextureAtlas
import net.minecraft.resources.Identifier

class CobblemonAtlas(
    val atlasId: Identifier,
    val sourcePath: Identifier
) {
    var textureAtlas: TextureAtlas? = null
        private set

    fun bind(textureAtlas: TextureAtlas): Boolean {
        if (textureAtlas.location() != atlasId) {
            return false
        }

        this.textureAtlas = textureAtlas
        return true
    }

    fun getSprite(identifier: Identifier) = textureAtlas?.getSprite(identifier)
}
