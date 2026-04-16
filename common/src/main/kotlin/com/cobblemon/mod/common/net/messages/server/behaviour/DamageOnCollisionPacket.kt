/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.server.behaviour

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.readVec3d
import com.cobblemon.mod.common.util.writeVec3d
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.world.phys.Vec3

class DamageOnCollisionPacket(
    val impactVec: Vec3
) : NetworkPacket<DamageOnCollisionPacket> {
    companion object {
        val ID = cobblemonResource("c2s_on_collision_damage")
        fun decode(buffer: RegistryFriendlyByteBuf): DamageOnCollisionPacket = DamageOnCollisionPacket(
            impactVec = buffer.readVec3d()
        )
    }

    override val id = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeVec3d(impactVec)
    }
}
