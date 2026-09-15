/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.integration

import juuxel.adorn.block.variant.BlockVariant
import juuxel.adorn.block.variant.BlockVariantSet

class AdornCompatibility: BlockVariantSet {
    override fun getWoodVariants(): List<BlockVariant?>? {
        val result: MutableList<BlockVariant> = mutableListOf()
        result.add(BlockVariant.Wood("cobblemon/apricorn"))
        result.add(BlockVariant.Wood("cobblemon/saccharine"))
        return result
    }
}