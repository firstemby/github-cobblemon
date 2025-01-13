package com.cobblemon.mod.common.api.ai.config.task

import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.ai.BrainConfigurationContext
import com.cobblemon.mod.common.api.ai.asVariables
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMostSpecificMoLangValue
import com.cobblemon.mod.common.api.npc.configuration.MoLangConfigVariable
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.withQueryValue
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.behavior.BehaviorControl
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder
import net.minecraft.world.entity.ai.behavior.declarative.Trigger
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.WalkTarget
import net.minecraft.world.phys.Vec3

class PathToNestTaskConfig : SingleTaskConfig {
    companion object {
        const val BREED = "breed" // Category
    }

    val condition = booleanVariable(BREED, "breeds", true).asExpressible()
    val speedMultiplier = numberVariable(BREED, "speed_multiplier", 0.6).asExpressible()

    override val variables: List<MoLangConfigVariable>
        get() = listOf(condition, speedMultiplier).asVariables()

    override fun createTask(
        entity: LivingEntity,
        brainConfigurationContext: BrainConfigurationContext
    ): BehaviorControl<in LivingEntity>? {
        runtime.withQueryValue("entity", entity.asMostSpecificMoLangValue())
        if (!condition.resolveBoolean() || entity !is PokemonEntity) return null

        return BehaviorBuilder.create {
            it.group(
                it.absent(MemoryModuleType.LOOK_TARGET),
                it.present(CobblemonMemories.NEST_LOCATION),
                it.present(CobblemonMemories.POTENTIAL_MATE)
            ).apply(it) { lookTarget, nestMemory, mateMemory ->
                Trigger { world, entity, time ->
                    if (entity !is PathfinderMob || !entity.isAlive) return@Trigger false

                    runtime.withQueryValue("entity", entity.asMostSpecificMoLangValue())

                    // Retrieve nest location and mate entity from memory
                    val nestLocation = it.get(nestMemory)
                    val mate = it.get(mateMemory) as? PokemonEntity ?: return@Trigger false

                    // Create a target vector at the nest location
                    val targetVec = Vec3.atCenterOf(nestLocation)

                    // Check if Pokémon are close enough to the nest
                    if (entity.distanceToSqr(targetVec) <= 4.0 && mate.distanceToSqr(targetVec) <= 4.0) {
                        return@Trigger false // Task ends when both are close enough
                    }

                    // Set the WalkTarget for navigation
                    entity.brain.setMemory(
                        MemoryModuleType.WALK_TARGET,
                        WalkTarget(targetVec, speedMultiplier.resolveFloat(), 1)
                    )
                    mate.brain.setMemory(
                        MemoryModuleType.WALK_TARGET,
                        WalkTarget(targetVec, speedMultiplier.resolveFloat(), 1)
                    )

                    return@Trigger true
                }
            }
        }
    }
}
