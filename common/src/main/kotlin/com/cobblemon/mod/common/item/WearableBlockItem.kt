/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.item

import net.minecraft.world.item.ItemNameBlockItem
import net.minecraft.world.level.block.Block

class WearableBlockItem(override val name: String, block: Block): ItemNameBlockItem(block, Properties()), WearableItem{

}