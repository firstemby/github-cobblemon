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
import com.cobblemon.mod.common.battles.stadium.StadiumBattleLayout
import com.cobblemon.mod.common.battles.stadium.StadiumBattleRegistry
import com.cobblemon.mod.common.battles.stadium.StadiumBattleSide
import com.cobblemon.mod.common.battles.stadium.StadiumBlockData
import com.cobblemon.mod.common.battles.stadium.StadiumBlockLayout
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

        val origin = Vec3(blockPos.x.toDouble(), blockPos.y.toDouble(), blockPos.z.toDouble())

        val singlesLayout = data.singlesLayout?.toBattleLayout()
        val doublesLayout = data.doublesLayout?.toBattleLayout()

        if (singlesLayout == null && doublesLayout == null) return null

        return StadiumBattleConfig(
            id = data.stadiumId.toString(),
            dimension = level?.dimension()?.location(),
            origin = origin,
            cameraTimeline = data.cameraTimeline,
            cameraTimelineSideB = data.cameraTimelineSideB,
            cameraOriginOffset = data.cameraOriginOffset,
            singles = singlesLayout,
            doubles = doublesLayout,
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

    private fun StadiumBlockLayout.toBattleLayout(): StadiumBattleLayout? {
        if (side1.actors.isEmpty() && side2.actors.isEmpty()) return null
        return StadiumBattleLayout(
            side1 = StadiumBattleSide(
                actors = side1.actors.map { StadiumPlacement(it.position, it.lookAt, it.yaw, it.pitch) },
                pokemon = side1.pokemon.map { StadiumPlacement(it.position, it.lookAt, it.yaw, it.pitch) }
            ),
            side2 = StadiumBattleSide(
                actors = side2.actors.map { StadiumPlacement(it.position, it.lookAt, it.yaw, it.pitch) },
                pokemon = side2.pokemon.map { StadiumPlacement(it.position, it.lookAt, it.yaw, it.pitch) }
            )
        )
    }
}
