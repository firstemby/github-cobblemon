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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pools.*;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(JigsawPlacement.class)
public class JigsawPlacementMixin {

	@Unique
	private static final Logger cobblemon$LOGGER = LoggerFactory.getLogger("Cobblemon/HeightRangeVeto");

	@Inject(
			method = "addPieces(Lnet/minecraft/world/level/levelgen/structure/Structure$GenerationContext;Lnet/minecraft/core/Holder;Ljava/util/Optional;ILnet/minecraft/core/BlockPos;ZLjava/util/Optional;ILnet/minecraft/world/level/levelgen/structure/pools/alias/PoolAliasLookup;Lnet/minecraft/world/level/levelgen/structure/pools/DimensionPadding;Lnet/minecraft/world/level/levelgen/structure/templatesystem/LiquidSettings;)Ljava/util/Optional;",
			at = @At("RETURN"),
			cancellable = true
	)
	private static void onAddPieces(
			Structure.GenerationContext context,
			Holder<StructureTemplatePool> startPool,
			Optional<ResourceLocation> startJigsawName,
			int maxDepth,
			BlockPos pos,
			boolean useExpansionHack,
			Optional<Heightmap.Types> projectStartToHeightmap,
			int maxDistanceFromCenter,
			PoolAliasLookup aliasLookup,
			DimensionPadding dimensionPadding,
			LiquidSettings liquidSettings,
			CallbackInfoReturnable<Optional<Structure.GenerationStub>> cir
	) {
		Optional<Structure.GenerationStub> result = cir.getReturnValue();
		if (result.isEmpty()) {
			return;
		}

		BlockPos spawnPos = result.get().position();
		String poolId = startPool.unwrapKey().map(key -> key.location().toString()).orElse("unknown");
		boolean canPlace = cobblemon$canStructurePlace(context, startPool, spawnPos);

		cobblemon$LOGGER.info(
				"[HeightRangeVeto] addPieces evaluated requestedPos={} spawnPos={} startPool={} -> canPlace={}",
				pos, spawnPos, poolId, canPlace
		);

		if (canPlace) {
			return;
		}

		cobblemon$LOGGER.info(
				"[HeightRangeVeto] Vetoing structure start at {} (startPool={})",
				spawnPos, poolId
		);

		cir.setReturnValue(Optional.empty());
	}

	@Unique
	private static boolean cobblemon$canStructurePlace(
			Structure.GenerationContext generationContext,
			Holder<StructureTemplatePool> startPool,
			BlockPos startPos
	) {
		for (StructurePoolElement element : startPool.value().templates) {
			Optional<HeightRangeStructureProcessor> heightRangeProcessor = cobblemon$getHeightRangeProcessor(element);

			if (heightRangeProcessor.isEmpty()) {
				continue;
			}

			HeightRangeStructureProcessor processor = heightRangeProcessor.get();
			boolean valid = processor.isValidPlacement(
					generationContext.chunkGenerator(),
					generationContext.heightAccessor(),
					generationContext.randomState(),
					startPos
			);

			cobblemon$LOGGER.info(
					"[HeightRangeVeto] template={} pos={} valid={}",
					element, startPos, valid
			);

			if (!valid) {
				return false;
			}
		}

		return true;
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