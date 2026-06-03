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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.LegacySinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Optional;

@Mixin(JigsawPlacement.class)
public class JigsawPlacementMixin {

	@Inject(
			method = "addPieces*",
			at = @At("HEAD"),
			cancellable = true
	)
	private static void cobblemon$cancelInvalidHeightRangeStart(
			CallbackInfoReturnable<Optional<Structure.GenerationStub>> cir,
			@Local(argsOnly = true, ordinal = 0) Structure.GenerationContext generationContext,
			@Local(argsOnly = true, ordinal = 0) Holder<StructureTemplatePool> startPool,
			@Local(argsOnly = true, ordinal = 0) BlockPos startPos
	) {
		if (cobblemon$canAnyStartElementPlace(generationContext, startPool, startPos)) {
			return;
		}

		cir.setReturnValue(Optional.empty());
	}

	@Unique
	private static boolean cobblemon$canAnyStartElementPlace(
			Structure.GenerationContext generationContext,
			Holder<StructureTemplatePool> startPool,
			BlockPos startPos
	) {
		List<StructurePoolElement> elements = startPool.value().getShuffledTemplates(RandomSource.create(0L));
		for (StructurePoolElement element : elements) {
			Optional<HeightRangeStructureProcessor> heightRangeProcessor = cobblemon$getHeightRangeProcessor(element);
			if (heightRangeProcessor.isEmpty()) {
				return true;
			}

			if (heightRangeProcessor.get().isValidPlacement(
					generationContext.chunkGenerator(),
					generationContext.heightAccessor(),
					generationContext.randomState(),
					startPos
			)) {
				return true;
			}
		}

		return false;
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

