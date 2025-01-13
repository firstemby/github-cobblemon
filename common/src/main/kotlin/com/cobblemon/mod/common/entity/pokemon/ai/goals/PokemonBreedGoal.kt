package com.cobblemon.mod.common.entity.pokemon.ai.goals

import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.phys.Vec3
import java.util.EnumSet

/**
 * A custom goal that allows Pokémon to find a mate, path to them, and then move to a nest to breed.
 *
 * @since January 12th, 2025
 */
class PokemonBreedGoal(private val entity: PokemonEntity) : Goal() {
    private var mate: PokemonEntity? = null
    private var nestLocation: BlockPos? = null
    private var breedingStep = BreedingStep.FIND_MATE

    init {
        flags = EnumSet.of(Flag.MOVE)
    }

    companion object {
        /**
         * Factory method to create a new instance of [PokemonBreedGoal].
         *
         * @param entity The Pokémon entity to assign the goal to.
         * @return A new instance of [PokemonBreedGoal].
         */
        fun create(entity: PokemonEntity): PokemonBreedGoal {
            return PokemonBreedGoal(entity)
        }
    }

    override fun canUse(): Boolean {
        val brain = entity.brain
        if (!brain.hasMemoryValue(CobblemonMemories.POTENTIAL_MATE) ||
            !brain.hasMemoryValue(CobblemonMemories.NEST_LOCATION)
        ) {
            return false
        }

        mate = brain.getMemory(CobblemonMemories.POTENTIAL_MATE).orElse(null)
        nestLocation = brain.getMemory(CobblemonMemories.NEST_LOCATION).orElse(null)
        return mate != null && nestLocation != null && !entity.isBusy && entity.tethering == null
    }

    override fun canContinueToUse(): Boolean {
        return mate != null && !mate!!.isDeadOrDying && nestLocation != null && !entity.isBusy
    }

    override fun start() {
        breedingStep = BreedingStep.FIND_MATE
    }

    override fun stop() {
        mate = null
        nestLocation = null
        breedingStep = BreedingStep.FIND_MATE
    }

    override fun tick() {
        when (breedingStep) {
            BreedingStep.FIND_MATE -> {
                if (mate == null) return
                if (entity.distanceTo(mate) > 2.0) {
                    entity.navigation.moveTo(mate, 1.0)
                } else {
                    // Make the mate start its breeding goal to ensure synchronized movement
                    mate?.brain?.setMemory(CobblemonMemories.NEST_LOCATION, nestLocation)
                    mate?.addGoal(1, PokemonBreedGoal(mate!!))
                    breedingStep = BreedingStep.FIND_NEST
                }
            }
            BreedingStep.FIND_NEST -> {
                if (nestLocation == null) return
                if (entity.distanceToSqr(Vec3.atCenterOf(nestLocation!!)) > 4.0) {
                    entity.navigation.moveTo(
                        nestLocation!!.x + 0.5,
                        nestLocation!!.y + 0.5,
                        nestLocation!!.z + 0.5,
                        1.0
                    )
                } else if (mate?.distanceToSqr(Vec3.atCenterOf(nestLocation!!)) ?: Double.MAX_VALUE > 4.0) {
                    // Wait for both Pokémon to reach the nest
                    mate?.navigation?.moveTo(
                        nestLocation!!.x + 0.5,
                        nestLocation!!.y + 0.5,
                        nestLocation!!.z + 0.5,
                        1.0
                    )
                } else {
                    breedingStep = BreedingStep.BREED
                }
            }
            BreedingStep.BREED -> {
                if (mate == null || nestLocation == null) return
                breed()
                stop()
            }
        }
    }

    private fun breed() {
        // Trigger breeding cooldown for both Pokémon
        entity.pokemon.breedingCooldown = 6000 // Example cooldown: 5 minutes
        mate?.pokemon?.breedingCooldown = 6000

        // Add egg to the nest block entity
        val blockEntity: BlockEntity? = entity.level().getBlockEntity(nestLocation!!)
        if (blockEntity != null) {
            // TODO: Add logic to interact with the nest block entity
            // Example: Add egg item or Pokémon data to the block entity
            println("Adding egg to the nest at $nestLocation") // Replace with actual logic
        }

        // Broadcast a visual or sound effect to signal breeding success
        entity.level().broadcastEntityEvent(entity, 10.toByte())
        mate?.level()?.broadcastEntityEvent(mate, 10.toByte())
    }

    private enum class BreedingStep {
        FIND_MATE,
        FIND_NEST,
        BREED
    }
}
