package com.cobblemon.mod.common.entity.pokemon.ai.tasks

import com.cobblemon.mod.common.CobblemonBlocks
import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.block.SaccharineLogSlatheredBlock
import net.minecraft.core.Direction
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.behavior.OneShot
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder
import net.minecraft.world.entity.ai.behavior.declarative.Trigger
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.level.block.RotatedPillarBlock
import net.minecraft.world.phys.Vec3
import kotlin.random.Random

object SlatherHoneyOnLogTask {
    fun create(): OneShot<in LivingEntity> = BehaviorBuilder.create {
        it.group(
            it.absent(MemoryModuleType.WALK_TARGET),
            it.present(CobblemonMemories.SACCHARINE_LOG_LOCATION),
            it.present(CobblemonMemories.HAS_HONEY)
        ).apply(it) { walkTarget, logMem, hasHoney ->
            Trigger { world, entity, _ ->
                if (entity !is PathfinderMob) return@Trigger false
                val pos = it.get(logMem)
                val target = Vec3.atCenterOf(pos)
                if (entity.distanceToSqr(target) > 2.0) return@Trigger false

                val state = world.getBlockState(pos)
                if (state.block != CobblemonBlocks.SACCHARINE_LOG) return@Trigger false
                if (state.getValue(RotatedPillarBlock.AXIS) != Direction.Axis.Y) return@Trigger false

                // pick a horizontal face with air in front
                val face = Direction.Plane.HORIZONTAL.find {
                    world.getBlockState(pos.relative(it)).isAir
                } ?: return@Trigger false

                val newState = CobblemonBlocks.SACCHARINE_LOG_SLATHERED.defaultBlockState()
                    .setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y)
                    .setValue(SaccharineLogSlatheredBlock.HONEY_TYPE, Random.nextInt(0, SaccharineLogSlatheredBlock.HONEY_TYPE_MAX + 1))

                world.setBlock(pos, newState, 3)
                world.playSound(null, pos, SoundEvents.HONEY_BLOCK_PLACE, SoundSource.BLOCKS, 1f, 1f)

                entity.brain.eraseMemory(CobblemonMemories.SACCHARINE_LOG_LOCATION)
                entity.brain.eraseMemory(CobblemonMemories.HAS_HONEY)
                true
            }
        }
    }
}