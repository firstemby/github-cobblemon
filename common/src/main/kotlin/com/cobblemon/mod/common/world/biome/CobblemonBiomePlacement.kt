/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.world.biome

import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.biome.Climate

/**
 * Where a biome should be placed: which multi-noise preset (e.g. minecraft:overworld,
 * minecraft:nether) and at which climate parameter point.
 */
data class CobblemonBiomePlacement(
	val preset: ResourceLocation,
	val point: Climate.ParameterPoint
)
