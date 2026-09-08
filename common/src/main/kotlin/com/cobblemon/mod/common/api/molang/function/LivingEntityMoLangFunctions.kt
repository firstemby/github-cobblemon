/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molang.function

import com.bedrockk.molang.runtime.MoParams
import com.bedrockk.molang.runtime.struct.VariableStruct
import com.bedrockk.molang.runtime.value.DoubleValue
import com.bedrockk.molang.runtime.value.MoValue
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.ai.CobblemonBlockPosTracker
import com.cobblemon.mod.common.api.ai.CobblemonWanderControl
import com.cobblemon.mod.common.api.molang.ObjectValue
import com.cobblemon.mod.common.entity.npc.NPCEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.asIdentifierDefaultingNamespace
import com.cobblemon.mod.common.util.asUUID
import com.cobblemon.mod.common.util.getBooleanOrNull
import com.cobblemon.mod.common.util.getDoubleOrNull
import com.cobblemon.mod.common.util.getIntOrNull
import com.cobblemon.mod.common.util.getMemorySafely
import com.cobblemon.mod.common.util.hasMemoryFromString
import com.cobblemon.mod.common.util.isDoingActivityFromString
import com.cobblemon.mod.common.util.isLookingAt
import com.cobblemon.mod.common.util.traceEntityCollision
import net.minecraft.core.Position
import net.minecraft.core.Vec3i
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.TamableAnimal
import net.minecraft.world.entity.ai.behavior.BlockPosTracker
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.MemoryStatus
import net.minecraft.world.entity.ai.memory.WalkTarget
import net.minecraft.world.entity.animal.Animal
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.pathfinder.PathType
import net.minecraft.world.phys.Vec3
import java.util.*

object LivingEntityMoLangFunctions : AbstractMoLangFunctionHolder<LivingEntity>() {
    override fun LivingEntity.moLangFunctions(): MutableMap<String, (MoParams) -> Any> {
        val entity = this
        val map = mutableMapOf<String, (MoParams) -> Any>()
        map["is_player"] = { _ -> DoubleValue(entity is Player) }
        map["is_npc"] = { _ -> DoubleValue(entity is NPCEntity) }
        map["is_mob"] = { _ -> DoubleValue(entity is Mob) }
        map["is_pokemon"] = { _ -> DoubleValue(entity is PokemonEntity) }
        map["is_animal"] = { _ -> DoubleValue(entity is Animal) }
        map["is_tamable"] = { _ -> DoubleValue(entity is TamableAnimal) }
        map["is_tamed"] = { _ -> DoubleValue(entity is TamableAnimal && entity.isTame) }
        map["is_hostile"] = { _ -> DoubleValue(entity is Monster) }
        map["is_baby"] = { _ -> DoubleValue(entity.isBaby) }
        map["is_adult"] = { _ -> DoubleValue(!entity.isBaby) }
        map["remove_effect"] = put@{ params ->
            val effectId = params.getString(0).asIdentifierDefaultingNamespace()
            val effectHolder = BuiltInRegistries.MOB_EFFECT.getHolder(effectId).orElse(null)
            if (effectHolder != null) {
                entity.removeEffect(effectHolder)
                return@put DoubleValue.ONE
            }
        }
        map["add_effect"] = put@{ params ->
            val effectId = params.getString(0).asIdentifierDefaultingNamespace()
            val duration = params.getInt(1)
            val amplifier = params.getIntOrNull(2) ?: 0
            val ambient = params.getBooleanOrNull(3) ?: false
            val visible = params.getBooleanOrNull(4) ?: true
            val effectHolder = BuiltInRegistries.MOB_EFFECT.getHolder(effectId).orElse(null)
            if (effectHolder != null) {
                entity.addEffect(MobEffectInstance(effectHolder, duration, amplifier, ambient, visible))
                return@put DoubleValue.ONE
            }
        }
        map["has_effect"] = put@{ params ->
            val effectId = params.getString(0).asIdentifierDefaultingNamespace()
            val effectHolder = BuiltInRegistries.MOB_EFFECT.getHolder(effectId).orElse(null)
            return@put DoubleValue(if (effectHolder != null) entity.hasEffect(effectHolder) else false)
        }
        map["heal"] = { params ->
            val amount = params.getDouble(0)
            entity.heal(amount.toFloat())
        }
        map["is_looking_at"] = put@{ params ->
            val targetEntity = params.get<MoValue>(0)
            val maxDistance = params.getDoubleOrNull(1)?.toFloat() ?: 2.0F

            val entity = if (targetEntity is ObjectValue<*> && targetEntity.obj is Entity) {
                targetEntity.obj as Entity
            } else {
                return@put DoubleValue.ZERO
            }

            return@put DoubleValue(entity.isLookingAt(entity, maxDistance))
        }
        map["is_living_entity"] = { DoubleValue.ONE }
        map["is_flying"] = { _ -> DoubleValue(entity.isFallFlying) }
        map["is_sleeping"] = { _ -> DoubleValue(entity.isSleeping) }
        map["health"] = { _ -> DoubleValue(entity.health) }
        map["max_health"] = { _ -> DoubleValue(entity.maxHealth) }
        map["look_at_position"] = { params ->
            val x = params.getDouble(0)
            val y = params.getDouble(1)
            val z = params.getDouble(2)
            val duration = params.getIntOrNull(3) ?: 20
            val flags = params.params.subList(4, params.params.size).map { it.asString() }
            val brain = entity.brain
            brain.setMemoryWithExpiry(
                MemoryModuleType.LOOK_TARGET,
                CobblemonBlockPosTracker(Vec3(x, y, z), flags.toSet()),
                duration.toLong()
            )
        }
        map["get_wander_control_memory"] = put@{
            val value = entity.brain.getMemorySafely(CobblemonMemories.WANDER_CONTROL).orElse(null)
                ?: CobblemonWanderControl()
            if (
                !entity.brain.checkMemory(CobblemonMemories.WANDER_CONTROL, MemoryStatus.VALUE_PRESENT)
                && entity.brain.checkMemory(CobblemonMemories.WANDER_CONTROL, MemoryStatus.REGISTERED)
            ) {
                entity.brain.setMemory(CobblemonMemories.WANDER_CONTROL, value)
            }
            return@put value.struct
        }
        map["get_position_memory"] = put@{ params ->
            val id = params.getString(0).asIdentifierDefaultingNamespace()
            val memoryType = BuiltInRegistries.MEMORY_MODULE_TYPE.get(id)
            if (entity.brain.checkMemory(memoryType, MemoryStatus.VALUE_PRESENT)) {
                return@put when (val memory = entity.brain.getMemory(memoryType).get()) {
                    is Vec3i -> VariableStruct(
                        mapOf(
                            "x" to DoubleValue(memory.x),
                            "y" to DoubleValue(memory.y),
                            "z" to DoubleValue(memory.z)
                        )
                    )

                    is Position -> VariableStruct(
                        mapOf(
                            "x" to DoubleValue(memory.x()),
                            "y" to DoubleValue(memory.y()),
                            "z" to DoubleValue(memory.z())
                        )
                    )

                    else -> DoubleValue.ZERO
                }
            } else {
                return@put DoubleValue.ZERO
            }
        }
        map["set_uuid_memory"] = put@{ params ->
            val memory = params.getString(0).asIdentifierDefaultingNamespace()
                .let(BuiltInRegistries.MEMORY_MODULE_TYPE::get) as MemoryModuleType<UUID>
            val uuid = params.getString(1).asUUID ?: return@put DoubleValue.ZERO
            val expiry = params.getIntOrNull(2) ?: -1
            if (expiry != -1) {
                entity.brain.setMemoryWithExpiry(memory, uuid, expiry.toLong())
            } else {
                entity.brain.setMemory(memory, uuid)
            }
            return@put DoubleValue.ONE
        }
        map["erase_memory"] = put@{ params ->
            val memories = params.params.map { it.asString().asIdentifierDefaultingNamespace() }
                .map(BuiltInRegistries.MEMORY_MODULE_TYPE::get)
            memories.forEach { entity.brain.eraseMemory(it) }
            return@put DoubleValue.ONE
        }
        map["has_memory_value"] = put@{ params ->
            for (param in params.params) {
                if (!entity.hasMemoryFromString(param.asString())) {
                    return@put DoubleValue.ZERO
                }
            }
            return@put DoubleValue.ONE
        }
        map["lacks_memory_value"] = put@{ params ->
            for (param in params.params) {
                if (entity.hasMemoryFromString(param.asString())) {
                    return@put DoubleValue.ZERO
                }
            }
            return@put DoubleValue.ONE
        }
        map["is_doing_activity"] = put@{ params ->
            for (param in params.params) {
                if (entity.isDoingActivityFromString(param.asString())) {
                    return@put DoubleValue.ONE
                }
            }
            return@put DoubleValue.ZERO
        }
        map["can_see"] = put@{ params ->
            val target = params.get<MoValue>(0)
            val range = params.getDoubleOrNull(1) ?: 32.0
            if (target is ObjectValue<*>) {
                val targetEntity = target.obj as? LivingEntity ?: return@put DoubleValue.ZERO
                val vector = targetEntity.eyePosition.subtract(entity.eyePosition).normalize()
                val trace = entity.traceEntityCollision(
                    maxDistance = range.toFloat(),
                    stepDistance = 0.1F,
                    direction = vector,
                    entityClass = targetEntity.javaClass,
                    collideBlock = ClipContext.Fluid.NONE
                )
                return@put DoubleValue(targetEntity in (trace?.entities ?: emptyList()))
            } else {
                return@put DoubleValue.ZERO
            }
        }

        if (entity is PathfinderMob) {
            map["walk_to"] = { params ->
                val x = params.getDouble(0)
                val y = params.getDouble(1)
                val z = params.getDouble(2)
                val speedMultiplier = params.getDoubleOrNull(3) ?: 0.35

                if (entity.brain.checkMemory(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED)) {
                    entity.brain.setMemory(
                        MemoryModuleType.WALK_TARGET,
                        WalkTarget(Vec3(x, y, z), speedMultiplier.toFloat(), 1)
                    )
                    entity.brain.setMemory(
                        MemoryModuleType.LOOK_TARGET,
                        BlockPosTracker(Vec3(x, y + entity.eyeHeight, z))
                    )
                } else {
                    entity.navigation.moveTo(x, y, z, speedMultiplier)
                    entity.lookControl.setLookAt(Vec3(x, y + entity.eyeHeight, z))
                }
            }

            map["has_walk_target"] = { _ ->
                DoubleValue(entity.brain.getMemorySafely(MemoryModuleType.WALK_TARGET).isPresent || entity.isPathFinding)
            }

            map["set_can_float"] = { params ->
                val canFloat = params.getBooleanOrNull(0) != false
                entity.navigation.setCanFloat(canFloat)
                DoubleValue.ONE
            }

            map["get_pathfinding_malus"] = put@{ params ->
                val type = PathType.entries.find { it.name == params.getString(0).uppercase() }
                if (type != null) {
                    return@put DoubleValue(entity.getPathfindingMalus(type))
                }
                Cobblemon.LOGGER.error("Unknown pathfinding type: ${params.getString(0)}")
                return@put DoubleValue.ZERO
            }
            map["set_pathfinding_malus"] = put@{ params ->
                val type = PathType.entries.find { it.name == params.getString(0).uppercase() }
                val malus = params.getDouble(1).toFloat()
                if (type != null) {
                    entity.setPathfindingMalus(type, malus)
                    return@put DoubleValue.ONE
                }
                Cobblemon.LOGGER.error("Unknown pathfinding type: ${params.getString(0)}")
                return@put DoubleValue.ZERO
            }
        }
        return map
    }
}
