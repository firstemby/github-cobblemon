package com.cobblemon.mod.common.entity.pokemon.ai.sensors

import com.cobblemon.mod.common.CobblemonBlocks
import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.ai.sensing.Sensor
import net.minecraft.world.level.block.RotatedPillarBlock

class SaccharineLogSensor : Sensor<PokemonEntity>(200) {
    override fun requires() = setOf(CobblemonMemories.SACCHARINE_LOG_LOCATION, CobblemonMemories.HAS_HONEY)

    override fun doTick(world: ServerLevel, entity: PokemonEntity) {
        val brain = entity.brain

        // Only search if we're carrying honey
        if (!brain.hasMemoryValue(CobblemonMemories.HAS_HONEY)) {
            brain.eraseMemory(CobblemonMemories.SACCHARINE_LOG_LOCATION)
            return
        }

        val current = brain.getMemory(CobblemonMemories.SACCHARINE_LOG_LOCATION).orElse(null)
        if (current != null && isValidLog(world, entity, current)) return
        if (current != null) brain.eraseMemory(CobblemonMemories.SACCHARINE_LOG_LOCATION)

        val searchRadius = 16
        val center = entity.blockPosition()
        var best: BlockPos? = null
        var bestDist = Double.MAX_VALUE

        BlockPos.betweenClosedStream(
            center.offset(-searchRadius, -2, -searchRadius),
            center.offset(searchRadius, 2, searchRadius)
        ).forEach { pos ->
            val state = world.getBlockState(pos)
            if (state.block == CobblemonBlocks.SACCHARINE_LOG && // plain log only
                state.getValue(RotatedPillarBlock.AXIS) == Direction.Axis.Y &&
                hasReachableFace(world, entity, pos)
            ) {
                val d = pos.distToCenterSqr(center.x + 0.5, center.y + 0.5, center.z + 0.5)
                if (d < bestDist) {
                    bestDist = d
                    best = pos.immutable()
                }
            }
        }

        if (best != null) brain.setMemory(CobblemonMemories.SACCHARINE_LOG_LOCATION, best)
    }

    private fun isValidLog(world: ServerLevel, entity: PokemonEntity, pos: BlockPos): Boolean {
        val state = world.getBlockState(pos)
        return state.block == CobblemonBlocks.SACCHARINE_LOG &&
                state.getValue(RotatedPillarBlock.AXIS) == Direction.Axis.Y &&
                hasReachableFace(world, entity, pos)
    }

    private fun hasReachableFace(world: ServerLevel, entity: PokemonEntity, pos: BlockPos): Boolean {
        for (dir in Direction.Plane.HORIZONTAL) {
            val at = pos.relative(dir)
            if (world.isEmptyBlock(at)) {
                val path = entity.navigation.createPath(at, 0)
                if (path != null && path.canReach()) return true
            }
        }
        for (dir in arrayOf(Direction.UP, Direction.DOWN)) {
            val at = pos.relative(dir)
            if (world.isEmptyBlock(at)) {
                val path = entity.navigation.createPath(at, 0)
                if (path != null && path.canReach()) return true
            }
        }
        return false
    }
}