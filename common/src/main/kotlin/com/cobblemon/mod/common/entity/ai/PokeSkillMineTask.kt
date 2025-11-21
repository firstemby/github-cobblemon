package com.cobblemon.mod.common.entity.ai

import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.google.common.collect.ImmutableMap
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.ai.behavior.Behavior
import net.minecraft.world.entity.ai.behavior.BlockPosTracker
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.MemoryStatus
import net.minecraft.world.entity.ai.memory.WalkTarget

class PokeSkillMineTask : Behavior<PokemonEntity>(
    ImmutableMap.of(
        CobblemonMemories.MINE_TARGET, MemoryStatus.VALUE_PRESENT,
        MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
    ),
    Int.MAX_VALUE,
    Int.MAX_VALUE,
) {
    override fun checkExtraStartConditions(level: ServerLevel, entity: PokemonEntity): Boolean {
        val mineTarget = entity.brain.getMemory(CobblemonMemories.MINE_TARGET).orElse(null) ?: return false
        val blockPos = mineTarget.blockPos

        return !level.isEmptyBlock(blockPos)
    }

    override fun canStillUse(level: ServerLevel, entity: PokemonEntity, gameTime: Long): Boolean {
        val mineTarget = entity.brain.getMemory(CobblemonMemories.MINE_TARGET).orElse(null) ?: return false
        val blockPos = mineTarget.blockPos

        return !level.isEmptyBlock(blockPos)
    }

    override fun tick(level: ServerLevel, entity: PokemonEntity, gameTime: Long) {
        val mineTarget = entity.brain.getMemory(CobblemonMemories.MINE_TARGET).orElse(null) ?: return
        val blockPos = mineTarget.blockPos
        val direction = mineTarget.direction
        val walkPos = blockPos.relative(direction)

        entity.brain.setMemory(
            MemoryModuleType.WALK_TARGET,
            WalkTarget(BlockPosTracker(walkPos), 1f, 0)
        )

        val distSq = entity.distanceToSqr(blockPos.center)

        if (distSq <= 5.0) {
            val blocksToMine = get3x3AreaAround(blockPos, direction)
            for (blockToMine in blocksToMine ) {
                val state = level.getBlockState(blockToMine)
                if (!state.isAir) {
                    level.destroyBlock(blockToMine, true, entity)
                }
            }

            entity.brain.eraseMemory(CobblemonMemories.MINE_TARGET)
        }
    }

    fun get3x3AreaAround(pos: BlockPos, face: Direction): List<BlockPos> {
        val list = mutableListOf<BlockPos>()

        when (face) {
            Direction.UP, Direction.DOWN -> {
                // Horizontal 3×3 (x/z)
                for (dx in -1..1)
                    for (dz in -1..1)
                        list += pos.offset(dx, 0, dz)
            }

            Direction.NORTH, Direction.SOUTH -> {
                // Vertical 3×3 (x/y)
                for (dx in -1..1)
                    for (dy in -1..1)
                        list += pos.offset(dx, dy, 0)
            }

            Direction.EAST, Direction.WEST -> {
                // Vertical 3×3 (z/y)
                for (dz in -1..1)
                    for (dy in -1..1)
                        list += pos.offset(0, dy, dz)
            }
        }

        return list
    }
}