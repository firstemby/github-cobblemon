package com.cobblemon.mod.common.entity.pokemon.ai.sensors

import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.block.NestBlock
import com.cobblemon.mod.common.block.entity.NestBlockEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.sensing.Sensor
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import kotlin.collections.firstOrNull

/**
 * Sensor used to detect nearby nests (NestBlocks or NestBlockEntities).
 *
 * @since January 12th, 2025
 */
class NestSensor : Sensor<LivingEntity>(200) {
    companion object {
        val OUTPUT_MEMORY_MODULES = setOf(CobblemonMemories.NEST_LOCATION)
        private const val DETECTION_RADIUS = 10.0 // Radius to search for nests
    }

    override fun requires(): Set<MemoryModuleType<*>> = OUTPUT_MEMORY_MODULES

    override fun doTick(world: ServerLevel, entity: LivingEntity) {
        if (entity !is PokemonEntity) return

        val paternalNestLocation = entity.brain.getMemory(CobblemonMemories.PATERNAL_NEST_LOCATION).orElse(null)

        // Validate the paternal nest location
        if (paternalNestLocation != null) {
            val blockAtLocation = world.getBlockEntity(paternalNestLocation)
            if (blockAtLocation !is NestBlockEntity) {
                entity.brain.eraseMemory(CobblemonMemories.PATERNAL_NEST_LOCATION)
                entity.brain.eraseMemory(CobblemonMemories.EGG_IN_NEST)
                entity.brain.eraseMemory(CobblemonMemories.EGG_LOCATION)
            }
            else if (blockAtLocation.parents.first == entity.uuid || blockAtLocation.parents.second == entity.uuid) {
                entity.brain.setMemory(CobblemonMemories.PATERNAL_NEST_LOCATION, paternalNestLocation) // Crab Note: This line is probably redundant and not needed
                entity.brain.setMemory(CobblemonMemories.NEST_LOCATION, paternalNestLocation) // make sure the Nest Location gets synced too

                // check for egg status in nest
                if (blockAtLocation.getItem().item == ItemStack.EMPTY.item){
                    entity.brain.setMemory(CobblemonMemories.EGG_IN_NEST, false)
                    //entity.brain.eraseMemory(CobblemonMemories.EGG_LOCATION)
                }
                else {
                    entity.brain.setMemory(CobblemonMemories.EGG_IN_NEST, true)
                    //entity.brain.setMemory(CobblemonMemories.EGG_LOCATION, paternalNestLocation)
                }
                return
            } else {
                entity.brain.eraseMemory(CobblemonMemories.PATERNAL_NEST_LOCATION)
                entity.brain.setMemory(CobblemonMemories.EGG_IN_NEST, false)
                entity.brain.eraseMemory(CobblemonMemories.EGG_LOCATION)
            }
        }

        val nearbyNest = findNearbyNest((entity as PokemonEntity), world, entity.blockPosition()) // todo send in entity?
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

            /*// Check if this Pokémon is a parent of the nest
            if (nestBlockEntity.parents.first == pokemonEntity.uuid || nestBlockEntity.parents.second == pokemonEntity.uuid) {
                pokemonEntity.brain.setMemory(CobblemonMemories.PATERNAL_NEST_LOCATION, pos)
                return@firstOrNull true // Prioritize the paternal nest
            }

            // Clear paternal nest memory if this isn't their nest
            pokemonEntity.brain.eraseMemory(CobblemonMemories.PATERNAL_NEST_LOCATION)*/ // I do worry that this will clear a parental nest if they get too close to another nest todo look into this case

            // cancel if the nest cannot be reached by the entity
            if (!canPathfindToTarget(pokemonEntity, nestBlockEntity.blockPos)) return@firstOrNull false

            // Check if the nest is valid based on tethering and ownership
            val isWildNest = nestBlockEntity.ownerUUID == null
            val isOwnedNest = nestBlockEntity.ownerUUID != null
            val isClaimed = nestBlockEntity.nestClaimedBy.first != null || nestBlockEntity.nestClaimedBy.second != null
            val isEggless = nestBlockEntity.getItem(0) == ItemStack.EMPTY
            val WATER_EGG_GROUPS = setOf("water_1", "water_2", "water_3")
            val eggGroups = pokemonEntity.pokemon.species.eggGroups.map { it.name.lowercase() }
            val hasWaterEggGroup = eggGroups.any { it in WATER_EGG_GROUPS }
            val onlyWaterEggGroup = eggGroups.all { it in WATER_EGG_GROUPS }
            val isNestWaterlogged = nestBlockEntity.blockState.getOptionalValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED).orElse(false)
            val nestClaimerOne = nestBlockEntity.nestClaimedBy.first?.let { world.getEntity(it) }
            val nestClaimerTwo = nestBlockEntity.nestClaimedBy.second?.let { world.getEntity(it) }
            val isClaimerOne = pokemonEntity.uuid == nestClaimerOne?.uuid
            val isClaimerTwo = pokemonEntity.uuid == nestClaimerTwo?.uuid
            val nestParentOneActive = nestBlockEntity.parents.first?.let { world.getEntity(it) }
            val nestParentTwoActive = nestBlockEntity.parents.second?.let { world.getEntity(it) }


            // check if nest without an egg has paternal wild pokemon that are presently roaming or claimed already
            if (isWildNest && isEggless && ((nestParentOneActive != null || nestParentTwoActive != null) || (isClaimed && (nestClaimerOne != null && !isClaimerOne && nestClaimerTwo != null && !isClaimerTwo)))) {
                return@firstOrNull false
            }

                    // make it so that only waterlogged nests can be used by pokemon with Water 1/2/3 egg groups
            if (isNestWaterlogged && !hasWaterEggGroup)
                return@firstOrNull false

            // make it so that pokemon that only have Water 1/2/3 egg groups and nothing else can only breed in watterlogged nests
            if (!isNestWaterlogged && onlyWaterEggGroup)
                return@firstOrNull false

            return@firstOrNull when {
                // Pokémon is tethered: Use owned nests
                pokemonEntity.tethering?.tetheringId != null -> isOwnedNest && isEggless
                // Pokémon is not tethered: Use wild nests
                else -> isWildNest && isEggless
            }
        }
    }

    private fun canPathfindToTarget(entity: PathfinderMob, targetPos: BlockPos): Boolean {
        val path = entity.navigation.createPath(targetPos, 0)
        return path != null && path.distToTarget < 1.75f
    }
}