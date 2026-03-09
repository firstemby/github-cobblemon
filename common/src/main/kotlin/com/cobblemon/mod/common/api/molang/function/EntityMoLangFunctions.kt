/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molang.function

import com.bedrockk.molang.runtime.MoLangRuntime
import com.bedrockk.molang.runtime.MoParams
import com.bedrockk.molang.runtime.struct.ArrayStruct
import com.bedrockk.molang.runtime.value.DoubleValue
import com.bedrockk.molang.runtime.value.MoValue
import com.bedrockk.molang.runtime.value.StringValue
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.dialogue.ReferenceDialogueFaceProvider
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asBiomeMoLangValue
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMostSpecificMoLangValue
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asWorldMoLangValue
import com.cobblemon.mod.common.api.molang.ObjectValue
import com.cobblemon.mod.common.api.scheduling.Schedulable
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.entity.MoLangScriptingEntity
import com.cobblemon.mod.common.entity.PosableEntity
import com.cobblemon.mod.common.net.messages.client.animation.PlayPosableAnimationPacket
import com.cobblemon.mod.common.net.messages.client.effect.SpawnSnowstormEntityParticlePacket
import com.cobblemon.mod.common.util.asArrayValue
import com.cobblemon.mod.common.util.asExpressionLike
import com.cobblemon.mod.common.util.asIdentifierDefaultingNamespace
import com.cobblemon.mod.common.util.asResource
import com.cobblemon.mod.common.util.asUUID
import com.cobblemon.mod.common.util.cloneFrom
import com.cobblemon.mod.common.util.effectiveName
import com.cobblemon.mod.common.util.getBlockStatesWithPos
import com.cobblemon.mod.common.util.getBooleanOrNull
import com.cobblemon.mod.common.util.getDoubleOrNull
import com.cobblemon.mod.common.util.getIsSubmerged
import com.cobblemon.mod.common.util.getOrNull
import com.cobblemon.mod.common.util.getStringOrNull
import com.cobblemon.mod.common.util.isStandingOn
import com.cobblemon.mod.common.util.resolve
import com.cobblemon.mod.common.util.worldRegistry
import net.minecraft.core.registries.Registries
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.TagKey
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.damagesource.DamageTypes
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.TamableAnimal
import net.minecraft.world.entity.ai.behavior.BlockPosTracker
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.MemoryStatus
import net.minecraft.world.entity.ai.memory.WalkTarget
import net.minecraft.world.level.pathfinder.PathType
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.math.sqrt

object EntityMoLangFunctions : AbstractMoLangFunctionHolder<Entity> {
    override val holder: MutableList<(Entity) -> MutableMap<String, (MoParams) -> Any>> = mutableListOf(
        { entity ->
            val map = hashMapOf<String, (MoParams) -> Any>()
            map["uuid"] = { _ -> StringValue(entity.uuid.toString()) }
            map["set_name"] = put@{ params ->
                val name = params.getString(0)
                entity.customName = name.text()
                return@put DoubleValue.ONE
            }
            map["damage"] = { params ->
                val amount = params.getDouble(0)
                val source = DamageSource(
                    entity.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                        .getHolder(DamageTypes.GENERIC).get()
                )
                entity.hurt(source, amount.toFloat())
            }
            map["distance_to_owner"] = put@{
                if (entity !is TamableAnimal) {
                    return@put DoubleValue.ZERO
                }
                val owner = entity.owner ?: return@put DoubleValue.ZERO
                return@put DoubleValue(entity.distanceTo(owner))
            }
            map["owner"] = put@{
                if (entity !is TamableAnimal) {
                    return@put DoubleValue.ZERO
                }
                val owner = entity.owner
                return@put owner?.asMostSpecificMoLangValue() ?: DoubleValue.ZERO
            }
            map["delta_movement"] = put@{
                return@put listOf(entity.deltaMovement.x, entity.deltaMovement.y, entity.deltaMovement.z).asArrayValue(
                    ::DoubleValue
                )
            }
            map["tags"] = put@{
                val tags = entity.tags
                val array = ArrayStruct(hashMapOf())
                tags.forEachIndexed { index, tag -> array.setDirectly("$index", StringValue(tag)) }
                return@put array
            }
            map["add_tag"] = put@{ params ->
                val tag = params.getString(0)
                entity.addTag(tag)
                return@put DoubleValue.ONE
            }
            map["remove_tag"] = put@{ params ->
                val tag = params.getString(0)
                entity.removeTag(tag)
                return@put DoubleValue.ONE
            }
            map["has_tag"] = put@{ params ->
                val tag = params.getString(0)
                return@put DoubleValue(entity.tags.contains(tag))
            }
            if (entity is MoLangScriptingEntity) {
                map["add_callback"] = { params ->
                    val type = params.getString(0).asIdentifierDefaultingNamespace()
                    val callback = params.getString(1).asIdentifierDefaultingNamespace()
                    val allowDuplicates = params.getBooleanOrNull(2) ?: false
                    entity.callbacks.addCallback(type, callback, allowDuplicates)
                }
                map["remove_callback"] = { params ->
                    val type = params.getString(0).asIdentifierDefaultingNamespace()
                    val callback = params.getString(1).asIdentifierDefaultingNamespace()
                    entity.callbacks.removeCallback(type, callback)
                }
                map["has_callback"] = put@{ params ->
                    val type = params.getString(0).asIdentifierDefaultingNamespace()
                    val callback = params.getString(1).asIdentifierDefaultingNamespace()
                    return@put DoubleValue(callback in (entity.callbacks[type] ?: emptySet()))
                }
                map["clear_callbacks"] = put@{ params ->
                    val type = params.getStringOrNull(0)?.asIdentifierDefaultingNamespace()
                    if (type != null) {
                        entity.callbacks[type]?.clear()
                    } else {
                        entity.callbacks.clear()
                    }
                    return@put DoubleValue.ONE
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
                    DoubleValue(entity.brain.getMemory(MemoryModuleType.WALK_TARGET).isPresent || entity.isPathFinding)
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
                    } else {
                        Cobblemon.LOGGER.error("Unknown pathfinding type: ${params.getString(0)}")
                        return@put DoubleValue.ZERO
                    }
                }
                map["set_pathfinding_malus"] = put@{ params ->
                    val type = PathType.entries.find { it.name == params.getString(0).uppercase() }
                    val malus = params.getDouble(1).toFloat()
                    if (type != null) {
                        entity.setPathfindingMalus(type, malus)
                        return@put DoubleValue.ONE
                    } else {
                        Cobblemon.LOGGER.error("Unknown pathfinding type: ${params.getString(0)}")
                        return@put DoubleValue.ZERO
                    }
                }
            }

            map["is_sneaking"] = { _ -> DoubleValue(entity.isShiftKeyDown) }
            map["is_sprinting"] = { _ -> DoubleValue(entity.isSprinting) }
            map["is_in_water"] = { _ -> DoubleValue(entity.isUnderWater) }
            map["is_in_rain"] = { _ -> DoubleValue(entity.isInWaterOrRain && !entity.isInWater) }
            map["is_touching_water_or_rain"] = { _ -> DoubleValue(entity.isInWaterRainOrBubble) }
            map["is_touching_water"] = { _ -> DoubleValue(entity.isInWater) }
            map["is_underwater"] = { DoubleValue(entity.getIsSubmerged()) }
            map["is_in_lava"] = { _ -> DoubleValue(entity.isInLava) }
            map["is_on_fire"] = { _ -> DoubleValue(entity.isOnFire) }
            map["is_invisible"] = { _ -> DoubleValue(entity.isInvisible) }
            map["is_riding"] = { _ -> DoubleValue(entity.isPassenger) }
            map["distance_to_pos"] = put@{ params ->
                val x = params.getDouble(0)
                val y = params.getDouble(1)
                val z = params.getDouble(2)
                return@put DoubleValue(sqrt(entity.distanceToSqr(Vec3(x, y, z))))
            }
            map["name"] = { _ -> StringValue(entity.effectiveName().string) }
            map["type"] = { _ ->
                entity.registryAccess().registry(Registries.ENTITY_TYPE).get().getKey(entity.type)?.toString()?.let {
                    StringValue(it)
                } ?: DoubleValue.ZERO
            }
            map["yaw"] = { _ -> DoubleValue(entity.yRot.toDouble()) }
            map["pitch"] = { _ -> DoubleValue(entity.xRot.toDouble()) }
            map["x"] = { _ -> DoubleValue(entity.x) }
            map["y"] = { _ -> DoubleValue(entity.y) }
            map["z"] = { _ -> DoubleValue(entity.z) }
            map["velocity_x"] = { _ -> DoubleValue(entity.deltaMovement.x) }
            map["velocity_y"] = { _ -> DoubleValue(entity.deltaMovement.y) }
            map["velocity_z"] = { _ -> DoubleValue(entity.deltaMovement.z) }
            map["width"] = { DoubleValue(entity.boundingBox.xsize) }
            map["height"] = { DoubleValue(entity.boundingBox.ysize) }
            map["entity_size"] = { DoubleValue(entity.boundingBox.run { if (xsize > ysize) xsize else ysize }) }
            map["entity_width"] = { DoubleValue(entity.boundingBox.xsize) }
            map["entity_height"] = { DoubleValue(entity.boundingBox.ysize) }
            map["id_modulo"] = { params -> DoubleValue(entity.uuid.hashCode() % params.getDouble(0)) }
            map["horizontal_velocity"] = { _ -> DoubleValue(entity.deltaMovement.horizontalDistance()) }
            map["vertical_velocity"] = { DoubleValue(entity.deltaMovement.y) }
            map["is_on_ground"] = { _ -> DoubleValue(entity.onGround()) }
            map["world"] = { _ -> entity.level().worldRegistry.wrapAsHolder(entity.level()).asWorldMoLangValue() }
            map["biome"] = { _ -> entity.level().getBiome(entity.blockPosition()).asBiomeMoLangValue() }
            map["is_passenger"] = { DoubleValue(entity.isPassenger) }
            map["find_nearby_block"] = put@{ params ->
                val input = params.getString(0)
                val isTag = input.contains("#")
                val type = input.replace("#", "").asIdentifierDefaultingNamespace(namespace = "minecraft")
                val range = params.getDoubleOrNull(1) ?: 10
                val blockPos = entity.level().getBlockStatesWithPos(
                    AABB.ofSize(
                        entity.position(),
                        range.toDouble(),
                        range.toDouble(),
                        range.toDouble()
                    )
                )
                    .filter { blockPosPair ->
                        blockPosPair.first.blockHolder.let {
                            if (isTag) it.`is`(
                                TagKey.create(
                                    Registries.BLOCK,
                                    type
                                )
                            ) else it.`is`(type)
                        }
                    }
                    .minByOrNull { it.second.distSqr(entity.blockPosition()) }
                    ?.second
                if (blockPos != null) {
                    return@put ArrayStruct(
                        mapOf(
                            "0" to DoubleValue(blockPos.x),
                            "1" to DoubleValue(blockPos.y),
                            "2" to DoubleValue(blockPos.z)
                        )
                    )
                } else {
                    return@put DoubleValue.ZERO
                }
            }
            map["get_nearby_entities"] = put@{ params ->
                val distance = params.getDouble(0)
                val entities =
                    entity.level().getEntities(entity, AABB.ofSize(entity.position(), distance, distance, distance))
                return@put entities
                    .filterIsInstance<Entity>()
                    .map { it.asMostSpecificMoLangValue() }
                    .asArrayValue()
            }
            map["is_standing_on_blocks"] = put@{ params ->
                val depth = params.getDouble(0).toInt()
                val blockStrings: MutableSet<String> = mutableSetOf()
                for (blockIndex in 1..<params.params.size) {
                    blockStrings.add(params.getString(blockIndex))
                }

                return@put if (entity.isStandingOn(blockStrings, depth)) DoubleValue.ONE else DoubleValue.ZERO
            }
            map["discard"] = {
                entity.discard()
            }
            if (entity is PosableEntity) {
                map["play_animation"] = put@{ params ->
                    val animation = params.getString(0)
                    val packet = PlayPosableAnimationPacket(entity.id, setOf(animation), emptyList())
                    val target = params.getStringOrNull(1)
                    if (target != null) {
                        val targetPlayer = if (target.asUUID != null) {
                            entity.level().getPlayerByUUID(target.asUUID!!) as ServerPlayer
                        } else if (entity.level() is ServerLevel) {
                            entity.level().server!!.playerList.getPlayerByName(target)
                        } else {
                            null
                        }
                        if (targetPlayer != null) {
                            packet.sendToPlayer(targetPlayer)
                            return@put DoubleValue.ONE
                        } else {
                            return@put DoubleValue.ZERO
                        }
                    } else {
                        packet.sendToPlayersAround(entity.x, entity.y, entity.z, 64.0, entity.level().dimension())
                        return@put DoubleValue.ONE
                    }
                }

                map["face"] = { params ->
                    ObjectValue(ReferenceDialogueFaceProvider(entity.id, params.getBooleanOrNull(0) != false))

                }
            }
            if (entity is Schedulable) {
                map["run_molang_after"] = { params ->
                    val expression = params.getString(0).asExpressionLike()
                    val delayInSeconds = params.getDouble(1).toFloat()
                    val runtime = MoLangRuntime()
                    runtime.environment.cloneFrom(params.environment)
                    entity.after(delayInSeconds) {
                        runtime.resolve(expression)
                    }
                }
            }
            // q.entity.spawn_bedrock_particles(effect, locator, [player]) - sends to everyone nearby or just to the player if they're set. Locator is necessary even if unused on non-posables.
            map["spawn_bedrock_particles"] = { params ->
                val particle = params.getString(0).asResource()
                val locator = params.getString(1)
                val player = params.getOrNull<MoValue>(2)?.let {
                    when (it) {
                        is StringValue -> entity.level().getPlayerByUUID(UUID.fromString(it.value))
                        is ObjectValue<*> -> it.obj
                        else -> null
                    }
                } as? ServerPlayer

                val packet = SpawnSnowstormEntityParticlePacket(particle, entity.id, listOf(locator))
                if (player == null) {
                    packet.sendToPlayersAround(entity.x, entity.y, entity.z, 64.0, entity.level().dimension())
                } else {
                    packet.sendToPlayer(player)
                }
            }
            map["make_intangible"] = put@{ params ->
                val intangible = params.getBooleanOrNull(0) ?: true
                entity.noPhysics = intangible
                return@put DoubleValue.ONE
            }
            return@mutableListOf map
        }
    )
}
