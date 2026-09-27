/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molang.function

import com.bedrockk.molang.runtime.MoParams
import com.bedrockk.molang.runtime.struct.ArrayStruct
import com.bedrockk.molang.runtime.value.DoubleValue
import com.bedrockk.molang.runtime.value.MoValue
import com.bedrockk.molang.runtime.value.StringValue
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonBlockEntities
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMoLangValue
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMostSpecificMoLangValue
import com.cobblemon.mod.common.api.molang.ObjectValue
import com.cobblemon.mod.common.api.npc.NPCClasses
import com.cobblemon.mod.common.api.spawning.TimeRange
import com.cobblemon.mod.common.entity.npc.NPCEntity
import com.cobblemon.mod.common.net.messages.client.effect.SpawnSnowstormParticlePacket
import com.cobblemon.mod.common.net.messages.client.sound.UnvalidatedPlaySoundS2CPacket
import com.cobblemon.mod.common.util.*
import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LightningBolt
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.level.Level
import net.minecraft.world.level.Level.ExplosionInteraction
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.storage.loot.LootParams
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets
import net.minecraft.world.level.storage.loot.parameters.LootContextParams
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.UUID

object WorldMoLangFunctions : AbstractMoLangFunctionHolder<Holder<Level>>() {
    override fun Holder<Level>.moLangFunctions(): MutableMap<String, (MoParams) -> Any> {
        val world = this.value()
        val functions = hashMapOf<String, (MoParams) -> Any>()

        functions["is_time_of_day"] = isTimeOfDay@{ params ->
            val timeOfDay =
                TimeRange.timeRanges[params.getString(0).lowercase()]
                    ?: return@isTimeOfDay DoubleValue.ZERO
            val time = world.dayTime % 24000

            DoubleValue(timeOfDay.contains(time.toInt()))
        }
        functions["game_time"] = { DoubleValue(world.gameTime.toDouble()) }
        functions["time_of_day"] = { DoubleValue((world.dayTime % 24000).toDouble()) }
        functions["server"] = { server()?.asMoLangValue() ?: DoubleValue.ZERO }
        functions["is_raining_at"] = { params ->
            val x = params.getInt(0)
            val y = params.getInt(1)
            val z = params.getInt(2)
            DoubleValue(world.isRainingAt(BlockPos(x, y, z)))
        }
        functions["is_snowing_at"] = { params ->
            val x = params.getInt(0)
            val y = params.getInt(1)
            val z = params.getInt(2)
            val blockPos = BlockPos(x, y, z)
            when {
                !world.isRaining -> {
                    DoubleValue.ZERO
                }

                !world.canSeeSky(blockPos) -> {
                    DoubleValue.ZERO
                }

                (world.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, blockPos).y > blockPos.y) -> {
                    DoubleValue.ZERO
                }

                else -> {
                    val biome = world.getBiome(blockPos).value() as Biome
                    DoubleValue(biome.getPrecipitationAt(blockPos) == Biome.Precipitation.SNOW)
                }
            }
        }
        functions["is_chunk_loaded_at"] = { params ->
            val x = params.getInt(0)
            val y = params.getInt(1)
            val z = params.getInt(2)
            DoubleValue(world.isLoaded(BlockPos(x, y, z)))
        }
        functions["is_thundering"] = { DoubleValue(world.isThundering) }
        functions["is_raining"] = { DoubleValue(world.isRaining) }
        functions["set_block"] = setBlock@{ params ->
            val x = params.getInt(0)
            val y = params.getInt(1)
            val z = params.getInt(2)
            val block =
                world.blockRegistry.get(params.getString(3).asIdentifierDefaultingNamespace())
                    ?: run {
                        Cobblemon.LOGGER.error("Unknown block: ${params.getString(3)}")
                        return@setBlock DoubleValue.ZERO
                    }
            world.setBlock(BlockPos(x, y, z), block.defaultBlockState(), Block.UPDATE_ALL)
        }
        functions["is_air"] = { params ->
            val x = params.getDouble(0).toInt()
            val y = params.getDouble(1).toInt()
            val z = params.getDouble(2).toInt()
            val blockState = world.getBlockState(BlockPos(x, y, z))
            DoubleValue(blockState.isAir)
        }
        functions["get_block"] = { params ->
            val x = params.getInt(0)
            val y = params.getInt(1)
            val z = params.getInt(2)
            val block = world.getBlockState(BlockPos(x, y, z)).block
            world.blockRegistry.wrapAsHolder(block).asMoLangValue(Registries.BLOCK)
        }
        functions["spawn_explosion"] = { params ->
            val x = params.getDouble(0)
            val y = params.getDouble(1)
            val z = params.getDouble(2)
            val range = params.getDouble(3).toFloat()
            world.explode(
                null,
                x,
                y,
                z,
                range,
                ExplosionInteraction.valueOf(
                    params.getStringOrNull(4)?.uppercase() ?: ExplosionInteraction.TNT.name,
                ),
            )
        }
        functions["spawn_lightning"] = { params ->
            val x = params.getDouble(0)
            val y = params.getDouble(1)
            val z = params.getDouble(2)
            val lightning = LightningBolt(EntityType.LIGHTNING_BOLT, world)
            lightning.setPos(x, y, z)
            world.addFreshEntity(lightning)
            DoubleValue.ONE
        }
        // q.entity.world.spawn_bedrock_particles(effect, x, y, z, [player]) - sends to everyone nearby or just to the player if they're set.
        functions["spawn_bedrock_particles"] = { params ->
            val particle = params.getString(0).asResource()
            val x = params.getDouble(1)
            val y = params.getDouble(2)
            val z = params.getDouble(3)
            val player =
                params.getOrNull<MoValue>(4)?.let {
                    when (it) {
                        is StringValue -> world.getPlayerByUUID(UUID.fromString(it.value))
                        is ObjectValue<*> -> it.obj
                        else -> null
                    }
                } as? ServerPlayer
            val pos = Vec3(x, y, z)

            val packet = SpawnSnowstormParticlePacket(particle, pos)
            if (player != null) {
                packet.sendToPlayer(player)
            } else {
                packet.sendToPlayersAround(x, y, z, 64.0, world.dimension())
            }
        }
        functions["spawn_pokemon"] = spawnPokemon@{ params ->
            val x = params.getInt(0)
            val y = params.getInt(1)
            val z = params.getInt(2)
            val props = params.getString(3).toProperties()

            val pos = BlockPos(x, y, z)

            if (!Level.isInSpawnableBounds(pos)) return@spawnPokemon DoubleValue.ZERO

            val pokemon = props.createEntity(world)
            pokemon.moveTo(pos, pokemon.yRot, pokemon.xRot)

            if (world.addFreshEntity(pokemon)) return@spawnPokemon pokemon.struct

            DoubleValue.ZERO
        }
        functions["spawn_npc"] = spawnNpc@{ params ->
            val x = params.getDouble(0)
            val y = params.getDouble(1)
            val z = params.getDouble(2)
            val npcClass =
                params
                    .getStringOrNull(3)
                    ?.let { NPCClasses.getByIdentifier(it.asIdentifierDefaultingNamespace()) }
            val level = params.getInt(4)

            if (npcClass == null) return@spawnNpc DoubleValue.ZERO

            val npc = NPCEntity(world)
            npc.moveTo(x, y, z, npc.yRot, npc.xRot)
            npc.npc = npcClass
            npc.initialize(level)

            if (world.addFreshEntity(npc)) return@spawnNpc npc.asMoLangValue()

            DoubleValue.ZERO
        }
        functions["play_sound_on_server"] = { params ->
            val sound = params.getString(0).asResource()
            val soundSource = params.getString(1).uppercase()
            val x = params.getDouble(2)
            val y = params.getDouble(3)
            val z = params.getDouble(4)
            val player =
                params.getOrNull<MoValue>(5)?.let {
                    when (it) {
                        is StringValue -> world.getPlayerByUUID(UUID.fromString(it.value))
                        is ObjectValue<*> -> it.obj
                        else -> null
                    }
                } as? ServerPlayer
            val volume = params.getDoubleOrNull(6)?.toFloat() ?: 1.0f
            val pitch = params.getDoubleOrNull(7)?.toFloat() ?: 1.0f

            val packet =
                UnvalidatedPlaySoundS2CPacket(sound, SoundSource.valueOf(soundSource), x, y, z, volume, pitch)
            if (player != null) {
                packet.sendToPlayer(player)
            } else {
                packet.sendToPlayersAround(x, y, z, 16.0, world.dimension())
            }
        }
        functions["get_entities_around"] = { params ->
            val x = params.getDouble(0)
            val y = params.getDouble(1)
            val z = params.getDouble(2)
            val range = params.getDouble(3) * 2
            val entities = world.getEntities(null, AABB.ofSize(Vec3(x, y, z), range, range, range))
            entities
                .filterIsInstance<LivingEntity>()
                .map { it.asMostSpecificMoLangValue() }
                .asArrayValue()
        }
        functions["is_healer_in_use"] = isHealerInUse@{ params ->
            val pos = params.get<ArrayStruct>(0).asBlockPos()
            val healer =
                world.getBlockEntity(pos, CobblemonBlockEntities.HEALING_MACHINE).orElse(null)
                    ?: return@isHealerInUse DoubleValue.ONE
            return@isHealerInUse DoubleValue(healer.isInUse)
        }

        functions["spawn_loot_table_items"] = spawnLootTableItems@{ params ->
            val serverLevel = world as? ServerLevel
            if (serverLevel == null) {
                Cobblemon.LOGGER.warn("spawn_loot_table_items: world is not a ServerLevel")
                return@spawnLootTableItems DoubleValue.ZERO
            }
            val lootTableId = params.getString(0).asIdentifierDefaultingNamespace()
            val x = params.getDouble(1)
            val y = params.getDouble(2)
            val z = params.getDouble(3)

            val lootTableKey = ResourceKey.create(Registries.LOOT_TABLE, lootTableId)
            val lootTable = serverLevel.server.reloadableRegistries().getLootTable(lootTableKey)

            val lootParams =
                LootParams
                    .Builder(serverLevel)
                    .withParameter(LootContextParams.ORIGIN, Vec3(x, y, z))
                    .create(LootContextParamSets.COMMAND)

            val items = lootTable.getRandomItems(lootParams)
            val spawnedItems = ArrayStruct(hashMapOf())
            var index = 0

            for (itemStack in items) {
                if (!itemStack.isEmpty) {
                    val itemEntity = ItemEntity(serverLevel, x, y, z, itemStack)
                    itemEntity.setDefaultPickUpDelay()
                    if (serverLevel.addFreshEntity(itemEntity)) {
                        spawnedItems.setDirectly("$index", itemStack.asMoLangValue(serverLevel.registryAccess()))
                        index++
                    } else {
                        Cobblemon.LOGGER.warn("spawn_loot_table_items: Failed to add entity to world for ${itemStack.item}")
                    }
                }
            }

            spawnedItems
        }

        return functions
    }
}
