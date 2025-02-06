package com.cobblemon.mod.common.api.ai.config.task

import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.ai.BrainConfigurationContext
import com.cobblemon.mod.common.api.ai.WrapperLivingEntityTask
import com.cobblemon.mod.common.api.ai.asVariables
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMostSpecificMoLangValue
import com.cobblemon.mod.common.api.pokemon.breeding.Egg
import com.cobblemon.mod.common.api.text.green
import com.cobblemon.mod.common.block.entity.NestBlockEntity
import com.cobblemon.mod.common.breeding.BreedingLogicManager
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.entity.pokemon.ai.tasks.BreedAtNestTask
import com.cobblemon.mod.common.entity.pokemon.ai.tasks.PickUpEggTask
import com.cobblemon.mod.common.entity.pokemon.ai.tasks.PlaceEggInNestTask
import com.cobblemon.mod.common.entity.pokemon.ai.tasks.SearchForEggTask
import com.cobblemon.mod.common.util.withQueryValue
import com.mojang.datafixers.kinds.IdF
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.behavior.BehaviorControl
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder
import net.minecraft.world.entity.ai.behavior.declarative.Trigger
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3

class PickUpEggTaskConfig : SingleTaskConfig {
    companion object {
        const val BREED = "breed_at_nest" // Category
    }

    val condition = booleanVariable(BREED, "can_breed", true).asExpressible()

    override fun getVariables(entity: LivingEntity) = listOf(
        condition
    ).asVariables()

    override fun createTask(
        entity: LivingEntity,
        brainConfigurationContext: BrainConfigurationContext
    ): BehaviorControl<in LivingEntity>? {
        return WrapperLivingEntityTask(PickUpEggTask.create(), PokemonEntity::class.java)
    }
}

    /*override fun createTask(
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
                    val mate = (mateMemory.value() as? IdF<PokemonEntity>)?.value() ?: return@Trigger false

                    // Convert nest location to Vec3
                    val targetVec = Vec3.atCenterOf(nestLocation)

                    // Check proximity to the nest
                    if (entity.distanceToSqr(targetVec) > 2.0 && mate.distanceToSqr(targetVec) > 2.0) {
                        return@Trigger false
                    }

                    // Access the nest block entity
                    val nestBlockEntity = world.getBlockEntity(nestLocation) as? NestBlockEntity
                        ?: return@Trigger false

                    // double check to make sure the nest is not taken yet and cancel if it is
                    if (nestBlockEntity.getItem(0) != ItemStack.EMPTY) return@Trigger false

                    // Generate the egg using BreedingLogicManager
                    val parentOne = (entity as PokemonEntity).pokemon
                    val parentTwo = mate.pokemon
                    val breedingResult = BreedingLogicManager.breed(parentOne, parentTwo)
                    val form = BreedingLogicManager.calculateForm(parentOne, parentTwo)

                    // Ensure the breeding result is valid
                    val egg = breedingResult.pokemon?.let { result ->
                        Egg(
                            species = result.species.resourceIdentifier,
                            parentOne = parentOne,
                            parentTwo = parentTwo,
                            form = form.name.lowercase(),
                            timeToHatch = 6000F
                        )
                    } ?: return@Trigger false

                    // Set the egg in the nest
                    nestBlockEntity.setItem(egg.createEggItemStack())

                    // set the parents of the nest if it/they are wild so they guard it
                    if (nestBlockEntity.ownerUUID == null)
                        nestBlockEntity.parents = Pair((entity as PokemonEntity).uuid, mate.uuid)


                    // set breeding cooldown for both
                    parentOne.breedingCooldown = BreedingLogicManager.resetBreedingCooldown(parentOne)
                    parentTwo.breedingCooldown = BreedingLogicManager.resetBreedingCooldown(parentTwo)

                    // Display heart particles above parents

                    (world as net.minecraft.server.level.ServerLevel).sendParticles(
                            net.minecraft.core.particles.ParticleTypes.HEART,
                            entity.getRandomX(1.0), // Particle X position with slight randomness
                            entity.getRandomY() + 0.5, // Slightly raised Y position
                            entity.getRandomZ(1.0), // Particle Z position with slight randomness
                            4 + kotlin.random.Random.nextInt(2), // Particle count
                            0.2 * entity.random.nextGaussian(), // X velocity with slight variance
                            0.1 * entity.random.nextGaussian(), // Y velocity with upwards variance
                            0.2 * entity.random.nextGaussian(), // Z velocity with slight variance
                            0.02 // Particle speed
                    )
                    (world as net.minecraft.server.level.ServerLevel).sendParticles(
                            net.minecraft.core.particles.ParticleTypes.HEART,
                            mate.getRandomX(1.0), // Particle X position with slight randomness
                            mate.getRandomY() + 0.5, // Slightly raised Y position
                            mate.getRandomZ(1.0), // Particle Z position with slight randomness
                            4 + kotlin.random.Random.nextInt(2), // Particle count
                            0.2 * mate.random.nextGaussian(), // X velocity with slight variance
                            0.1 * mate.random.nextGaussian(), // Y velocity with upwards variance
                            0.2 * mate.random.nextGaussian(), // Z velocity with slight variance
                            0.02 // Particle speed
                    )

                    // Notify nearby players
                    world.players().forEach { player ->
                        player.displayClientMessage("An egg has been laid in the nest!".green(), true)
                    }

                    // todo maybe have them fall asleep after?

                    return@Trigger true
                }
            }
        }*/