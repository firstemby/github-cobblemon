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
        val functions = hashMapOf<String, (MoParams) -> Any>()

        functions["is_player"] = { DoubleValue(entity is Player) }
        functions["is_npc"] = { DoubleValue(entity is NPCEntity) }
        functions["is_mob"] = { DoubleValue(entity is Mob) }
        functions["is_pokemon"] = { DoubleValue(entity is PokemonEntity) }
        functions["is_animal"] = { DoubleValue(entity is Animal) }
        functions["is_tamable"] = { DoubleValue(entity is TamableAnimal) }
        functions["is_tamed"] = { DoubleValue(entity is TamableAnimal && entity.isTame) }
        functions["is_hostile"] = { DoubleValue(entity is Monster) }
        functions["is_baby"] = { DoubleValue(entity.isBaby) }
        functions["is_adult"] = { DoubleValue(!entity.isBaby) }
        functions["remove_effect"] = removeEffect@{ params ->
            val effectId = params.getString(0).asIdentifierDefaultingNamespace()
            val effectHolder = BuiltInRegistries.MOB_EFFECT.getHolder(effectId).orElse(null)
            if (effectHolder != null) {
                entity.removeEffect(effectHolder)
                return@removeEffect DoubleValue.ONE
            }
        }
        functions["add_effect"] = addEffect@{ params ->
            val effectId = params.getString(0).asIdentifierDefaultingNamespace()
            val duration = params.getInt(1)
            val amplifier = params.getIntOrNull(2) ?: 0
            val ambient = params.getBooleanOrNull(3) ?: false
            val visible = params.getBooleanOrNull(4) ?: true
            val effectHolder = BuiltInRegistries.MOB_EFFECT.getHolder(effectId).orElse(null)
            if (effectHolder != null) {
                entity.addEffect(MobEffectInstance(effectHolder, duration, amplifier, ambient, visible))
                return@addEffect DoubleValue.ONE
            }
        }
        functions["has_effect"] = { params ->
            val effectId = params.getString(0).asIdentifierDefaultingNamespace()
            val effectHolder = BuiltInRegistries.MOB_EFFECT.getHolder(effectId).orElse(null)
            DoubleValue(effectHolder != null && entity.hasEffect(effectHolder))
        }
        functions["heal"] = { params ->
            val amount = params.getDouble(0)
            entity.heal(amount.toFloat())
        }
        functions["is_looking_at"] = isLookingAt@{ params ->
            val targetEntity = params.get<MoValue>(0)
            val maxDistance = params.getDoubleOrNull(1)?.toFloat() ?: 2.0F

            val entity =
                if (targetEntity is ObjectValue<*> && targetEntity.obj is Entity) {
                    targetEntity.obj as Entity
                } else {
                    return@isLookingAt DoubleValue.ZERO
                }

            DoubleValue(entity.isLookingAt(entity, maxDistance))
        }
        functions["is_living_entity"] = { DoubleValue.ONE }
        functions["is_flying"] = { DoubleValue(entity.isFallFlying) }
        functions["is_sleeping"] = { DoubleValue(entity.isSleeping) }
        functions["health"] = { DoubleValue(entity.health) }
        functions["max_health"] = { DoubleValue(entity.maxHealth) }
        functions["look_at_position"] = { params ->
            val x = params.getDouble(0)
            val y = params.getDouble(1)
            val z = params.getDouble(2)
            val duration = params.getIntOrNull(3) ?: 20
            val flags = params.params.subList(4, params.params.size).map { it.asString() }
            val brain = entity.brain
            brain.setMemoryWithExpiry(
                MemoryModuleType.LOOK_TARGET,
                CobblemonBlockPosTracker(Vec3(x, y, z), flags.toSet()),
                duration.toLong(),
            )
        }
        functions["get_wander_control_memory"] = {
            val value =
                entity.brain.getMemorySafely(CobblemonMemories.WANDER_CONTROL).orElse(null)
                    ?: CobblemonWanderControl()
            if (
                !entity.brain.checkMemory(CobblemonMemories.WANDER_CONTROL, MemoryStatus.VALUE_PRESENT) &&
                entity.brain.checkMemory(CobblemonMemories.WANDER_CONTROL, MemoryStatus.REGISTERED)
            ) {
                entity.brain.setMemory(CobblemonMemories.WANDER_CONTROL, value)
            }
            value.struct
        }
        functions["get_position_memory"] = getPosMemory@{ params ->
            val id = params.getString(0).asIdentifierDefaultingNamespace()
            val memoryType = BuiltInRegistries.MEMORY_MODULE_TYPE.get(id)
            if (entity.brain.checkMemory(memoryType, MemoryStatus.VALUE_PRESENT)) {
                return@getPosMemory when (val memory = entity.brain.getMemory(memoryType).get()) {
                    is Vec3i -> {
                        VariableStruct(
                            mapOf(
                                "x" to DoubleValue(memory.x),
                                "y" to DoubleValue(memory.y),
                                "z" to DoubleValue(memory.z),
                            ),
                        )
                    }

                    is Position -> {
                        VariableStruct(
                            mapOf(
                                "x" to DoubleValue(memory.x()),
                                "y" to DoubleValue(memory.y()),
                                "z" to DoubleValue(memory.z()),
                            ),
                        )
                    }

                    else -> {
                        DoubleValue.ZERO
                    }
                }
            }

            DoubleValue.ZERO
        }
        functions["set_uuid_memory"] = setUuidMemory@{ params ->
            val memory =
                params
                    .getString(0)
                    .asIdentifierDefaultingNamespace()
                    .let(BuiltInRegistries.MEMORY_MODULE_TYPE::get) as MemoryModuleType<UUID>
            val uuid = params.getString(1).asUUID ?: return@setUuidMemory DoubleValue.ZERO
            val expiry = params.getIntOrNull(2) ?: -1
            if (expiry != -1) {
                entity.brain.setMemoryWithExpiry(memory, uuid, expiry.toLong())
            } else {
                entity.brain.setMemory(memory, uuid)
            }
            DoubleValue.ONE
        }
        functions["erase_memory"] = { params ->
            val memories =
                params.params
                    .map { it.asString().asIdentifierDefaultingNamespace() }
                    .map(BuiltInRegistries.MEMORY_MODULE_TYPE::get)
            memories.forEach { entity.brain.eraseMemory(it) }
            DoubleValue.ONE
        }
        functions["has_memory_value"] = hasMemoryValue@{ params ->
            for (param in params.params) {
                if (!entity.hasMemoryFromString(param.asString())) {
                    return@hasMemoryValue DoubleValue.ZERO
                }
            }
            DoubleValue.ONE
        }
        functions["lacks_memory_value"] = lacksMemoryValue@{ params ->
            for (param in params.params) {
                if (entity.hasMemoryFromString(param.asString())) {
                    return@lacksMemoryValue DoubleValue.ZERO
                }
            }
            DoubleValue.ONE
        }
        functions["is_doing_activity"] = isDoingActivity@{ params ->
            for (param in params.params) {
                if (entity.isDoingActivityFromString(param.asString())) {
                    return@isDoingActivity DoubleValue.ONE
                }
            }
            DoubleValue.ZERO
        }
        functions["can_see"] = canSee@{ params ->
            val target = params.get<MoValue>(0)
            val range = params.getDoubleOrNull(1) ?: 32.0
            if (target is ObjectValue<*>) {
                val targetEntity = target.obj as? LivingEntity ?: return@canSee DoubleValue.ZERO
                val vector = targetEntity.eyePosition.subtract(entity.eyePosition).normalize()
                val trace =
                    entity.traceEntityCollision(
                        maxDistance = range.toFloat(),
                        stepDistance = 0.1F,
                        direction = vector,
                        entityClass = targetEntity.javaClass,
                        collideBlock = ClipContext.Fluid.NONE,
                    )
                return@canSee DoubleValue(targetEntity in (trace?.entities ?: emptyList()))
            }
            DoubleValue.ZERO
        }

        when (entity) {
            is PathfinderMob -> {
                functions["walk_to"] = { params ->
                    val x = params.getDouble(0)
                    val y = params.getDouble(1)
                    val z = params.getDouble(2)
                    val speedMultiplier = params.getDoubleOrNull(3) ?: 0.35

                    if (entity.brain.checkMemory(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED)) {
                        entity.brain.setMemory(
                            MemoryModuleType.WALK_TARGET,
                            WalkTarget(Vec3(x, y, z), speedMultiplier.toFloat(), 1),
                        )
                        entity.brain.setMemory(
                            MemoryModuleType.LOOK_TARGET,
                            BlockPosTracker(Vec3(x, y + entity.eyeHeight, z)),
                        )
                    } else {
                        entity.navigation.moveTo(x, y, z, speedMultiplier)
                        entity.lookControl.setLookAt(Vec3(x, y + entity.eyeHeight, z))
                    }
                }

                functions["has_walk_target"] = {
                    DoubleValue(entity.brain.getMemorySafely(MemoryModuleType.WALK_TARGET).isPresent || entity.isPathFinding)
                }

                functions["set_can_float"] = { params ->
                    val canFloat = params.getBooleanOrNull(0) != false
                    entity.navigation.setCanFloat(canFloat)
                    DoubleValue.ONE
                }

                functions["get_pathfinding_malus"] = getPfMalus@{ params ->
                    val type = PathType.entries.find { it.name == params.getString(0).uppercase() }
                    if (type != null) {
                        return@getPfMalus DoubleValue(entity.getPathfindingMalus(type))
                    }
                    Cobblemon.LOGGER.error("Cannot get pathfinding type, unknown value: ${params.getString(0)}")
                    DoubleValue.ZERO
                }
                functions["set_pathfinding_malus"] = setPfMalus@{ params ->
                    val type = PathType.entries.find { it.name == params.getString(0).uppercase() }
                    val malus = params.getDouble(1).toFloat()
                    if (type != null) {
                        entity.setPathfindingMalus(type, malus)
                        return@setPfMalus DoubleValue.ONE
                    }
                    Cobblemon.LOGGER.error("Cannot set pathfinding type, unknown value: ${params.getString(0)}")
                    DoubleValue.ZERO
                }
            }
        }

        return functions
    }
}
