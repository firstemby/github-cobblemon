/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.serverhandling.stadium

import com.cobblemon.mod.common.util.lang
import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.CobblemonNetwork.sendPacket
import com.cobblemon.mod.common.net.messages.client.stadium.StadiumConfigResultPacket
import com.cobblemon.mod.common.battles.stadium.StadiumBlockData
import com.cobblemon.mod.common.battles.stadium.StadiumConfiguration
import com.cobblemon.mod.common.battles.stadium.StadiumCinematic
import com.cobblemon.mod.common.command.StadiumBattleCommand
import com.cobblemon.mod.common.api.camera.result.ValidationError
import com.cobblemon.mod.common.block.entity.StadiumBlockEntity
import com.cobblemon.mod.common.net.messages.server.stadium.UpdateStadiumBlockPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.Vec3
import net.minecraft.world.entity.LivingEntity
import java.util.UUID

object UpdateStadiumBlockHandler : ServerNetworkPacketHandler<UpdateStadiumBlockPacket> {
    override fun handle(packet: UpdateStadiumBlockPacket, server: MinecraftServer, player: ServerPlayer) {
        fun fail(message: Component) { player.sendPacket(StadiumConfigResultPacket(packet.blockPos, packet.requestId, false, message)) }
        if (!player.level().hasChunk(packet.blockPos.x shr 4, packet.blockPos.z shr 4)) { fail(lang("stadium.save.unloaded")); return }
        val blockEntity = player.level().getBlockEntity(packet.blockPos) as? StadiumBlockEntity
            ?: run { fail(lang("stadium.save.missing")); return }

        if (!StadiumConfiguration.validPosition(player.position(), Vec3.atLowerCornerOf(packet.blockPos))) { fail(lang("stadium.save.distance")); return }

        val isOwner = blockEntity.data.ownerUUID == player.uuid
        val isOp = player.hasPermissions(2)
        if (!isOwner && !isOp) {
            fail(lang("stadium.save.permission"))
            return
        }

        val newData = runCatching { StadiumBlockData.fromNbt(packet.data) }.getOrElse {
            fail(lang("stadium.save.malformed"))
            return
        }
        newData.cinematicSceneJson = blockEntity.data.cinematicSceneJson.takeIf { newData.cinematicScene == blockEntity.data.cinematicScene }
        if (newData.arena == null && (blockEntity.data.arena != null || StadiumConfiguration.layoutIds.any {
                val before = blockEntity.data.layout(it)
                val after = newData.layout(it)
                listOf(before?.side1?.actors.orEmpty(), before?.side1?.pokemon.orEmpty(), before?.side2?.actors.orEmpty(), before?.side2?.pokemon.orEmpty()) !=
                    listOf(after?.side1?.actors.orEmpty(), after?.side1?.pokemon.orEmpty(), after?.side2?.actors.orEmpty(), after?.side2?.pokemon.orEmpty())
            })) {
            fail(lang("stadium.save.arena_required"))
            return
        }
        val errors = StadiumConfiguration.errors(newData, Vec3.atLowerCornerOf(packet.blockPos)) +
            if (newData.isConfigured) StadiumConfiguration.readiness(newData) else emptyList()
        if (errors.isNotEmpty()) {
            fail(errors.first())
            return
        }
        if (newData.isConfigured && newData.cinematicScene != null) {
            val scene = StadiumCinematic.resolve(newData.cinematicScene, newData.cinematicSceneJson)
            if (scene == null || scene.validate().any { it.severity == ValidationError.Severity.ERROR } ||
                scene.beats.values.sumOf { beat -> beat.cues.count { it.id == "battle_launch" } } != 1) {
                fail(lang("stadium.save.cinematic"))
                return
            }
        }
        newData.copy(
            stadiumId = blockEntity.data.stadiumId,
            ownerUUID = blockEntity.data.ownerUUID
        ).let { blockEntity.data = it }

        blockEntity.markUpdated()

        if (blockEntity.data.isConfigured) {
            blockEntity.registerDynamic()
        } else {
            blockEntity.unregisterDynamic()
        }
        player.sendSystemMessage(lang("stadium.save.success", blockEntity.data.displayName))
        player.sendPacket(StadiumConfigResultPacket(packet.blockPos, packet.requestId, true, lang("stadium.save.success", blockEntity.data.displayName)))
        if (packet.action == UpdateStadiumBlockPacket.Action.WAND) {
            packet.wand?.takeIf { it.blockPos == packet.blockPos }?.let { RequestStadiumPlacementWandHandler.handle(it, server, player) }
        }
        if (packet.action == UpdateStadiumBlockPacket.Action.START) {
            val config = blockEntity.buildStadiumBattleConfig()
            val participants = listOf(packet.participantA, packet.participantB).map { name ->
                val uuid = runCatching { UUID.fromString(name) }.getOrNull()
                (if (uuid != null) player.serverLevel().getEntity(uuid) else server.playerList.getPlayerByName(name)) as? LivingEntity
            }
            if (config == null || participants.any { it == null || it.level() !== player.level() || it.distanceToSqr(player) > 128 * 128 }) {
                player.sendSystemMessage(lang("stadium.save.participants"))
                return
            }
            StadiumBattleCommand.start(player.createCommandSourceStack(), config, "active", participants[0]!!, participants[1]!!)
        }
    }
}
