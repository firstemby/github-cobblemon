/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.npc.configuration.interaction

import com.cobblemon.mod.common.api.npc.configuration.NPCInteractConfiguration
import com.cobblemon.mod.common.entity.npc.NPCEntity
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.ExtraCodecs

/**
 * An [NPCInteractConfiguration] which does nothing when the player interacts with the NPC.
 *
 * The purpose of having this exist is so that an entity can overwrite a class-driven interaction
 * to have no interaction, as null will instead refer to the NPC class's interaction.
 *
 * @author Hiroku
 * @since July 5th, 2024
 */
class NoneNPCInteractionConfiguration : NPCInteractConfiguration {
    override val type = "none"
    override fun interact(npc: NPCEntity, player: ServerPlayer) = false
    override fun isDifferentTo(other: NPCInteractConfiguration) = other !is NoneNPCInteractionConfiguration
}
