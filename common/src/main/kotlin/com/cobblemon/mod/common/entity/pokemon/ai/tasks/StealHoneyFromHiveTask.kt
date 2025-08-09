package com.cobblemon.mod.common.entity.pokemon.ai.tasks

import com.cobblemon.mod.common.CobblemonMemories
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.behavior.OneShot
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder
import net.minecraft.world.entity.ai.behavior.declarative.Trigger
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.level.block.BeehiveBlock
import net.minecraft.world.phys.Vec3

object StealHoneyFromHiveTask {
    fun create(): OneShot<in LivingEntity> = BehaviorBuilder.create {
        it.group(
            it.absent(MemoryModuleType.WALK_TARGET),
            it.present(CobblemonMemories.HIVE_LOCATION),
            it.absent(CobblemonMemories.HAS_HONEY),
            it.absent(CobblemonMemories.HIVE_COOLDOWN)
        ).apply(it) { walkTarget, hiveMem, hasHoney, cooldown ->
            Trigger { world, entity, _ ->
                if (entity !is PathfinderMob) return@Trigger false
                val hive = it.get(hiveMem)
                val center = Vec3.atCenterOf(hive)
                if (entity.distanceToSqr(center) > 2.0) return@Trigger false

                val state = world.getBlockState(hive)
                val block = state.block
                if (block is BeehiveBlock) {
                    val level = state.getValue(BeehiveBlock.HONEY_LEVEL)
                    if (level > 0) {
                        world.setBlock(hive, state.setValue(BeehiveBlock.HONEY_LEVEL, level - 1), 3)
                        entity.brain.setMemory(CobblemonMemories.HAS_HONEY, true)
                        entity.brain.setMemoryWithExpiry(CobblemonMemories.HIVE_COOLDOWN, true, 1200L)
                        return@Trigger true
                    }
                }
                false
            }
        }
    }
}