package com.cobblemon.mod.common.entity.pokemon.ai.goals

import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.ai.goal.Goal
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

    override fun canUse(): Boolean {
        // Start goal if both mate and nest memories are available
        val brain = entity.brain
        if (!brain.hasMemoryValue(CobblemonMemories.POTENTIAL_MATE) ||
            !brain.hasMemoryValue(CobblemonMemories.NEST_LOCATION)) {
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
                    breedingStep = BreedingStep.FIND_NEST
                }
            }
            BreedingStep.FIND_NEST -> {
                if (nestLocation == null) return
                //if (entity.distanceTo(Minecraft.getInstance().level.getBlockEntity(nestLocation)) > 2.0) {
                if (entity.distanceToSqr(Vec3.atCenterOf(nestLocation!!)) > 4.0) {
                    entity.navigation.moveTo(nestLocation!!.x + 0.5, nestLocation!!.y + 0.5, nestLocation!!.z + 0.5, 1.0)
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
        // Trigger breeding logic for the Pokémon
        entity.pokemon.breedingCooldown = 6000 // Cooldown before next breeding (example: 5 minutes)
        mate?.pokemon?.breedingCooldown = 6000

        // TODO: Implement breeding logic to spawn an egg or baby Pokémon at the nest
        entity.level().broadcastEntityEvent(entity, 10.toByte()) // Example particle/event broadcast
        mate?.level()?.broadcastEntityEvent(mate, 10.toByte())
    }

    private enum class BreedingStep {
        FIND_MATE,
        FIND_NEST,
        BREED
    }
}
