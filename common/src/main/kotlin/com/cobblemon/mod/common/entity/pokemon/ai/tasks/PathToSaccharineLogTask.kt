package com.cobblemon.mod.common.entity.pokemon.ai.tasks

import com.cobblemon.mod.common.CobblemonMemories
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.behavior.BlockPosTracker
import net.minecraft.world.entity.ai.behavior.OneShot
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder
import net.minecraft.world.entity.ai.behavior.declarative.Trigger
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.WalkTarget
import net.minecraft.world.phys.Vec3

object PathToSaccharineLogTask {
    fun create(): OneShot<in LivingEntity> = BehaviorBuilder.create {
        it.group(
            it.registered(MemoryModuleType.LOOK_TARGET),
            it.absent(MemoryModuleType.WALK_TARGET),
            it.present(CobblemonMemories.SACCHARINE_LOG_LOCATION),
            it.present(CobblemonMemories.HAS_HONEY)
        ).apply(it) { lookTarget, walkTarget, logMem, hasHoney ->
            Trigger { world, entity, _ ->
                if (entity !is PathfinderMob || !entity.isAlive) return@Trigger false
                val log = it.get(logMem)
                val target = Vec3.atCenterOf(log)

                // if already close, let the slather task handle it
                if (entity.distanceToSqr(target) <= 2.0) return@Trigger false

                walkTarget.set(WalkTarget(target, 0.35F, 0))
                lookTarget.set(BlockPosTracker(target.add(0.0, entity.eyeHeight.toDouble(), 0.0)))
                true
            }
        }
    }
}
