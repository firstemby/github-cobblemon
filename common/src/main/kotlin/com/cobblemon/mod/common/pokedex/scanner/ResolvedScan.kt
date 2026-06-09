/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokedex.scanner

import com.cobblemon.mod.common.api.pokedex.scan.BlockScanDefinition
import net.minecraft.resources.ResourceLocation

/**
 * A scannable target's display + recording data, resolved uniformly from either a block entity
 * (via its [com.cobblemon.mod.common.pokedex.scanner.block.BlockScanProvider]) or a
 * [com.cobblemon.mod.common.entity.scan.ScanNodeEntity]. Lets the scanner UI and recording path
 * treat both sources identically.
 */
data class ResolvedScan(
    val frames: List<BlockScanDefinition.Frame>,
    val values: Map<String, Any?>,
    val bucket: String,
    val scanKey: ResourceLocation
)
