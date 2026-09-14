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
import com.cobblemon.mod.common.battles.stadium.StadiumBattleRegistry
import com.cobblemon.mod.common.battles.stadium.StadiumBlockData
import com.cobblemon.mod.common.battles.stadium.StadiumConfiguration
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import java.util.UUID

class StadiumBlockEntity(
    blockPos: BlockPos,
    blockState: BlockState
) : BlockEntity(CobblemonBlockEntities.STADIUM_BLOCK, blockPos, blockState) {

    var data: StadiumBlockData = StadiumBlockData.createDefault()
    private var registered: Pair<UUID, StadiumBattleConfig>? = null

    override fun setLevel(level: Level) {
        super.setLevel(level)
        if (!level.isClientSide) registerDynamic()
    }

    override fun clearRemoved() {
        super.clearRemoved()
        if (level?.isClientSide == false) registerDynamic()
    }

    override fun setRemoved() {
        if (level?.isClientSide == false) unregisterDynamic()
        super.setRemoved()
    }

    override fun loadAdditional(compoundTag: CompoundTag, registryLookup: HolderLookup.Provider) {
        super.loadAdditional(compoundTag, registryLookup)
        data = StadiumBlockData.fromNbt(compoundTag)
        if (level?.isClientSide == false) registerDynamic()
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
        if (!data.isConfigured || StadiumConfiguration.readiness(data).isNotEmpty()) return null

        val origin = Vec3(blockPos.x.toDouble(), blockPos.y.toDouble(), blockPos.z.toDouble())
        if (StadiumConfiguration.errors(data, origin).isNotEmpty()) return null

        val singlesLayout = data.singlesLayout?.let { StadiumConfiguration.toBattleLayout(it, origin) }
        val doublesLayout = data.doublesLayout?.let { StadiumConfiguration.toBattleLayout(it, origin) }

        if (singlesLayout == null && doublesLayout == null) return null

        return StadiumBattleConfig(
            id = data.stadiumId.toString(),
            dimension = level?.dimension()?.location(),
            origin = origin,
            cameraOriginOffset = data.cameraOriginOffset,
            singles = singlesLayout,
            doubles = doublesLayout,
            cinematicScene = data.cinematicScene,
            cinematicSceneJson = data.cinematicSceneJson,
            formats = data.formatConfigs.mapValues { StadiumConfiguration.copy(it.value) },
            activeFormat = data.activeFormat,
            arena = data.arena,
            spectatorAreas = data.spectatorAreas.toList(),
            displayName = data.displayName
        )
    }

    fun registerDynamic() {
        unregisterDynamic()
        if (StadiumBattleRegistry.hasDynamic(data.stadiumId)) {
            data = data.copy(stadiumId = UUID.randomUUID())
            setChanged()
        }
        val config = buildStadiumBattleConfig() ?: return
        StadiumBattleRegistry.registerDynamic(data.stadiumId, config)
        registered = data.stadiumId to config
    }

    fun unregisterDynamic() {
        val previous = registered ?: return
        registered = null
        StadiumBattleRegistry.unregisterDynamic(previous.first, previous.second)
    }

}
