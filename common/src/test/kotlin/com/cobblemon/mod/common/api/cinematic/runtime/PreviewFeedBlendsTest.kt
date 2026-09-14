/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.runtime

import com.cobblemon.mod.common.api.camera.CameraRotation
import com.cobblemon.mod.common.api.cinematic.model.*
import com.cobblemon.mod.common.net.messages.client.cinematic.FeedTransform
import com.cobblemon.mod.common.net.messages.client.cinematic.FeedTransformPacket
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class PreviewFeedBlendsTest {
    @Test
    fun `cut blend samples and replays on beat time with shortest yaw and roll arcs`() {
        val beat = Beat("main", duration = 8f, cuts = mapOf("primary" to CutTrack(listOf(
            CutEntry(0f, "wide"), CutEntry(4f, "close", CutTransition.Blend(2f))))))
        val clock = PreviewPlayback(beat)
        val blends = PreviewFeedBlends()
        val id = UUID.randomUUID()
        var result: FeedTransform? = null
        fun seek(time: Float): FeedTransform {
            clock.seek(time, true)
            clock.evaluate(blends::reset) { t ->
                val target = if (t < 4f) FeedTransform("primary", Vec3.ZERO, CameraRotation(170f, 0f), 60f, 170f)
                else FeedTransform("primary", Vec3(10.0, 0.0, 0.0), CameraRotation(-170f, 0f), 80f, -170f)
                result = blends.apply(FeedTransformPacket(id, listOf(target)), beat, t).feeds.single()
            }
            return result!!
        }
        val middle = seek(5f)
        assertEquals(5.0, middle.position.x, 0.0001)
        assertEquals(180f, middle.rotation!!.yaw, 0.0001f)
        assertEquals(70f, middle.fov!!, 0.0001f)
        assertEquals(180f, middle.roll!!, 0.0001f)
        seek(7f)
        assertEquals(middle, seek(5f))
        assertEquals(0.0, seek(4f).position.x, 0.0001)
    }
}
