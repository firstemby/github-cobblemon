/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.tms.obtain

import com.cobblemon.mod.common.api.tms.ObtainMethod
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.server.level.ServerPlayer
import net.minecraft.resources.ResourceLocation

/**
 * An [ObtainMethod] that triggers when the player has an advancement.
 */
class PlayerHasAdvancementObtainMethod(val advancement: ResourceLocation? = null) : ObtainMethod {

    // todo we can leave this here in case we want to use it later.... or addons?
    companion object {
        val ID = cobblemonResource("advancement")

        fun readFromBuffer(buffer: RegistryFriendlyByteBuf): PlayerHasAdvancementObtainMethod {
            val hasAdvancement = buffer.readBoolean()
            val advancement = if (hasAdvancement) buffer.readResourceLocation() else null
            return PlayerHasAdvancementObtainMethod(advancement)
        }
    }

    override val passive = true

    override fun matches(player: ServerPlayer): Boolean {
        if (advancement == null) return false
        val advancementInstance = player.server.getAdvancements().get(advancement) ?: return false
        val progress = player.advancements.getOrStartProgress(advancementInstance)
        return progress != null && progress.isDone
    }

    override fun writeToBuffer(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUtf("cobblemon:advancement")
        buffer.writeResourceLocation(advancement ?: ResourceLocation.tryParse("minecraft:empty"))
    }
}

