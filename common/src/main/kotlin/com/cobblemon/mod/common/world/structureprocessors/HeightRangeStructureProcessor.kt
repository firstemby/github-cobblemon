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
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate

class HeightRangeStructureProcessor(
	val minOffset: Int,
	val maxOffset: Int,
	val reference: HeightReference = HeightReference.SEA_LEVEL
) : StructureProcessor() {

	override fun processBlock(
		world: LevelReader,
		pos: BlockPos,
		pivot: BlockPos,
		originalBlockInfo: StructureTemplate.StructureBlockInfo,
		currentBlockInfo: StructureTemplate.StructureBlockInfo,
		data: StructurePlaceSettings
	): StructureTemplate.StructureBlockInfo? {
		val referenceY = reference.getY(world, pos)
		val minY = referenceY + minOffset
		val maxY = referenceY + maxOffset

		return if (pos.y in minY..maxY) {
			currentBlockInfo
		} else {
			null
		}
	}

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

		@Suppress("DEPRECATION")
		fun getY(world: LevelReader, pos: BlockPos): Int {
			return if (this == SEA_LEVEL) {
				world.seaLevel
			} else {
				world.getHeight(type ?: Heightmap.Types.WORLD_SURFACE_WG, pos.x, pos.z)
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
