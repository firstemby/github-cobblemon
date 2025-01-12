package com.cobblemon.mod.common.entity.sensor

import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.block.NestBlock
import com.cobblemon.mod.common.block.entity.NestBlockEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.sensing.Sensor
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

/**
 * Sensor used to detect nearby nests (NestBlocks or NestBlockEntities).
 *
 * @since January 12th, 2025
 */
class NestSensor : Sensor<LivingEntity>() {
    companion object {
        val OUTPUT_MEMORY_MODULES = setOf(CobblemonMemories.NEST_LOCATION)
        private const val DETECTION_RADIUS = 10.0 // Radius to search for nests
    }

    override fun requires(): Set<MemoryModuleType<*>> = OUTPUT_MEMORY_MODULES

    override fun doTick(world: ServerLevel, entity: LivingEntity) {
        // Ensure the entity is a PokemonEntity
        if (entity !is PokemonEntity) return

        val nearbyNest = findNearbyNest(world, entity.blockPosition())
        if (nearbyNest != null) {
            entity.brain.setMemory(CobblemonMemories.NEST_LOCATION, nearbyNest)
        } else {
            entity.brain.eraseMemory(CobblemonMemories.NEST_LOCATION)
        }
    }

    private fun findNearbyNest(world: ServerLevel, entityPos: BlockPos): BlockPos? {
        return BlockPos.betweenClosed(
            entityPos.offset(-10, -5, -10),
            entityPos.offset(10, 5, 10)
        ).firstOrNull { pos ->
            val blockState: BlockState = world.getBlockState(pos)
            val blockEntity: BlockEntity? = world.getBlockEntity(pos)

            blockState.block is NestBlock || blockEntity is NestBlockEntity
        }
    }
}