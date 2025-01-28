package com.cobblemon.mod.common.entity.pokemon.ai.sensors

import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.pokemon.breeding.BreedingLogic
import com.cobblemon.mod.common.breeding.BreedingLogicManager
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Gender
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.sensing.Sensor
import net.minecraft.world.phys.AABB
import kotlin.collections.firstOrNull
import kotlin.jvm.java
import kotlin.jvm.optionals.getOrNull

/**
 * Sensor used to detect nearby Pokémon that are potential mates.
 *
 * @since January 12th, 2025
 */
class MateSensor : Sensor<LivingEntity>() {
    companion object {
        val OUTPUT_MEMORY_MODULES = setOf(CobblemonMemories.POTENTIAL_MATE)
        private const val DETECTION_RADIUS = 10.0 // Radius to search for potential mates
    }

    override fun requires(): Set<MemoryModuleType<*>> = OUTPUT_MEMORY_MODULES

    override fun doTick(world: ServerLevel, entity: LivingEntity) {
        // Ensure the entity is a PokemonEntity
        if (entity !is PokemonEntity) return

        // make sure they are not on Mate Scan cooldown
        if (entity.brain.getMemory(CobblemonMemories.MATE_SCAN_COOLDOWN).getOrNull() == true) {
            return
        }

        // Make sure the current pokemon has no breeding cooldown
        if ((entity as PokemonEntity).pokemon.breedingCooldown != 0) return

        val potentialMate = world.getEntitiesOfClass(
            PokemonEntity::class.java,
            AABB(entity.blockPosition()).inflate(DETECTION_RADIUS)
        ) { nearbyEntity ->
            nearbyEntity != entity && isValidMate(entity, nearbyEntity)
        }.firstOrNull()

        // mate scan just got done so let's set a cooldown for a next scan
        entity.brain.setMemoryWithExpiry(CobblemonMemories.MATE_SCAN_COOLDOWN, true, 1200)

        if (potentialMate != null) {
            entity.brain.setMemory(CobblemonMemories.POTENTIAL_MATE, potentialMate)  // WORKS
        } else {
            entity.brain.eraseMemory(CobblemonMemories.POTENTIAL_MATE)
        }
    }

    private fun isValidMate(entity: PokemonEntity, mate: PokemonEntity): Boolean {
        val gender = entity.pokemon.gender
        val mateGender = mate.pokemon.gender

        // cancel sensor if it is a player sent out pokemon or in a battle
        if (entity.isBattling || entity.isBattleClone() || (!entity.pokemon.isWild() && entity.tethering?.tetheringId == null)) return false

        //todo delete this later (this is for breakpointing only true returns)
        if (BreedingLogicManager.canBreed(entity.pokemon, mate.pokemon)) {
            val test = 1
            if (BreedingLogicManager.checkCompatibility(entity.pokemon, mate.pokemon)) {
                val test2 = 1
            }
        }

        // make sure the potetntial mate is not on a breeding cooldown
        if (mate.pokemon.breedingCooldown != 0) return false

        val canBreed = BreedingLogicManager.canBreed(entity.pokemon, mate.pokemon)
        if (!canBreed)
            return false
        val isCompatible = BreedingLogicManager.checkCompatibility(entity.pokemon, mate.pokemon)
        val bothWild = (entity.pokemon.isWild() && mate.pokemon.isWild())
        val bothOwned = (!entity.pokemon.isWild() && !mate.pokemon.isWild())

        return when {
            //(entity.pokemon.species.name == "ditto" || mate.pokemon.species.name == "ditto") -> true
            //(gender == Gender.MALE && mateGender == Gender.FEMALE) -> true
            //(gender == Gender.FEMALE && mateGender == Gender.MALE) -> true
            (canBreed && isCompatible && (bothWild || bothOwned)) -> true
            else -> false
        }
    }
}