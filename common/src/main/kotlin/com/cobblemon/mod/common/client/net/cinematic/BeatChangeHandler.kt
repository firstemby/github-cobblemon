/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.net.cinematic

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.cobblemon.mod.common.client.cinematic.ClientSceneSessionManager
import com.cobblemon.mod.common.net.messages.client.cinematic.BeatChangePacket
import net.minecraft.client.Minecraft

object BeatChangeHandler : ClientNetworkPacketHandler<BeatChangePacket> {
    override fun handle(packet: BeatChangePacket, client: Minecraft) {
        ClientSceneSessionManager.onBeatChange(
            sceneId = packet.sceneId,
            beatId = packet.beatId,
            instantTransition = packet.instantTransition,
            renderPlayerBody = packet.renderPlayerBody,
            hiddenHudElements = packet.hiddenHudElements,
            activeFeedIds = packet.activeFeedIds
        )
    }
}
