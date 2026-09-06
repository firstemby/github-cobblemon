/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin;

import com.cobblemon.mod.common.mixin.accessor.SinglePoolElementAccessor;
import com.cobblemon.mod.common.world.structureprocessors.HeightRangeStructureProcessor;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.function.Predicate;

@Mixin(Structure.class)
public abstract class StructureMixin {

	/**
	 * Discards a structure start when any of its pieces carries a {@link HeightRangeStructureProcessor}
	 * whose range the piece does not fit in.
	 * Crab Note: We have to make this has to happen here rather than in the processor itself. Terrain adaptation and {@code /locate}
	 * both read the start, so by the time processors run the terrain has already been generated and the
	 * structure is already on the map which previously were left behdind when we were trying to make some structures not generate via processor. Now
	 * Returning {@link StructureStart#INVALID_START} keeps the start from ever existing, which makes sure that weird generation isn't left behind.
	 */
	@Inject(
			method = "generate",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/structure/StructureStart;isValid()Z"),
			cancellable = true
	)
	private void cobblemon$rejectStartsOutsideHeightRange(
			RegistryAccess registryAccess, ChunkGenerator chunkGenerator, BiomeSource biomeSource,
			RandomState randomState, StructureTemplateManager structureTemplateManager, long seed,
			ChunkPos chunkPos, int references, LevelHeightAccessor heightAccessor,
			Predicate<Holder<Biome>> validBiome,
			CallbackInfoReturnable<StructureStart> cir,
			@Local StructureStart structureStart
	) {
		for (StructurePiece piece : structureStart.getPieces()) {
			if (!(piece instanceof PoolElementStructurePiece poolPiece)) {
				continue;
			}

			Optional<HeightRangeStructureProcessor> heightRangeProcessor = cobblemon$getHeightRangeProcessor(poolPiece.getElement());
			if (heightRangeProcessor.isEmpty()) {
				continue;
			}

			boolean valid = heightRangeProcessor.get().isValidPlacement(
					chunkGenerator,
					heightAccessor,
					randomState,
					piece.getBoundingBox()
			);

			if (!valid) {
				cir.setReturnValue(StructureStart.INVALID_START);
				return;
			}
		}
	}

	@Unique
	private static Optional<HeightRangeStructureProcessor> cobblemon$getHeightRangeProcessor(StructurePoolElement element) {
		if (!(element instanceof SinglePoolElement)) {
			return Optional.empty();
		}

		StructureProcessorList processorList = ((SinglePoolElementAccessor) element).getProcessors().value();
		for (StructureProcessor processor : processorList.list()) {
			if (processor instanceof HeightRangeStructureProcessor heightRangeStructureProcessor) {
				return Optional.of(heightRangeStructureProcessor);
			}
		}

		return Optional.empty();
	}
}