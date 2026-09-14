package com.cobblemon.mod.common.net.serverhandling.stadium

import com.cobblemon.mod.common.CobblemonNetwork.sendPacket
import com.cobblemon.mod.common.api.cinematic.registry.SceneRegistry
import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.battles.stadium.StadiumCinematic
import com.cobblemon.mod.common.battles.stadium.StadiumConfiguration
import com.cobblemon.mod.common.block.entity.StadiumBlockEntity
import com.cobblemon.mod.common.net.messages.client.stadium.StadiumCinematicPacket
import com.cobblemon.mod.common.net.messages.server.stadium.EditStadiumCinematicPacket
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.lang
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import java.security.MessageDigest

object EditStadiumCinematicHandler : ServerNetworkPacketHandler<EditStadiumCinematicPacket> {
    override fun handle(packet: EditStadiumCinematicPacket, server: MinecraftServer, player: ServerPlayer) {
        fun fail(message: Component) = player.sendPacket(StadiumCinematicPacket(packet.requestId, false, message))
        if (!player.hasPermissions(2)) { fail(lang("stadium.cinematic.operator_required")); return }
        val level = player.serverLevel()
        if (!level.hasChunk(packet.blockPos.x shr 4, packet.blockPos.z shr 4) ||
            !StadiumConfiguration.validPosition(player.position(), Vec3.atLowerCornerOf(packet.blockPos))) {
            fail(lang("stadium.cinematic.out_of_range", StadiumConfiguration.MAX_DISTANCE.toInt())); return
        }
        val block = level.getBlockEntity(packet.blockPos) as? StadiumBlockEntity
        if (block == null || block.data.stadiumId != packet.stadiumId) { fail(lang("stadium.cinematic.block_changed")); return }
        val data = block.data
        fun revision() = MessageDigest.getInstance("SHA-256").digest(
            "${data.cinematicScene}\n${data.cinematicSceneJson.orEmpty()}".toByteArray()).joinToString("") { "%02x".format(it) }
        if (packet.source.isEmpty()) {
            val source = data.cinematicSceneJson ?: data.cinematicScene?.let { SceneRegistry.getSource(it.toString()) }
            if (source == null || source.length > StadiumCinematic.MAX_LENGTH) { fail(lang("stadium.cinematic.select_template")); return }
            val origin = Vec3.atLowerCornerOf(packet.blockPos).add(data.cameraOriginOffset)
            val prepared = runCatching { StadiumCinematic.forEditor(source, data, origin) }.getOrElse {
                fail(lang("stadium.cinematic.invalid")); return
            }
            if (prepared.length > StadiumCinematic.MAX_LENGTH) { fail(lang("stadium.cinematic.too_large")); return }
            player.sendPacket(StadiumCinematicPacket(packet.requestId, true, Component.empty(), revision(), prepared, origin))
            return
        }
        if (packet.revision != revision()) { fail(lang("stadium.cinematic.revision_changed")); return }
        val id = cobblemonResource("stadium/${data.stadiumId}")
        val source = runCatching { StadiumCinematic.normalize(packet.source, id) }.getOrElse {
            fail((it as? StadiumCinematic.ValidationException)?.reason ?: lang("stadium.cinematic.invalid")); return
        }
        data.cinematicScene = id
        data.cinematicSceneJson = source
        block.markUpdated()
        block.registerDynamic()
        player.sendPacket(StadiumCinematicPacket(packet.requestId, true, lang("stadium.cinematic.applied", data.displayName), revision()))
    }
}
