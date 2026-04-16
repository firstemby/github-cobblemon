/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.item.berry

import com.cobblemon.mod.common.block.BerryBlock
import net.minecraft.world.item.BlockItem

open class BerryItem(private val berryBlock: BerryBlock, properties: Properties) : BlockItem(berryBlock, properties.useItemDescriptionPrefix()) {

    fun berry() = this.berryBlock.berry()
}
