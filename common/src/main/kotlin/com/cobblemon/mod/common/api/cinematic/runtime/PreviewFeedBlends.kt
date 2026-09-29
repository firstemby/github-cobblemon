/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.runtime

import com.cobblemon.mod.common.api.camera.CameraRotation
import com.cobblemon.mod.common.api.cinematic.model.Beat
import com.cobblemon.mod.common.api.cinematic.model.CutEntry
import com.cobblemon.mod.common.api.cinematic.model.CutTransition
import com.cobblemon.mod.common.net.messages.client.cinematic.FeedTransform
import com.cobblemon.mod.common.net.messages.client.cinematic.FeedTransformPacket
import net.minecraft.util.Mth

class PreviewFeedBlends {
    private val cuts = mutableMapOf<String, CutEntry>()
    private val outgoing = mutableMapOf<String, FeedTransform>()
    private val previous = mutableMapOf<String, FeedTransform>()

    fun reset() { cuts.clear(); outgoing.clear(); previous.clear() }

    fun apply(packet: FeedTransformPacket, beat: Beat, time: Float): FeedTransformPacket {
        val feeds = packet.feeds.map { target ->
            val entries = beat.cuts[target.feedId]?.entries.orEmpty()
            val cut = entries.lastOrNull { it.time <= time } ?: entries.firstOrNull()
            if (cut != cuts[target.feedId] && cut != null) {
                previous[target.feedId]?.let { outgoing[target.feedId] = it }
                cuts[target.feedId] = cut
            }
            val from = outgoing[target.feedId]
            val transition = cut?.transition as? CutTransition.Blend
            val alpha = if (transition != null) ((time - cut.time) / transition.duration).coerceIn(0f, 1f) else 1f
            val result = if (from != null && alpha < 1f) {
                target.copy(
                    position = from.position.lerp(target.position, alpha.toDouble()),
                    rotation = if (from.rotation != null && target.rotation != null) CameraRotation(
                        from.rotation.yaw + Mth.wrapDegrees(target.rotation.yaw - from.rotation.yaw) * alpha,
                        from.rotation.pitch + (target.rotation.pitch - from.rotation.pitch) * alpha
                    ) else target.rotation,
                    fov = blendOptional(from.fov, target.fov, alpha),
                    roll = if (from.roll != null && target.roll != null) {
                        from.roll + Mth.wrapDegrees(target.roll - from.roll) * alpha
                    } else target.roll,
                    boneHint = null
                )
            } else target
            previous[target.feedId] = result
            result
        }
        return FeedTransformPacket(packet.sceneId, feeds)
    }

    private fun blendOptional(from: Float?, to: Float?, alpha: Float): Float? =
        if (from != null && to != null) from + (to - from) * alpha else to
}
