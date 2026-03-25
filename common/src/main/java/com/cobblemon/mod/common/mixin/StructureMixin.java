/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin;

import com.cobblemon.mod.common.world.CobblemonStructureIDs;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.apache.commons.lang3.IntegerRange;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

@Mixin(Structure.class)
public abstract class StructureMixin {
	// Define a set of structures that should not spawn below Sea Level
	@Unique
	private static final Map<ResourceLocation, IntegerRange> RESTRICTED_STRUCTURES = Map.of(
			// The IntegerRange values are calculated FROM Sea Level. (Sealevel + VALUE)
			CobblemonStructureIDs.STONJOURNER_HENGE, IntegerRange.of(0, 50),
			CobblemonStructureIDs.LUNA_HENGE, IntegerRange.of(0, 50),
			CobblemonStructureIDs.SOL_HENGE, IntegerRange.of(0, 50)
	);

	@Inject(method = "generate", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/structure/StructureStart;isValid()Z"),
			cancellable = true)
	public void cobblemon$isValid(RegistryAccess registryAccess, ChunkGenerator chunkGenerator, BiomeSource biomeSource,
	                              RandomState randomState, StructureTemplateManager structureTemplateManager, long seed,
	                              ChunkPos chunkPos, int references, LevelHeightAccessor heightAccessor,
	                              Predicate<Holder<Biome>> validBiome,
	                              CallbackInfoReturnable<StructureStart> cir,
	                              @Local StructureStart structureStart) {
		ResourceLocation structureKey = registryAccess.registryOrThrow(Registries.STRUCTURE).getKey((Structure) (Object) this);
		if (!RESTRICTED_STRUCTURES.containsKey(structureKey)) {
			return;
		}
		int sealevel = chunkGenerator.getSeaLevel();
		IntegerRange range = RESTRICTED_STRUCTURES.get(structureKey);

		if (range != null) {
			int minY = sealevel + range.getMinimum();
			int maxY = sealevel + range.getMaximum();

			for (StructurePiece piece : structureStart.getPieces()) {
				int pieceMinY = piece.getBoundingBox().minY();

				if (pieceMinY < minY || pieceMinY > maxY) {
					cir.setReturnValue(StructureStart.INVALID_START);
					return;
				}
			}
		}
	}
}