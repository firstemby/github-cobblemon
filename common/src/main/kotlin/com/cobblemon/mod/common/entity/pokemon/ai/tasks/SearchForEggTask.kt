package com.cobblemon.mod.common.entity.pokemon.ai.tasks

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonItems
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
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.behavior.BehaviorControl
import net.minecraft.world.entity.ai.behavior.BlockPosTracker
import net.minecraft.world.entity.ai.behavior.OneShot
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder
import net.minecraft.world.entity.ai.behavior.declarative.Trigger
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.WalkTarget
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import kotlin.jvm.optionals.getOrNull
import kotlin.random.Random

object SearchForEggTask {
    private const val SEARCH_RADIUS = 20.0
    private const val DISTURBANCE_MIN_DISTANCE = 10.0

    fun create(): OneShot<PokemonEntity> {
        return BehaviorBuilder.create {
            it.group(
                it.present(CobblemonMemories.PATERNAL_NEST_LOCATION),
                it.registered(CobblemonMemories.EGG_IN_NEST),
                it.absent(CobblemonMemories.EGG_LOCATION),
                it.absent(CobblemonMemories.HOLDING_EGG),
                it.absent(MemoryModuleType.WALK_TARGET),
                it.registered(MemoryModuleType.DISTURBANCE_LOCATION),
                it.registered(CobblemonMemories.EGG_SEARCH_COUNTDOWN)
            ).apply(it) { nestMemory, eggInNest, eggMemory, holdingEgg, walkTarget, disturbanceMemory, eggSearchCountdown ->
                Trigger { world, entity, time ->
                    if (entity !is PathfinderMob || !entity.isAlive) return@Trigger false

                    // run this trigger if the egg is not in nest
                    val eggInNest = entity.brain.getMemory(CobblemonMemories.EGG_IN_NEST).orElse(null)
                    if (eggInNest != null && eggInNest == true) {
                        return@Trigger false
                    }

                    val nestPos = entity.brain.getMemory(CobblemonMemories.PATERNAL_NEST_LOCATION).orElse(null) ?: return@Trigger false
                    val nestCenter = Vec3.atCenterOf(nestPos)

                    val searchCountdownRemaining = entity.brain.getTimeUntilExpiry(CobblemonMemories.EGG_SEARCH_COUNTDOWN)

                    // If search has lasted too long, clear memories
                    if (searchCountdownRemaining <= 20) {
                        // clear the parents of the paternal nest here too so that the nest sensor doesn't detect them as the parent of that nest anymore
                        (world.getBlockEntity(nestPos) as NestBlockEntity).parents = Pair(null, null)

                        entity.brain.eraseMemory(CobblemonMemories.EGG_SEARCH_COUNTDOWN)
                        entity.brain.eraseMemory(CobblemonMemories.EGG_LOCATION)
                        entity.brain.eraseMemory(CobblemonMemories.PATERNAL_NEST_LOCATION)
                        entity.brain.eraseMemory(CobblemonMemories.EGG_IN_NEST)
                        entity.brain.eraseMemory(CobblemonMemories.POTENTIAL_MATE)

                        return@Trigger false // Stop task
                    }

                    // Check if there's a disturbance location in memory
                    val disturbancePos = disturbanceMemory.value()?.let { it as? BlockPos }
                    if (disturbancePos != null) {
                        val disturbanceVec = Vec3.atCenterOf(disturbancePos)
                        if (entity.position().distanceTo(disturbanceVec) > DISTURBANCE_MIN_DISTANCE) {
                            // Move towards the disturbance if it's at least 10 blocks away from the previous disturbance
                            walkTarget.set(WalkTarget(disturbanceVec, 1.0F, 1))
                            return@Trigger true
                        }
                    }

                    // If no disturbance pathfind randomly around the nest
                    val randomTarget = getRandomPositionAround(nestCenter, entity)
                    if (randomTarget != null) {
                        walkTarget.set(WalkTarget(randomTarget, 1.0F, 1))
                        return@Trigger true
                    }

                    return@Trigger false
                }
            }
        }
    }

    private fun getRandomPositionAround(center: Vec3, entity: PathfinderMob): Vec3? {
        repeat(10) { // Try up to 10 times to find a valid position
            val offsetX = Random.nextDouble(-SEARCH_RADIUS, SEARCH_RADIUS)
            val offsetZ = Random.nextDouble(-SEARCH_RADIUS, SEARCH_RADIUS)
            val targetVec = center.add(offsetX, 0.0, offsetZ)

            val targetPos = BlockPos(targetVec.x.toInt(), targetVec.y.toInt(), targetVec.z.toInt())
            if (entity.navigation.createPath(targetPos, 0) != null) {
                return Vec3.atCenterOf(targetPos)
            }
        }
        return null
    }
}
