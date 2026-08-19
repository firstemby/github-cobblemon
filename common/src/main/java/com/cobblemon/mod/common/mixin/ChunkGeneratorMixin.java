/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin;

import com.cobblemon.mod.common.world.feature.PlacedFeatureLocator;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Marks each chunk in {@code CobblemonPlacedFeatureIndex} once its decoration completes, making the index
 * authoritative for that chunk: any locatable feature it doesn't list there verifiably did not generate.
 * Runs at TAIL so placements (recorded per-feature) are always written before the chunk is marked.
 */
@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorMixin {

	@Inject(method = "applyBiomeDecoration", at = @At("TAIL"))
	private void cobblemon$markChunkDecorated(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager, CallbackInfo ci) {
		PlacedFeatureLocator.onChunkDecorated(level.getLevel(), chunk.getPos());
	}
}