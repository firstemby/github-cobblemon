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
import net.minecraft.world.phys.Vec3
import java.util.UUID

object LivingEntityMoLangFunctions {
    val holder: MutableList<(LivingEntity) -> HashMap<String, (MoParams) -> Any>> =
        mutableListOf(
            { entity ->
                val map = hashMapOf<String, (MoParams) -> Any>()
                map.put("is_player") { _ -> DoubleValue(entity is Player) }
                map.put("is_npc") { _ -> DoubleValue(entity is NPCEntity) }
                map.put("is_mob") { _ -> DoubleValue(entity is Mob) }
                map.put("is_pokemon") { _ -> DoubleValue(entity is PokemonEntity) }
                map.put("is_animal") { _ -> DoubleValue(entity is Animal) }
                map.put("is_tamable") { _ -> DoubleValue(entity is TamableAnimal) }
                map.put("is_tamed") { _ -> DoubleValue(entity is TamableAnimal && entity.isTame) }
                map.put("is_hostile") { _ -> DoubleValue(entity is Monster) }
                map.put("is_baby") { _ -> DoubleValue(entity.isBaby) }
                map.put("is_adult") { _ -> DoubleValue(!entity.isBaby) }
                map.put("remove_effect") { params ->
                    val effectId = params.getString(0).asIdentifierDefaultingNamespace()
                    val effectHolder = BuiltInRegistries.MOB_EFFECT.getHolder(effectId).orElse(null)
                    if (effectHolder != null) {
                        entity.removeEffect(effectHolder)
                        return@put DoubleValue.ONE
                    }
                }
                map.put("add_effect") { params ->
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
                map.put("has_effect") { params ->
                    val effectId = params.getString(0).asIdentifierDefaultingNamespace()
                    val effectHolder = BuiltInRegistries.MOB_EFFECT.getHolder(effectId).orElse(null)
                    return@put DoubleValue(if (effectHolder != null) entity.hasEffect(effectHolder) else false)
                }
                map.put("heal") { params ->
                    val amount = params.getDouble(0)
                    entity.heal(amount.toFloat())
                }
                map.put("is_looking_at") { params ->
                    val targetEntity = params.get<MoValue>(0)
                    val maxDistance = params.getDoubleOrNull(1)?.toFloat() ?: 2.0F

                    val entity = if (targetEntity is ObjectValue<*> && targetEntity.obj is Entity) {
                        targetEntity.obj as Entity
                    } else {
                        return@put DoubleValue.ZERO
                    }

                    return@put DoubleValue(entity.isLookingAt(entity, maxDistance))
                }
                map.put("is_living_entity") { DoubleValue.ONE }
                map.put("is_flying") { _ -> DoubleValue(entity.isFallFlying) }
                map.put("is_sleeping") { _ -> DoubleValue(entity.isSleeping) }
                map.put("health") { _ -> DoubleValue(entity.health) }
                map.put("max_health") { _ -> DoubleValue(entity.maxHealth) }
                map.put("look_at_position") { params ->
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
                map.put("get_wander_control_memory") {
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
                map.put("get_position_memory") { params ->
                    val id = params.getString(0).asIdentifierDefaultingNamespace()
                    val memoryType = BuiltInRegistries.MEMORY_MODULE_TYPE.get(id)
                    if (entity.brain.checkMemory(memoryType, MemoryStatus.VALUE_PRESENT)) {
                        val memory = entity.brain.getMemory(memoryType).get()
                        return@put when (memory) {
                            is Vec3i -> VariableStruct(
                                mapOf(
                                    "x" to DoubleValue(memory.x),
                                    "y" to DoubleValue(memory.y),
                                    "z" to DoubleValue(memory.z)
                                )
                            )

                            is net.minecraft.core.Position -> VariableStruct(
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
                map.put("set_uuid_memory") { params ->
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
                map.put("erase_memory") { params ->
                    val memories = params.params.map { it.asString().asIdentifierDefaultingNamespace() }
                        .map(BuiltInRegistries.MEMORY_MODULE_TYPE::get)
                    memories.forEach { entity.brain.eraseMemory(it) }
                    return@put DoubleValue.ONE
                }
                map.put("has_memory_value") { params ->
                    for (param in params.params) {
                        if (!entity.hasMemoryFromString(param.asString())) {
                            return@put DoubleValue.ZERO
                        }
                    }
                    return@put DoubleValue.ONE
                }
                map.put("lacks_memory_value") { params ->
                    for (param in params.params) {
                        if (entity.hasMemoryFromString(param.asString())) {
                            return@put DoubleValue.ZERO
                        }
                    }
                    return@put DoubleValue.ONE
                }
                map.put("is_doing_activity") { params ->
                    for (param in params.params) {
                        if (entity.isDoingActivityFromString(param.asString())) {
                            return@put DoubleValue.ONE
                        }
                    }
                    return@put DoubleValue.ZERO
                }
                map.put("can_see") { params ->
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
                    map.put("walk_to") { params ->
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
                    map.put("has_walk_target") { _ ->
                        DoubleValue(entity.brain.getMemorySafely(MemoryModuleType.WALK_TARGET).isPresent || entity.isPathFinding)
                    }
                }
                map
            }
        )
}
