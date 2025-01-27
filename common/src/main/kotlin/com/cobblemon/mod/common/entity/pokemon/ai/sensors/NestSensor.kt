package com.cobblemon.mod.common.entity.pokemon.ai.sensors

import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.block.NestBlock
import com.cobblemon.mod.common.block.entity.NestBlockEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.sensing.Sensor
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import kotlin.collections.firstOrNull

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

        val nearbyNest = findNearbyNest((entity as PokemonEntity), world, entity.blockPosition()) // todo send in entity
        if (nearbyNest != null) { // WORKS
            entity.brain.setMemory(CobblemonMemories.NEST_LOCATION, nearbyNest)
        } else {
            entity.brain.eraseMemory(CobblemonMemories.NEST_LOCATION)
        }
    }

    private fun findNearbyNest(pokemonEntity: PokemonEntity, world: ServerLevel, entityPos: BlockPos): BlockPos? {
        return BlockPos.betweenClosed(
            entityPos.offset(-20, -5, -20),
            entityPos.offset(20, 5, 20)
        ).firstOrNull { pos ->
            val blockState: BlockState = world.getBlockState(pos)
            val blockEntity: BlockEntity? = world.getBlockEntity(pos)

            if (blockEntity !is NestBlockEntity) return@firstOrNull false

            // cancel sensor if it is a player sent out pokemon or in a battle
            if (pokemonEntity.isBattling || pokemonEntity.isBattleClone() || (!pokemonEntity.pokemon.isWild() && pokemonEntity.tethering?.tetheringId == null)) return@firstOrNull false

            val nestBlockEntity = blockEntity as NestBlockEntity

            // Check if this Pokémon is a parent of the nest
            if (nestBlockEntity.parents.first == pokemonEntity.uuid || nestBlockEntity.parents.second == pokemonEntity.uuid) {
                pokemonEntity.brain.setMemory(CobblemonMemories.PATERNAL_NEST_LOCATION, pos)
                return@firstOrNull true // Prioritize the paternal nest
            }

            // Clear paternal nest memory if this isn't their nest
            pokemonEntity.brain.eraseMemory(CobblemonMemories.PATERNAL_NEST_LOCATION) // I do worry that this will clear a parental nest if they get too close to another nest todo look into this case

            // Check if the nest is valid based on tethering and ownership
            val isWildNest = nestBlockEntity.ownerUUID == null
            val isOwnedNest = nestBlockEntity.ownerUUID != null
            val isEggless = nestBlockEntity.getItem(0) == ItemStack.EMPTY

            return@firstOrNull when {
                // Pokémon is tethered: Use owned nests
                pokemonEntity.tethering?.tetheringId != null -> isOwnedNest && isEggless
                // Pokémon is not tethered: Use wild nests
                else -> isWildNest && isEggless
            }
        }
    }
}