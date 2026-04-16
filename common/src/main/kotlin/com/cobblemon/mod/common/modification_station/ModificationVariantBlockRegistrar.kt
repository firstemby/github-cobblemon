/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.modification_station

import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.Block

interface ModificationVariantBlockRegistrar {
    fun registerAll(consumer: (ResourceLocation, Block) -> Unit)
    fun allBlocks(): Collection<Block>
    fun allEntries(): Map<ResourceLocation, Block>
    fun variantIdentifier(block: Block): ResourceLocation?
}
