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
import com.cobblemon.mod.common.util.asArrayValue
import com.cobblemon.mod.common.util.asBlockPos
import com.cobblemon.mod.common.util.asIdentifierDefaultingNamespace
import com.cobblemon.mod.common.util.asResource
import com.cobblemon.mod.common.util.blockRegistry
import com.cobblemon.mod.common.util.getDoubleOrNull
import com.cobblemon.mod.common.util.getOrNull
import com.cobblemon.mod.common.util.getStringOrNull
import com.cobblemon.mod.common.util.server
import com.cobblemon.mod.common.util.toProperties
import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.core.registries.Registries
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LightningBolt
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.Level
import net.minecraft.world.level.Level.ExplosionInteraction
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.*

object WorldMoLangFunctions {
    val holder: MutableList<(Holder<Level>) -> MutableMap<String, (MoParams) -> Any>> =
        mutableListOf(
            { worldHolder ->
                val world = worldHolder.value()
                val map = mutableMapOf<String, (MoParams) -> Any>(
                )

                map.put("is_time_of_day") { params ->

                    val timeOfDay = TimeRange.timeRanges[params.getString(0).lowercase()]
                        ?: return@put DoubleValue.ZERO
                    val time = world.dayTime % 24000
                    return@put DoubleValue(timeOfDay.contains(time.toInt()))
                }
                map.put("game_time") { _ -> DoubleValue(world.gameTime.toDouble()) }
                map.put("time_of_day") {
                    val time = world.dayTime % 24000
                    return@put DoubleValue(time.toDouble())
                }
                map.put("server") { _ -> server()?.asMoLangValue() ?: DoubleValue.ZERO }
                map.put("is_raining_at") { params ->
                    val x = params.getInt(0)
                    val y = params.getInt(1)
                    val z = params.getInt(2)
                    return@put DoubleValue(world.isRainingAt(BlockPos(x, y, z)))
                }
                map.put("is_snowing_at") { params ->
                    val x = params.getInt(0)
                    val y = params.getInt(1)
                    val z = params.getInt(2)
                    val blockPos = BlockPos(x, y, z)
                    if (!world.isRaining()) {
                        return@put DoubleValue.ZERO
                    } else if (!world.canSeeSky(blockPos)) {
                        return@put DoubleValue.ZERO
                    } else if (world.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, blockPos)
                            .getY() > blockPos.getY()
                    ) {
                        return@put DoubleValue.ZERO
                    } else {
                        val biome = world.getBiome(blockPos).value() as Biome
                        return@put DoubleValue(biome.getPrecipitationAt(blockPos) == Biome.Precipitation.SNOW)
                    }
                }
                map.put("is_chunk_loaded_at") { params ->
                    val x = params.getInt(0)
                    val y = params.getInt(1)
                    val z = params.getInt(2)
                    return@put DoubleValue(world.isLoaded(BlockPos(x, y, z)))
                }
                map.put("is_thundering") { _ -> DoubleValue(world.isThundering) }
                map.put("is_raining") { _ -> DoubleValue(world.isRaining) }
                map.put("set_block") { params ->
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
                map.put("is_air") { params ->
                    val x = params.getDouble(0).toInt()
                    val y = params.getDouble(1).toInt()
                    val z = params.getDouble(2).toInt()
                    val blockState = world.getBlockState(BlockPos(x, y, z))
                    return@put DoubleValue(blockState.isAir)
                }
                map.put("get_block") { params ->
                    val x = params.getInt(0)
                    val y = params.getInt(1)
                    val z = params.getInt(2)
                    val block = world.getBlockState(BlockPos(x, y, z)).block
                    return@put world.blockRegistry.wrapAsHolder(block).asMoLangValue(Registries.BLOCK)
                }
                map.put("spawn_explosion") { params ->
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
                map.put("spawn_lightning") { params ->
                    val x = params.getDouble(0)
                    val y = params.getDouble(1)
                    val z = params.getDouble(2)
                    val lightning = LightningBolt(EntityType.LIGHTNING_BOLT, world)
                    lightning.setPos(x, y, z)
                    world.addFreshEntity(lightning)
                    return@put DoubleValue.ONE
                }
                // q.entity.world.spawn_bedrock_particles(effect, x, y, z, [player]) - sends to everyone nearby or just to the player if they're set.
                map.put("spawn_bedrock_particles") { params ->
                    val particle = params.getString(0).asResource()
                    val x = params.getDouble(1)
                    val y = params.getDouble(2)
                    val z = params.getDouble(3)
                    val player = params.getOrNull<MoValue>(4)?.let {
                        if (it is StringValue) world.getPlayerByUUID(UUID.fromString(it.value))
                        else if (it is ObjectValue<*>) it.obj
                        else null
                    } as? ServerPlayer
                    val pos = Vec3(x, y, z)

                    val packet = SpawnSnowstormParticlePacket(particle, pos)
                    if (player != null) {
                        packet.sendToPlayer(player)
                    } else {
                        packet.sendToPlayersAround(x, y, z, 64.0, world.dimension())
                    }
                }
                map.put("spawn_pokemon") { params ->
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
                map.put("spawn_npc") { params ->
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
                map.put("play_sound_on_server") { params ->
                    val sound = params.getString(0).asResource()
                    val soundSource = params.getString(1).uppercase()
                    val x = params.getDouble(2)
                    val y = params.getDouble(3)
                    val z = params.getDouble(4)
                    val player = params.getOrNull<MoValue>(5)?.let {
                        if (it is StringValue) world.getPlayerByUUID(UUID.fromString(it.value))
                        else if (it is ObjectValue<*>) it.obj
                        else null
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
                map.put("get_entities_around") { params ->
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
                map.put("is_healer_in_use") { params ->
                    val pos = params.get<ArrayStruct>(0).asBlockPos()
                    val healer = world.getBlockEntity(pos, CobblemonBlockEntities.HEALING_MACHINE).orElse(null)
                        ?: return@put DoubleValue.ONE
                    return@put DoubleValue(healer.isInUse)
                }

                return@mutableListOf map
            }
        )
}
