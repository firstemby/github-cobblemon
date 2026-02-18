/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block.entity

import com.cobblemon.mod.common.CobblemonBlockEntities
import com.cobblemon.mod.common.battles.stadium.StadiumBattleConfig
import com.cobblemon.mod.common.battles.stadium.StadiumBattleFormat
import com.cobblemon.mod.common.battles.stadium.StadiumBattleLayout
import com.cobblemon.mod.common.battles.stadium.StadiumBattleRegistry
import com.cobblemon.mod.common.battles.stadium.StadiumBattleSide
import com.cobblemon.mod.common.battles.stadium.StadiumBlockData
import com.cobblemon.mod.common.battles.stadium.StadiumPlacement
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3

class StadiumBlockEntity(
    blockPos: BlockPos,
    blockState: BlockState
) : BlockEntity(CobblemonBlockEntities.STADIUM_BLOCK, blockPos, blockState) {

    var data: StadiumBlockData = StadiumBlockData.createDefault()

    override fun loadAdditional(compoundTag: CompoundTag, registryLookup: HolderLookup.Provider) {
        super.loadAdditional(compoundTag, registryLookup)
        data = StadiumBlockData.fromNbt(compoundTag)
    }

    override fun saveAdditional(compoundTag: CompoundTag, registryLookup: HolderLookup.Provider) {
        super.saveAdditional(compoundTag, registryLookup)
        val dataTag = data.toNbt()
        for (key in dataTag.allKeys) {
            compoundTag.put(key, dataTag.get(key)!!)
        }
    }

    override fun getUpdatePacket(): Packet<ClientGamePacketListener>? = ClientboundBlockEntityDataPacket.create(this)

    override fun getUpdateTag(provider: HolderLookup.Provider): CompoundTag = super.saveWithFullMetadata(provider)

    fun markUpdated() {
        setChanged()
        level?.sendBlockUpdated(blockPos, blockState, blockState, 3)
    }

    fun buildStadiumBattleConfig(): StadiumBattleConfig? {
        if (!data.isConfigured) return null
        val p1 = data.player1Pos ?: return null
        val p2 = data.player2Pos ?: return null

        val origin = Vec3(blockPos.x.toDouble(), blockPos.y.toDouble(), blockPos.z.toDouble())
        val activeConfig = data.formatConfigs[data.activeFormat] ?: return null

        val format = StadiumBattleFormat.fromId(activeConfig.battleTypeName) ?: return null

        val side1Actors = listOf(StadiumPlacement(position = p1, lookAt = p2, yaw = null, pitch = null))
        val side2Actors = listOf(StadiumPlacement(position = p2, lookAt = p1, yaw = null, pitch = null))

        val side1Pokemon = activeConfig.side1PokemonPositions.map {
            StadiumPlacement(position = it, lookAt = null, yaw = null, pitch = null)
        }.ifEmpty {
            listOf(StadiumPlacement(position = p1.add(1.0, 0.0, 0.0), lookAt = null, yaw = null, pitch = null))
        }

        val side2Pokemon = activeConfig.side2PokemonPositions.map {
            StadiumPlacement(position = it, lookAt = null, yaw = null, pitch = null)
        }.ifEmpty {
            listOf(StadiumPlacement(position = p2.add(-1.0, 0.0, 0.0), lookAt = null, yaw = null, pitch = null))
        }

        val layout = StadiumBattleLayout(
            side1 = StadiumBattleSide(actors = side1Actors, pokemon = side1Pokemon),
            side2 = StadiumBattleSide(actors = side2Actors, pokemon = side2Pokemon)
        )

        return StadiumBattleConfig(
            id = data.stadiumId.toString(),
            dimension = level?.dimension()?.location(),
            origin = origin,
            cameraTimeline = data.cameraTimeline,
            cameraTimelineSideB = null,
            cameraOriginOffset = data.cameraOriginOffset,
            singles = if (format == StadiumBattleFormat.SINGLES) layout else null,
            doubles = if (format == StadiumBattleFormat.DOUBLES) layout else null,
            introMusic = data.introMusic,
            introMusicVolume = data.introMusicVolume,
            introMusicPitch = data.introMusicPitch,
            music = data.loopMusic,
            musicVolume = data.loopMusicVolume,
            musicPitch = data.loopMusicPitch
        )
    }

    fun registerDynamic() {
        val config = buildStadiumBattleConfig() ?: return
        StadiumBattleRegistry.registerDynamic(data.stadiumId, config)
    }

    fun unregisterDynamic() {
        StadiumBattleRegistry.unregisterDynamic(data.stadiumId)
    }
}
