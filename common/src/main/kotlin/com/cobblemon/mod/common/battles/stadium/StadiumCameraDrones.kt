/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.stadium

import com.cobblemon.mod.common.api.cinematic.runtime.SceneInstance
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonEntities
import com.cobblemon.mod.common.entity.stadium.StadiumCameraDroneEntity
import com.cobblemon.mod.common.net.messages.client.cinematic.FeedTransformPacket
import net.minecraft.server.level.ServerLevel

internal class StadiumCameraDrones(
    private val level: ServerLevel,
    private val createDrone: () -> StadiumCameraDroneEntity = { StadiumCameraDroneEntity(CobblemonEntities.STADIUM_CAMERA_DRONE, level) }
) {
    private val drones = mutableMapOf<String, StadiumCameraDroneEntity>()
    private var closed = false

    fun update(packet: FeedTransformPacket) {
        if (closed) return
        val activeFeeds = packet.feeds.map { it.feedId }.toSet()
        drones.keys.filter { it !in activeFeeds }.forEach { drones.remove(it)?.let(::discard) }
        for (feed in packet.feeds) {
            if (closed) return
            val existing = drones[feed.feedId]?.takeUnless { it.isRemoved }
            val drone = existing ?: createDrone()
            drone.moveTo(feed.position.x, feed.position.y, feed.position.z,
                feed.rotation?.yaw ?: drone.yRot, feed.rotation?.pitch ?: drone.xRot)
            if (existing == null && !closed && level.addFreshEntity(drone)) {
                if (closed) discard(drone) else drones[feed.feedId] = drone
            }
        }
    }

    fun close() {
        if (closed) return
        closed = true
        val owned = drones.values.toList()
        drones.clear()
        owned.forEach(::discard)
    }

    private fun discard(drone: StadiumCameraDroneEntity) {
        try {
            drone.discard()
        } catch (error: Exception) {
            Cobblemon.LOGGER.warn("Failed to remove a stadium camera drone", error)
        }
    }

    companion object {
        fun attach(scene: SceneInstance, level: ServerLevel) {
            val drones = StadiumCameraDrones(level)
            scene.onFeedTransforms(drones::update)
            scene.onStop(drones::close)
        }
    }
}
