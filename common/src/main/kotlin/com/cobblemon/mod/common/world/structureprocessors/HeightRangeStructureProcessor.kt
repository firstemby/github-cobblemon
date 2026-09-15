/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.world.structureprocessors

import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.BlockPos
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.LevelHeightAccessor
import net.minecraft.world.level.chunk.ChunkGenerator
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.levelgen.RandomState
import net.minecraft.world.level.levelgen.structure.BoundingBox
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate

/**
 * Restricts the height range a structure piece is allowed to generate in, relative to a [HeightReference].
 *
 * The range is enforced while the structure start is still being built,
 * by `StructureMixin`, which gets rid of the entire start when any piece falls outside of its range. Voiding
 * blocks here instead would run after terrain adaptation has already deformed the terrain, leaving behind
 * random pockets of land and the `/locate` command would incorrectly find a structure that was never placed.
 */
class HeightRangeStructureProcessor(
	val minOffset: Int,
	val maxOffset: Int,
	val reference: HeightReference = HeightReference.SEA_LEVEL
) : StructureProcessor() {

	fun isValidPlacement(
		chunkGenerator: ChunkGenerator,
		heightAccessor: LevelHeightAccessor,
		randomState: RandomState,
		box: BoundingBox
	): Boolean {
		val referenceY = reference.getY(chunkGenerator, heightAccessor, randomState, box.center)
		return box.minY() in (referenceY + minOffset)..(referenceY + maxOffset)
	}

	override fun processBlock(
		world: LevelReader,
		pos: BlockPos,
		pivot: BlockPos,
		originalBlockInfo: StructureTemplate.StructureBlockInfo,
		currentBlockInfo: StructureTemplate.StructureBlockInfo,
		data: StructurePlaceSettings
	): StructureTemplate.StructureBlockInfo = currentBlockInfo

	override fun getType() = CobblemonProcessorTypes.HEIGHT_RANGE

	enum class HeightReference(private val id: String, private val type: Heightmap.Types?) {
		SEA_LEVEL("sea_level", null),
		WORLD_SURFACE("world_surface", Heightmap.Types.WORLD_SURFACE),
		WORLD_SURFACE_WG("world_surface_wg", Heightmap.Types.WORLD_SURFACE_WG),
		OCEAN_FLOOR("ocean_floor", Heightmap.Types.OCEAN_FLOOR),
		OCEAN_FLOOR_WG("ocean_floor_wg", Heightmap.Types.OCEAN_FLOOR_WG),
		MOTION_BLOCKING("motion_blocking", Heightmap.Types.MOTION_BLOCKING),
		MOTION_BLOCKING_NO_LEAVES("motion_blocking_no_leaves", Heightmap.Types.MOTION_BLOCKING_NO_LEAVES);

		fun getSerializedName(): String = id

		fun getY(
			chunkGenerator: ChunkGenerator,
			heightAccessor: LevelHeightAccessor,
			randomState: RandomState,
			pos: BlockPos
		): Int {
			return if (this == SEA_LEVEL) {
				chunkGenerator.seaLevel
			} else {
				chunkGenerator.getBaseHeight(pos.x, pos.z, type ?: Heightmap.Types.WORLD_SURFACE_WG, heightAccessor, randomState)
			}
		}

		companion object {
			val CODEC: Codec<HeightReference> = Codec.STRING.xmap(
				{ serialized -> entries.firstOrNull { it.id == serialized } ?: SEA_LEVEL },
				HeightReference::getSerializedName
			)
		}
	}

	companion object {
		val CODEC: MapCodec<HeightRangeStructureProcessor> = RecordCodecBuilder.mapCodec { instance ->
			instance.group(
				Codec.INT.fieldOf("min_offset").forGetter { it.minOffset },
				Codec.INT.fieldOf("max_offset").forGetter { it.maxOffset },
				HeightReference.CODEC.optionalFieldOf("reference", HeightReference.SEA_LEVEL).forGetter { it.reference }
			).apply(instance, ::HeightRangeStructureProcessor)
		}
	}
}