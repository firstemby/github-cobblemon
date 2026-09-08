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
import java.util.UUID
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

object WorldMoLangFunctions : AbstractMoLangFunctionHolder<Holder<Level>>() {
    override fun Holder<Level>.moLangFunctions(): MutableMap<String, (MoParams) -> Any> {

        val world = this.value()
        val map = mutableMapOf<String, (MoParams) -> Any>(
        )

        map["is_time_of_day"] = put@{ params ->

            val timeOfDay = TimeRange.timeRanges[params.getString(0).lowercase()]
                ?: return@put DoubleValue.ZERO
            val time = world.dayTime % 24000
            return@put DoubleValue(timeOfDay.contains(time.toInt()))
        }
        map["game_time"] = { _ -> DoubleValue(world.gameTime.toDouble()) }
        map["time_of_day"] = put@{
            val time = world.dayTime % 24000
            return@put DoubleValue(time.toDouble())
        }
        map["server"] = { _ -> server()?.asMoLangValue() ?: DoubleValue.ZERO }
        map["is_raining_at"] = put@{ params ->
            val x = params.getInt(0)
            val y = params.getInt(1)
            val z = params.getInt(2)
            return@put DoubleValue(world.isRainingAt(BlockPos(x, y, z)))
        }
        map["is_snowing_at"] = put@{ params ->
            val x = params.getInt(0)
            val y = params.getInt(1)
            val z = params.getInt(2)
            val blockPos = BlockPos(x, y, z)
            when {
                !world.isRaining -> return@put DoubleValue.ZERO
                !world.canSeeSky(blockPos) -> return@put DoubleValue.ZERO
                (world.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, blockPos).y > blockPos.y) -> {
                    return@put DoubleValue.ZERO
                }

                else -> {
                    val biome = world.getBiome(blockPos).value() as Biome
                    return@put DoubleValue(biome.getPrecipitationAt(blockPos) == Biome.Precipitation.SNOW)
                }
            }
        }
        map["is_chunk_loaded_at"] = put@{ params ->
            val x = params.getInt(0)
            val y = params.getInt(1)
            val z = params.getInt(2)
            return@put DoubleValue(world.isLoaded(BlockPos(x, y, z)))
        }
        map["is_thundering"] = { _ -> DoubleValue(world.isThundering) }
        map["is_raining"] = { _ -> DoubleValue(world.isRaining) }
        map["set_block"] = put@{ params ->
            val x = params.getInt(0)
            val y = params.getInt(1)
            val z = params.getInt(2)
            val block = world.blockRegistry.get(params.getString(3).asIdentifierDefaultingNamespace())
                ?: run {
                    Cobblemon.LOGGER.error("Unknown block: ${params.getString(3)}")
                    return@put DoubleValue.ZERO
                }
            world.setBlock(BlockPos(x, y, z), block.defaultBlockState(), Block.UPDATE_ALL)
        }
        map["is_air"] = put@{ params ->
            val x = params.getDouble(0).toInt()
            val y = params.getDouble(1).toInt()
            val z = params.getDouble(2).toInt()
            val blockState = world.getBlockState(BlockPos(x, y, z))
            return@put DoubleValue(blockState.isAir)
        }
        map["get_block"] = put@{ params ->
            val x = params.getInt(0)
            val y = params.getInt(1)
            val z = params.getInt(2)
            val block = world.getBlockState(BlockPos(x, y, z)).block
            return@put world.blockRegistry.wrapAsHolder(block).asMoLangValue(Registries.BLOCK)
        }
        map["spawn_explosion"] = { params ->
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
                    params.getStringOrNull(4)?.uppercase() ?: ExplosionInteraction.TNT.name
                )
            )
        }
        map["spawn_lightning"] = put@{ params ->
            val x = params.getDouble(0)
            val y = params.getDouble(1)
            val z = params.getDouble(2)
            val lightning = LightningBolt(EntityType.LIGHTNING_BOLT, world)
            lightning.setPos(x, y, z)
            world.addFreshEntity(lightning)
            return@put DoubleValue.ONE
        }
        // q.entity.world.spawn_bedrock_particles(effect, x, y, z, [player]) - sends to everyone nearby or just to the player if they're set.
        map["spawn_bedrock_particles"] = { params ->
            val particle = params.getString(0).asResource()
            val x = params.getDouble(1)
            val y = params.getDouble(2)
            val z = params.getDouble(3)
            val player = params.getOrNull<MoValue>(4)?.let {
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
        map["spawn_pokemon"] = put@{ params ->
            val x = params.getInt(0)
            val y = params.getInt(1)
            val z = params.getInt(2)
            val props = params.getString(3).toProperties()

            val pos = BlockPos(x, y, z)

            if (!Level.isInSpawnableBounds(pos)) {
                return@put DoubleValue.ZERO
            }

            val pokemon = props.createEntity(world)
            pokemon.moveTo(pos, pokemon.yRot, pokemon.xRot)

            if (world.addFreshEntity(pokemon)) {
                return@put pokemon.struct
            } else {
                return@put DoubleValue.ZERO
            }
        }
        map["spawn_npc"] = put@{ params ->
            val x = params.getDouble(0)
            val y = params.getDouble(1)
            val z = params.getDouble(2)
            val npcClass = params.getStringOrNull(3)
                ?.let { NPCClasses.getByIdentifier(it.asIdentifierDefaultingNamespace()) }
            val level = params.getInt(4)
            if (npcClass == null) return@put DoubleValue.ZERO
            val npc = NPCEntity(world)
            npc.moveTo(x, y, z, npc.yRot, npc.xRot)
            npc.npc = npcClass
            npc.initialize(level)
            if (world.addFreshEntity(npc)) {
                return@put npc.asMoLangValue()
            }
            return@put DoubleValue.ZERO
        }
        map["play_sound_on_server"] = { params ->
            val sound = params.getString(0).asResource()
            val soundSource = params.getString(1).uppercase()
            val x = params.getDouble(2)
            val y = params.getDouble(3)
            val z = params.getDouble(4)
            val player = params.getOrNull<MoValue>(5)?.let {
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
        map["get_entities_around"] = put@{ params ->
            val x = params.getDouble(0)
            val y = params.getDouble(1)
            val z = params.getDouble(2)
            val range = params.getDouble(3) * 2
            val entities = world.getEntities(null, AABB.ofSize(Vec3(x, y, z), range, range, range))
            return@put entities
                .filterIsInstance<LivingEntity>()
                .map { it.asMostSpecificMoLangValue() }
                .asArrayValue()
        }
        map["is_healer_in_use"] = put@{ params ->
            val pos = params.get<ArrayStruct>(0).asBlockPos()
            val healer = world.getBlockEntity(pos, CobblemonBlockEntities.HEALING_MACHINE).orElse(null)
                ?: return@put DoubleValue.ONE
            return@put DoubleValue(healer.isInUse)
        }

        map["spawn_loot_table_items"] = put@{ params ->
            val serverLevel = world as? ServerLevel
            if (serverLevel == null) {
                Cobblemon.LOGGER.warn("spawn_loot_table_items: world is not a ServerLevel")
                return@put DoubleValue.ZERO
            }
            val lootTableId = params.getString(0).asIdentifierDefaultingNamespace()
            val x = params.getDouble(1)
            val y = params.getDouble(2)
            val z = params.getDouble(3)

            val lootTableKey = ResourceKey.create(Registries.LOOT_TABLE, lootTableId)
            val lootTable = serverLevel.server.reloadableRegistries().getLootTable(lootTableKey)

            val lootParams = LootParams.Builder(serverLevel)
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
            return@put spawnedItems
        }

        return map
    }
}
