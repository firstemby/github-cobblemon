package com.cobblemon.mod.common.entity.pokemon.ai.tasks

import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.client.render.models.blockbench.animation.ActiveAnimation
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.net.messages.client.animation.PlayPosableAnimationPacket
import com.google.common.collect.ImmutableMap
import net.minecraft.client.particle.Particle
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ItemParticleOption
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.ai.behavior.Behavior
import net.minecraft.world.entity.ai.behavior.BlockPosTracker
import net.minecraft.world.entity.ai.behavior.PositionTracker
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.MemoryStatus
import net.minecraft.world.entity.ai.memory.WalkTarget
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.food.Foods
import net.minecraft.world.item.ItemStack
import java.util.Optional
import java.util.function.Consumer
import kotlin.enums.enumEntries


class MoveToItemAndEatTask(): Behavior<PokemonEntity> (
    ImmutableMap.of(
        MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM, MemoryStatus.VALUE_PRESENT
    )
) {
    private val eatRange = 1.5
    private var lastEatTime: Long = 0
    private var lastEatAnimationTime: Long = 0
    private var cooldown: Long = 25
    private var eatCooldown: Long = 15

    override fun stop(world: ServerLevel, entity: PokemonEntity, time: Long) {
        entity.brain.eraseMemory(MemoryModuleType.WALK_TARGET)
        entity.brain.eraseMemory(MemoryModuleType.LOOK_TARGET)
    }

    override fun canStillUse(world: ServerLevel, entity: PokemonEntity, time: Long): Boolean {
        return entity.brain.getMemory(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM).isPresent
    }

    override fun tick(world: ServerLevel, entity: PokemonEntity, time: Long) {
        val item = entity.brain.getMemory(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM).get()
        val itemPos = item.blockPosition()
        val walkTarget = WalkTarget(itemPos, 0.5f, 1)
        entity.brain.setMemory(MemoryModuleType.WALK_TARGET, walkTarget)
        entity.brain.setMemory(MemoryModuleType.LOOK_TARGET, BlockPosTracker(itemPos))
        if (entity.blockPosition().closerThan(item.blockPosition(), eatRange) && time >= lastEatTime + eatCooldown){
            eatItem(world, entity, item)
        }
        if (entity.blockPosition().closerThan(item.blockPosition(), eatRange) && time >= lastEatAnimationTime + cooldown) {
            eatItemAnimation(entity, item)
        }
    }

    private fun eatItemAnimation(entity: PokemonEntity, item: ItemEntity) {
        if (item.item.count > 0) {
            cooldown = 35
            eatMultipleAnimation(entity)
        } else if (entity.mainHandItem.isEmpty()) {
            cooldown = 62
            eatAnimation(entity)
        }
        lastEatAnimationTime = entity.level().gameTime
    }

    private fun eatItem(world: ServerLevel, entity: PokemonEntity, item: ItemEntity) {
        entity.setItemSlot(EquipmentSlot.MAINHAND, item.item)
        entity.eat(world, item.item)
        eatParticles(world, entity, item)
        entity.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY)
        lastEatTime = entity.level().gameTime
    }

    private fun eatParticles(world: ServerLevel, entity: Entity, item: ItemEntity) {
        val itemStack = item.item
        if (itemStack.isEmpty) {
            return
        }
        val particleData = ItemParticleOption(ParticleTypes.ITEM, itemStack)
        world.sendParticles(
            particleData, // Particle type with item stack
            entity.x, entity.y + 0.6, entity.z, // Position
            10, // Number of particles
            0.1, 0.1, 0.1, // Offset
            0.1 // Speed
        )
    }

    private fun eatMultipleAnimation(pokemon: PokemonEntity) {
        val playPoseableAnimationPacket = PlayPosableAnimationPacket(pokemon.id, setOf("eat_multiple"), listOf())
        playPoseableAnimationPacket.sendToPlayersAround(pokemon.x, pokemon.y, pokemon.z, 128.0, pokemon.level().dimension())
    }

    private fun eatAnimation(pokemon: PokemonEntity) {
        val playPoseableAnimationPacket = PlayPosableAnimationPacket(pokemon.id, setOf("eat"), listOf())
        playPoseableAnimationPacket.sendToPlayersAround(pokemon.x, pokemon.y, pokemon.z, 128.0, pokemon.level().dimension())
    }
}