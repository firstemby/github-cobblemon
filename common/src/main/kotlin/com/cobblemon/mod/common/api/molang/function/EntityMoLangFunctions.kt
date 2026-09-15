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
import net.minecraft.world.entity.TamableAnimal
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.math.sqrt

object EntityMoLangFunctions : AbstractMoLangFunctionHolder<Entity>() {
    override fun Entity.moLangFunctions(): MutableMap<String, (MoParams) -> Any> {
        val entity = this
        val functions = hashMapOf<String, (MoParams) -> Any>()

        functions["uuid"] = { StringValue(entity.uuid.toString()) }
        functions["name"] = { StringValue(entity.effectiveName().string) }
        functions["yaw"] = { DoubleValue(entity.yRot.toDouble()) }
        functions["pitch"] = { DoubleValue(entity.xRot.toDouble()) }
        functions["x"] = { DoubleValue(entity.x) }
        functions["y"] = { DoubleValue(entity.y) }
        functions["z"] = { DoubleValue(entity.z) }
        functions["velocity_x"] = { DoubleValue(entity.deltaMovement.x) }
        functions["velocity_y"] = { DoubleValue(entity.deltaMovement.y) }
        functions["velocity_z"] = { DoubleValue(entity.deltaMovement.z) }
        functions["width"] = { DoubleValue(entity.boundingBox.xsize) }
        functions["height"] = { DoubleValue(entity.boundingBox.ysize) }
        functions["this_size"] = { DoubleValue(entity.boundingBox.run { if (xsize > ysize) xsize else ysize }) }
        functions["this_width"] = { DoubleValue(entity.boundingBox.xsize) }
        functions["this_height"] = { DoubleValue(entity.boundingBox.ysize) }
        functions["id_modulo"] = { params -> DoubleValue(entity.uuid.hashCode() % params.getDouble(0)) }
        functions["horizontal_velocity"] = { DoubleValue(entity.deltaMovement.horizontalDistance()) }
        functions["vertical_velocity"] = { DoubleValue(entity.deltaMovement.y) }
        functions["is_on_ground"] = { DoubleValue(entity.onGround()) }
        functions["world"] = {
            entity
                .level()
                .worldRegistry
                .wrapAsHolder(entity.level())
                .asWorldMoLangValue()
        }
        functions["biome"] = { entity.level().getBiome(entity.blockPosition()).asBiomeMoLangValue() }
        functions["is_passenger"] = { DoubleValue(entity.isPassenger) }
        functions["discard"] = { entity.discard() }
        functions["is_sneaking"] = { DoubleValue(entity.isShiftKeyDown) }
        functions["is_sprinting"] = { DoubleValue(entity.isSprinting) }
        functions["is_in_water"] = { DoubleValue(entity.isUnderWater) }
        functions["is_in_rain"] = { DoubleValue(entity.isInWaterOrRain && !entity.isInWater) }
        functions["is_touching_water_or_rain"] = { DoubleValue(entity.isInWaterRainOrBubble) }
        functions["is_touching_water"] = { DoubleValue(entity.isInWater) }
        functions["is_underwater"] = { DoubleValue(entity.getIsSubmerged()) }
        functions["is_in_lava"] = { DoubleValue(entity.isInLava) }
        functions["is_on_fire"] = { DoubleValue(entity.isOnFire) }
        functions["is_invisible"] = { DoubleValue(entity.isInvisible) }
        functions["is_riding"] = { DoubleValue(entity.isPassenger) }
        functions["set_name"] = { params ->
            val name = params.getString(0)
            entity.customName = name.text()
            DoubleValue.ONE
        }
        functions["damage"] = { params ->
            val amount = params.getDouble(0)
            val source =
                DamageSource(
                    entity
                        .level()
                        .registryAccess()
                        .registryOrThrow(Registries.DAMAGE_TYPE)
                        .getHolder(DamageTypes.GENERIC)
                        .get(),
                )
            entity.hurt(source, amount.toFloat())
        }
        functions["distance_to_owner"] = distanceToOwner@{
            if (entity !is TamableAnimal) {
                return@distanceToOwner DoubleValue.ZERO
            }
            val owner = entity.owner ?: return@distanceToOwner DoubleValue.ZERO
            DoubleValue(entity.distanceTo(owner))
        }
        functions["owner"] = owner@{
            if (entity !is TamableAnimal) return@owner DoubleValue.ZERO
            val owner = entity.owner
            owner?.asMostSpecificMoLangValue() ?: DoubleValue.ZERO
        }
        functions["delta_movement"] = {
            listOf(entity.deltaMovement.x, entity.deltaMovement.y, entity.deltaMovement.z)
                .asArrayValue(::DoubleValue)
        }
        functions["tags"] = {
            val tags = entity.tags
            val array = ArrayStruct(hashMapOf())
            tags.forEachIndexed { index, tag -> array.setDirectly("$index", StringValue(tag)) }
            array
        }
        functions["add_tag"] = { params ->
            val tag = params.getString(0)
            entity.addTag(tag)
            DoubleValue.ONE
        }
        functions["remove_tag"] = { params ->
            val tag = params.getString(0)
            entity.removeTag(tag)
            DoubleValue.ONE
        }
        functions["has_tag"] = { params ->
            val tag = params.getString(0)
            DoubleValue(entity.tags.contains(tag))
        }
        functions["distance_to_pos"] = { params ->
            val x = params.getDouble(0)
            val y = params.getDouble(1)
            val z = params.getDouble(2)
            DoubleValue(sqrt(entity.distanceToSqr(Vec3(x, y, z))))
        }
        functions["type"] = {
            entity
                .registryAccess()
                .registry(Registries.ENTITY_TYPE)
                .get()
                .getKey(entity.type)
                ?.toString()
                ?.let {
                    StringValue(it)
                } ?: DoubleValue.ZERO
        }
        functions["find_nearby_block"] = findNearbyBlock@{ params ->
            val input = params.getString(0)
            val isTag = input.contains("#")
            val type = input.replace("#", "").asIdentifierDefaultingNamespace(namespace = "minecraft")
            val range = params.getDoubleOrNull(1) ?: 10
            val blockPos =
                entity
                    .level()
                    .getBlockStatesWithPos(
                        AABB.ofSize(
                            entity.position(),
                            range.toDouble(),
                            range.toDouble(),
                            range.toDouble(),
                        ),
                    ).filter { blockPosPair ->
                        blockPosPair.first.blockHolder.let {
                            if (isTag) {
                                it.`is`(
                                    TagKey.create(
                                        Registries.BLOCK,
                                        type,
                                    ),
                                )
                            } else {
                                it.`is`(type)
                            }
                        }
                    }.minByOrNull { it.second.distSqr(entity.blockPosition()) }
                    ?.second
            if (blockPos != null) {
                return@findNearbyBlock ArrayStruct(
                    mapOf(
                        "0" to DoubleValue(blockPos.x),
                        "1" to DoubleValue(blockPos.y),
                        "2" to DoubleValue(blockPos.z),
                    ),
                )
            }
            return@findNearbyBlock DoubleValue.ZERO
        }
        functions["get_nearby_entities"] = { params ->
            val distance = params.getDouble(0)
            val entities =
                entity
                    .level()
                    .getEntities(entity, AABB.ofSize(entity.position(), distance, distance, distance))

            entities
                .filterIsInstance<Entity>()
                .map { it.asMostSpecificMoLangValue() }
                .asArrayValue()
        }
        functions["is_standing_on_blocks"] = { params ->
            val depth = params.getDouble(0).toInt()
            val blockStrings: MutableSet<String> = mutableSetOf()
            for (blockIndex in 1..<params.params.size) {
                blockStrings.add(params.getString(blockIndex))
            }

            if (entity.isStandingOn(blockStrings, depth)) DoubleValue.ONE else DoubleValue.ZERO
        }

        // q.entity.spawn_bedrock_particles(effect, locator, [player])
        // sends to everyone nearby or just to the player if they're set.
        // Locator is necessary even if unused on non-posables.
        functions["spawn_bedrock_particles"] = { params ->
            val particle = params.getString(0).asResource()
            val locator = params.getString(1)
            val player =
                params.getOrNull<MoValue>(2)?.let {
                    when (it) {
                        is StringValue -> entity.level().getPlayerByUUID(UUID.fromString(it.value))
                        is ObjectValue<*> -> it.obj
                        else -> null
                    }
                } as? ServerPlayer

            val packet = SpawnSnowstormEntityParticlePacket(particle, entity.id, listOf(locator))
            if (player == null) {
                packet.sendToPlayersAround(
                    entity.x,
                    entity.y,
                    entity.z,
                    64.0,
                    entity.level().dimension(),
                )
            } else {
                packet.sendToPlayer(player)
            }
        }
        functions["make_intangible"] = { params ->
            val intangible = params.getBooleanOrNull(0) ?: true
            entity.noPhysics = intangible
            DoubleValue.ONE
        }

        when (entity) {
            is PosableEntity -> {
                functions["play_animation"] = playAnimation@{ params ->
                    val animation = params.getString(0)
                    val packet = PlayPosableAnimationPacket(entity.id, setOf(animation), emptyList())
                    val target = params.getStringOrNull(1)
                    if (target != null) {
                        val targetPlayer =
                            if (target.asUUID != null) {
                                entity.level().getPlayerByUUID(target.asUUID!!) as ServerPlayer
                            } else if (entity.level() is ServerLevel) {
                                entity
                                    .level()
                                    .server!!
                                    .playerList
                                    .getPlayerByName(target)
                            } else {
                                null
                            }
                        if (targetPlayer != null) {
                            packet.sendToPlayer(targetPlayer)
                            return@playAnimation DoubleValue.ONE
                        } else {
                            return@playAnimation DoubleValue.ZERO
                        }
                    } else {
                        packet.sendToPlayersAround(
                            entity.x,
                            entity.y,
                            entity.z,
                            64.0,
                            entity.level().dimension(),
                        )
                        return@playAnimation DoubleValue.ONE
                    }
                }

                functions["face"] = { params ->
                    ObjectValue(ReferenceDialogueFaceProvider(entity.id, params.getBooleanOrNull(0) != false))
                }
            }

            is Schedulable -> {
                functions["run_molang_after"] = { params ->
                    val expression = params.getString(0).asExpressionLike()
                    val delayInSeconds = params.getDouble(1).toFloat()
                    val runtime = MoLangRuntime()
                    runtime.environment.cloneFrom(params.environment)
                    entity.after(delayInSeconds) {
                        runtime.resolve(expression)
                    }
                }
            }

            is MoLangScriptingEntity -> {
                functions["add_callback"] = { params ->
                    val type = params.getString(0).asIdentifierDefaultingNamespace()
                    val callback = params.getString(1).asIdentifierDefaultingNamespace()
                    val allowDuplicates = params.getBooleanOrNull(2) ?: false
                    entity.callbacks.addCallback(type, callback, allowDuplicates)
                }
                functions["remove_callback"] = { params ->
                    val type = params.getString(0).asIdentifierDefaultingNamespace()
                    val callback = params.getString(1).asIdentifierDefaultingNamespace()
                    entity.callbacks.removeCallback(type, callback)
                }
                functions["has_callback"] = { params ->
                    val type = params.getString(0).asIdentifierDefaultingNamespace()
                    val callback = params.getString(1).asIdentifierDefaultingNamespace()
                    DoubleValue(callback in (entity.callbacks[type] ?: emptySet()))
                }
                functions["clear_callbacks"] = { params ->
                    val type = params.getStringOrNull(0)?.asIdentifierDefaultingNamespace()
                    if (type != null) {
                        entity.callbacks[type]?.clear()
                    } else {
                        entity.callbacks.clear()
                    }
                    DoubleValue.ONE
                }
            }
        }

        return functions
    }
}
