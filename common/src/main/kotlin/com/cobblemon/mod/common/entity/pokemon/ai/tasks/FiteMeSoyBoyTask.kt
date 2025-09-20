package com.cobblemon.mod.common.entity.pokemon.ai.tasks

import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.google.common.collect.ImmutableMap
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.behavior.Behavior
import net.minecraft.world.entity.ai.behavior.EntityTracker
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.MemoryStatus
import net.minecraft.world.entity.ai.memory.WalkTarget
import java.util.Optional
import java.util.UUID

class FiteMeSoyBoyTask : Behavior<PokemonEntity>(
    ImmutableMap.of(
        CobblemonMemories.RECENT_CATCH_ATTEMPT, MemoryStatus.VALUE_PRESENT,
    )
) {
    companion object {
        private const val ATTACK_RANGE = 6.0
        private const val SPEED = 1.0f
    }

    private var targetEntity: Optional<ServerPlayer> = Optional.empty()

    override fun checkExtraStartConditions(level: ServerLevel, entity: PokemonEntity): Boolean {
        this.targetEntity = findSoyBoy(level, entity)
        return targetEntity.isPresent
    }

    override fun canStillUse(level: ServerLevel, entity: PokemonEntity, gameTime: Long): Boolean
        = targetEntity.map { it.isAlive }.orElse(false)
        && entity.brain.hasMemoryValue(CobblemonMemories.RECENT_CATCH_ATTEMPT)

    private fun findSoyBoy(level: ServerLevel, entity: PokemonEntity): Optional<ServerPlayer> {
        val soyBoyUuid = entity.brain.getMemory(CobblemonMemories.RECENT_CATCH_ATTEMPT)?.get() ?: return Optional.empty()
        return level.players()
            .find { it.uuid == soyBoyUuid }
            ?.let { Optional.of(it) } ?: Optional.empty()
    }

    override fun start(level: ServerLevel, entity: PokemonEntity, gameTime: Long) {
        addLookWalkTargets(entity)
        super.start(level, entity, gameTime)
    }

    private fun addLookWalkTargets(entity: LivingEntity) {
        targetEntity.ifPresent {
            val lookTarget = EntityTracker(it, true)
            entity.brain.setMemory(MemoryModuleType.LOOK_TARGET, lookTarget)
            entity.brain.setMemory(MemoryModuleType.WALK_TARGET, WalkTarget(lookTarget, SPEED, 1))
        }
    }

    override fun stop(level: ServerLevel, entity: PokemonEntity, gameTime: Long) {
        targetEntity = Optional.empty()
    }

    override fun tick(level: ServerLevel, entity: PokemonEntity, gameTime: Long) {
        if (targetEntity.isPresent && entity.hasLineOfSight(targetEntity.get())) {
            val soyBoy = targetEntity.get()
            if (soyBoy.isAlive) {
                if (entity.distanceToSqr(soyBoy) <= ATTACK_RANGE * ATTACK_RANGE) {
                    if (entity.forceBattle(soyBoy)) {
                        entity.brain.eraseMemory(CobblemonMemories.RECENT_CATCH_ATTEMPT)
                    }
                } else {
                    addLookWalkTargets(entity)
                }
            }
        }
    }
}