package com.cobblemon.mod.common.api.ai.config.task

import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.ai.BrainConfigurationContext
import com.cobblemon.mod.common.api.ai.asVariables
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMostSpecificMoLangValue
import com.cobblemon.mod.common.api.npc.configuration.MoLangConfigVariable
import com.cobblemon.mod.common.api.pokemon.breeding.Egg
import com.cobblemon.mod.common.api.text.green
import com.cobblemon.mod.common.block.entity.NestBlockEntity
import com.cobblemon.mod.common.breeding.BreedingLogicManager
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.withQueryValue
import com.mojang.datafixers.kinds.IdF
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.behavior.BehaviorControl
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder
import net.minecraft.world.entity.ai.behavior.declarative.Trigger
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.phys.Vec3

class BreedAtNestTaskConfig : SingleTaskConfig {
    companion object {
        const val BREED = "breed_at_nest" // Category
    }

    val condition = booleanVariable(BREED, "can_breed", true).asExpressible()

    override val variables: List<MoLangConfigVariable>
        get() = listOf(condition).asVariables()

    override fun createTask(
        entity: LivingEntity,
        brainConfigurationContext: BrainConfigurationContext
    ): BehaviorControl<in LivingEntity>? {
        runtime.withQueryValue("entity", entity.asMostSpecificMoLangValue())
        if (!condition.resolveBoolean() || entity !is PokemonEntity) return null

        return BehaviorBuilder.create {
            it.group(
                it.present(CobblemonMemories.NEST_LOCATION),
                it.present(CobblemonMemories.POTENTIAL_MATE),
                it.absent(MemoryModuleType.WALK_TARGET)
            ).apply(it) { nestMemory, mateMemory, walkTarget ->
                Trigger { world, entity, time ->
                    if (entity !is PathfinderMob || !entity.isAlive) return@Trigger false

                    runtime.withQueryValue("entity", entity.asMostSpecificMoLangValue())

                    // Retrieve nest location and mate entity from memory
                    val nestLocation: BlockPos = (nestMemory.value() as? IdF<BlockPos>)?.value() ?: return@Trigger false
                    val mate = mateMemory.value() as? PokemonEntity ?: return@Trigger false

                    // Convert nest location to Vec3
                    val targetVec = Vec3.atCenterOf(nestLocation)

                    // Check proximity to the nest
                    if (entity.distanceToSqr(targetVec) > 4.0 && mate.distanceToSqr(targetVec) > 4.0) {
                        return@Trigger false
                    }

                    // Access the nest block entity
                    val nestBlockEntity = world.getBlockEntity(nestLocation) as? NestBlockEntity
                        ?: return@Trigger false

                    // Generate the egg using BreedingLogicManager
                    val parentOne = (entity as PokemonEntity).pokemon
                    val parentTwo = mate.pokemon
                    val breedingResult = BreedingLogicManager.breed(parentOne, parentTwo)

                    // Ensure the breeding result is valid
                    val egg = breedingResult.pokemon?.let { result ->
                        Egg(
                            species = result.species.resourceIdentifier,
                            parentOne = parentOne,
                            parentTwo = parentTwo,
                            timeToHatch = 6000F
                        )
                    } ?: return@Trigger false

                    // Set the egg in the nest
                    nestBlockEntity.setEgg(egg)

                    // Notify nearby players
                    world.players().forEach { player ->
                        player.displayClientMessage("An egg has been laid in the nest!".green(), true)
                    }

                    return@Trigger true
                }
            }
        }
    }
}