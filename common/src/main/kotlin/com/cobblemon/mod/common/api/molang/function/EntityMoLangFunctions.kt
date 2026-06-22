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
import com.cobblemon.mod.common.util.*
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.TagKey
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.damagesource.DamageTypes
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.TamableAnimal
import net.minecraft.world.level.block.BonemealableBlock
import net.minecraft.world.level.block.CropBlock
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.*
import kotlin.math.sqrt

object EntityMoLangFunctions : AbstractMoLangFunctionHolder<Entity>() {
    override fun Entity.moLangFunctions(): MutableMap<String, (MoParams) -> Any> {
        val thisEntity = this
        val map = mutableMapOf<String, (MoParams) -> Any>(
            "uuid" to { StringValue(thisEntity.uuid.toString()) },
            "name" to { _ -> StringValue(thisEntity.effectiveName().string) },
            "yaw" to { _ -> DoubleValue(thisEntity.yRot.toDouble()) },
            "pitch" to { _ -> DoubleValue(thisEntity.xRot.toDouble()) },
            "x" to { _ -> DoubleValue(thisEntity.x) },
            "y" to { _ -> DoubleValue(thisEntity.y) },
            "z" to { _ -> DoubleValue(thisEntity.z) },
            "velocity_x" to { _ -> DoubleValue(thisEntity.deltaMovement.x) },
            "velocity_y" to { _ -> DoubleValue(thisEntity.deltaMovement.y) },
            "velocity_z" to { _ -> DoubleValue(thisEntity.deltaMovement.z) },
            "width" to { DoubleValue(thisEntity.boundingBox.xsize) },
            "height" to { DoubleValue(thisEntity.boundingBox.ysize) },
            "this_size" to { DoubleValue(thisEntity.boundingBox.run { if (xsize > ysize) xsize else ysize }) },
            "this_width" to { DoubleValue(thisEntity.boundingBox.xsize) },
            "this_height" to { DoubleValue(thisEntity.boundingBox.ysize) },
            "id_modulo" to { params -> DoubleValue(thisEntity.uuid.hashCode() % params.getDouble(0)) },
            "horizontal_velocity" to { _ -> DoubleValue(thisEntity.deltaMovement.horizontalDistance()) },
            "vertical_velocity" to { DoubleValue(thisEntity.deltaMovement.y) },
            "is_on_ground" to { _ -> DoubleValue(thisEntity.onGround()) },
            "world" to { _ -> thisEntity.level().worldRegistry.wrapAsHolder(thisEntity.level()).asWorldMoLangValue() },
            "dimension" to { StringValue(thisEntity.level().dimension().location().toString()) },
            "biome" to { _ -> thisEntity.level().getBiome(thisEntity.blockPosition()).asBiomeMoLangValue() },
            "is_passenger" to { DoubleValue(thisEntity.isPassenger) },
            "is_passenger" to isPassenger@{ params ->
                val passengerEntity = (params.get<MoValue>(0) as? ObjectValue<*>)?.obj as? LivingEntity ?: return@isPassenger DoubleValue.ZERO
                DoubleValue(passengerEntity in thisEntity.passengers)
            },
            "discard" to { thisEntity.discard() },
            "is_sneaking" to { _ -> DoubleValue(thisEntity.isShiftKeyDown) },
            "is_sprinting" to { _ -> DoubleValue(thisEntity.isSprinting) },
            "is_in_water" to { _ -> DoubleValue(thisEntity.isUnderWater) },
            "is_in_rain" to { _ -> DoubleValue(thisEntity.isInWaterOrRain && !thisEntity.isInWater) },
            "is_touching_water_or_rain" to { _ -> DoubleValue(thisEntity.isInWaterRainOrBubble) },
            "is_touching_water" to { _ -> DoubleValue(thisEntity.isInWater) },
            "is_underwater" to { DoubleValue(thisEntity.getIsSubmerged()) },
            "is_in_lava" to { _ -> DoubleValue(thisEntity.isInLava) },
            "is_on_fire" to { _ -> DoubleValue(thisEntity.isOnFire) },
            "is_invisible" to { _ -> DoubleValue(thisEntity.isInvisible) },
            "is_riding" to { _ -> DoubleValue(thisEntity.isPassenger) },
            "set_name" to { params ->
                val name = params.getString(0)
                thisEntity.customName = name.text()
                DoubleValue.ONE
            },
            "damage" to { params ->
                val amount = params.getDouble(0)
                val source = DamageSource(
                    thisEntity.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                        .getHolder(DamageTypes.GENERIC).get()
                )
                thisEntity.hurt(source, amount.toFloat())
            },
            "distance_to_owner" to distanceToOwner@{
                if (thisEntity !is TamableAnimal) {
                    return@distanceToOwner DoubleValue.ZERO
                }
                val owner = thisEntity.owner ?: return@distanceToOwner DoubleValue.ZERO
                DoubleValue(thisEntity.distanceTo(owner))
            },
            "owner" to owner@{
                if (thisEntity !is TamableAnimal) return@owner DoubleValue.ZERO
                val owner = thisEntity.owner
                owner?.asMostSpecificMoLangValue() ?: DoubleValue.ZERO
            },
            "delta_movement" to {
                listOf(thisEntity.deltaMovement.x, thisEntity.deltaMovement.y, thisEntity.deltaMovement.z)
                    .asArrayValue(::DoubleValue)
            },
            "set_position" to { params ->
                val x = params.getDouble(0)
                val y = params.getDouble(1)
                val z = params.getDouble(2)
                thisEntity.setPos(x, y, z)
                DoubleValue.ONE
            },
            "fertilize_nearby_crops" to { params -> //TODO: pretty bespoke. Maybe should be moved or generalized?
                val radius = params.getIntOrNull(0) ?: 8
                val maxCrops = params.getIntOrNull(1) ?: 8

                var fertilizedCount = 0
                val level = thisEntity.level()
                val centerPos = thisEntity.blockPosition()

                // Find all bonemealable blocks within radius
                val bonemealableBlocks = mutableListOf<BlockPos>()

                for (x in -radius..radius) {
                    for (y in -3..3) { // Limited vertical range
                        for (z in -radius..radius) {
                            val pos = centerPos.offset(x, y, z)
                            val blockState = level.getBlockState(pos)
                            val block = blockState.block

                            // Check if block can be bonemealed
                            if (block is BonemealableBlock && block is CropBlock && block.isValidBonemealTarget(level, pos, blockState)) {
                                bonemealableBlocks.add(pos)
                            }
                        }
                    }
                }

                // Shuffle and take up to maxCrops blocks to fertilize
                bonemealableBlocks.shuffle()
                val blocksToFertilize = bonemealableBlocks.take(maxCrops)

                for (pos in blocksToFertilize) {
                    val blockState = level.getBlockState(pos)
                    val block = blockState.block

                    if (block is BonemealableBlock && block.isValidBonemealTarget(level, pos, blockState)) {
                        if (block.isBonemealSuccess(level, level.random, pos, blockState)) {
                            block.performBonemeal(level as ServerLevel, level.random, pos, blockState)

                            // Add bonemeal particle
                            val particlePos = block.getParticlePos(pos)
                            level.levelEvent(1505, particlePos, 15) // Same event used for bonemealing in vanilla

                            fertilizedCount++
                        }
                    }
                }

                DoubleValue(fertilizedCount.toDouble())
            },
            "tags" to {
                val tags = thisEntity.tags
                val array = ArrayStruct(hashMapOf())
                tags.forEachIndexed { index, tag -> array.setDirectly("$index", StringValue(tag)) }
                array
            },
            "add_tag" to { params ->
                val tag = params.getString(0)
                thisEntity.addTag(tag)
                DoubleValue.ONE
            },
            "remove_tag" to { params ->
                val tag = params.getString(0)
                thisEntity.removeTag(tag)
                DoubleValue.ONE
            },
            "has_tag" to { params ->
                val tag = params.getString(0)
                DoubleValue(thisEntity.tags.contains(tag))
            },
            "distance_to_pos" to { params ->
                val x = params.getDouble(0)
                val y = params.getDouble(1)
                val z = params.getDouble(2)
                DoubleValue(sqrt(thisEntity.distanceToSqr(Vec3(x, y, z))))
            },
            "type" to { _ ->
                thisEntity.registryAccess().registry(Registries.ENTITY_TYPE).get().getKey(thisEntity.type)?.toString()?.let {
                    StringValue(it)
                } ?: DoubleValue.ZERO
            },
            "find_nearby_blocks" to { params ->
                val input = params.getString(0)
                val isTag = input.contains("#")
                val type = input.replace("#", "").asIdentifierDefaultingNamespace(namespace = "minecraft")

                val range = params.getDoubleOrNull(1) ?: 10

                val blockPosList = thisEntity.level().getBlockStatesWithPos(
                    AABB.ofSize(
                        thisEntity.position(),
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
                .map { blockPosPair -> blockPosPair.second }

                blockPosList.map { it.toArrayStruct() }.asArrayValue()
            },
            "get_nearby_entities" to { params ->
                val distance = params.getDouble(0)
                val entities =
                    thisEntity.level().getEntities(thisEntity, AABB.ofSize(thisEntity.position(), distance, distance, distance))

                entities
                    .filterIsInstance<Entity>()
                    .map { it.asMostSpecificMoLangValue() }
                    .asArrayValue()
            },
            "is_standing_on_blocks" to { params ->
                val depth = params.getDouble(0).toInt()
                val blockStrings: MutableSet<String> = mutableSetOf()
                for (blockIndex in 1..<params.params.size) {
                    blockStrings.add(params.getString(blockIndex))
                }

                if (thisEntity.isStandingOn(blockStrings, depth)) DoubleValue.ONE else DoubleValue.ZERO
            },

            // q.entity.spawn_bedrock_particles(effect, locator, [player])
            // sends to everyone nearby or just to the player if they're set.
            // Locator is necessary even if unused on non-posables.
            "spawn_bedrock_particles" to { params ->
                val particle = params.getString(0).asResource()
                val locator = params.getString(1)
                val player = params.getOrNull<MoValue>(2)?.let {
                    when (it) {
                        is StringValue -> thisEntity.level().getPlayerByUUID(UUID.fromString(it.value))
                        is ObjectValue<*> -> it.obj
                        else -> null
                    }
                } as? ServerPlayer

                val packet = SpawnSnowstormEntityParticlePacket(particle, thisEntity.id, listOf(locator))
                if (player == null) {
                    packet.sendToPlayersAround(thisEntity.x, thisEntity.y, thisEntity.z, 64.0, thisEntity.level().dimension())
                } else {
                    packet.sendToPlayer(player)
                }
            },
            "make_intangible" to { params ->
                val intangible = params.getBooleanOrNull(0) ?: true
                thisEntity.noPhysics = intangible
                DoubleValue.ONE
            },
        )


        if (thisEntity is PosableEntity) {
            map["play_animation"] = playAnimation@{ params ->
                val animation = params.getString(0)
                val packet = PlayPosableAnimationPacket(thisEntity.id, setOf(animation), emptyList())
                val target = params.getStringOrNull(1)
                if (target != null) {
                    val targetPlayer = if (target.asUUID != null) {
                        thisEntity.level().getPlayerByUUID(target.asUUID!!) as ServerPlayer
                    } else if (thisEntity.level() is ServerLevel) {
                        thisEntity.level().server!!.playerList.getPlayerByName(target)
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
                    packet.sendToPlayersAround(thisEntity.x, thisEntity.y, thisEntity.z, 64.0, thisEntity.level().dimension())
                    return@playAnimation DoubleValue.ONE
                }
            }

            map["face"] = { params ->
                ObjectValue(ReferenceDialogueFaceProvider(thisEntity.id, params.getBooleanOrNull(0) != false))

            }
        }

        if (thisEntity is Schedulable) {
            map["run_molang_after"] = { params ->
                val expression = params.getString(0).asExpressionLike()
                val delayInSeconds = params.getDouble(1).toFloat()
                val runtime = MoLangRuntime()
                runtime.environment.cloneFrom(params.environment)
                thisEntity.after(delayInSeconds) {
                    runtime.resolve(expression)
                }
            }
        }

        if (thisEntity is MoLangScriptingEntity) {
            map["add_callback"] = { params ->
                val type = params.getString(0).asIdentifierDefaultingNamespace()
                val callback = params.getString(1).asIdentifierDefaultingNamespace()
                val allowDuplicates = params.getBooleanOrNull(2) ?: false
                thisEntity.callbacks.addCallback(type, callback, allowDuplicates)
            }
            map["remove_callback"] = { params ->
                val type = params.getString(0).asIdentifierDefaultingNamespace()
                val callback = params.getString(1).asIdentifierDefaultingNamespace()
                thisEntity.callbacks.removeCallback(type, callback)
            }
            map["has_callback"] = { params ->
                val type = params.getString(0).asIdentifierDefaultingNamespace()
                val callback = params.getString(1).asIdentifierDefaultingNamespace()
                DoubleValue(callback in (thisEntity.callbacks[type] ?: emptySet()))
            }
            map["clear_callbacks"] = { params ->
                val type = params.getStringOrNull(0)?.asIdentifierDefaultingNamespace()
                if (type != null) {
                    thisEntity.callbacks[type]?.clear()
                } else {
                    thisEntity.callbacks.clear()
                }
                DoubleValue.ONE
            }
        }

        return map
    }
}
