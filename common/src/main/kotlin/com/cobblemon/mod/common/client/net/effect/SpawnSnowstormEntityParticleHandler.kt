/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.net.effect

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.cobblemon.mod.common.client.particle.BedrockParticleOptionsRepository
import com.cobblemon.mod.common.client.particle.ParticleStorm
import com.cobblemon.mod.common.net.messages.client.effect.SpawnSnowstormEntityParticlePacket
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.LivingEntity

object SpawnSnowstormEntityParticleHandler : ClientNetworkPacketHandler<SpawnSnowstormEntityParticlePacket> {
    override fun handle(packet: SpawnSnowstormEntityParticlePacket, client: Minecraft) {
        val world = Minecraft.getInstance().level ?: return
        val effect = BedrockParticleOptionsRepository.getEffect(packet.effectId) ?: return
        val entity = world.getEntity(packet.entityId) as? LivingEntity ?: return
        ParticleStorm.createAtEntity(world, effect, entity, packet.locator).forEach(ParticleStorm::spawn)
    }
}