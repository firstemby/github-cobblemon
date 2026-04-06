/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.npc.configuration

import com.cobblemon.mod.common.api.npc.configuration.interaction.CustomScriptNPCInteractionConfiguration
import com.cobblemon.mod.common.api.npc.configuration.interaction.DialogueNPCInteractionConfiguration
import com.cobblemon.mod.common.api.npc.configuration.interaction.NoneNPCInteractionConfiguration
import com.cobblemon.mod.common.api.npc.configuration.interaction.ScriptNPCInteractionConfiguration
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.entity.npc.NPCEntity
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.codec.StreamCodec
import net.minecraft.server.level.ServerPlayer

/**
 * A type of interaction handler for when a player right-clicks the NPC.
 *
 * @author Hiroku
 * @since August 19th, 2023
 */
interface NPCInteractConfiguration {
    class NPCInteractConfigurationType<T : NPCInteractConfiguration>(
        val displayName: MutableComponent,
        val codec: MapCodec<T>
    )

    companion object {
        val types = mutableMapOf<String, NPCInteractConfigurationType<out NPCInteractConfiguration>>()

        fun <T: NPCInteractConfiguration>register(type: String, displayName: MutableComponent, codec: MapCodec<T>) {
            types[type] = NPCInteractConfigurationType(displayName, codec)
        }

        init {
            register(type = "script", displayName = "Script".text(), ScriptNPCInteractionConfiguration.CODEC)
            register(type = "custom_script", displayName = "Custom Script".text(), CustomScriptNPCInteractionConfiguration.CODEC)
            register(type = "dialogue", displayName = "Dialogue".text(), DialogueNPCInteractionConfiguration.CODEC)
            register(type = "none", displayName = "None".text(), MapCodec.unit(NoneNPCInteractionConfiguration()))
        }

        val CODEC: Codec<NPCInteractConfiguration> =
            Codec.STRING.dispatch(
                { it.type },
                { id ->
                    types[id]?.codec
                        ?: error("Unknown NPCInteractConfiguration type: $id")
                }
            )
    }

    val type: String
    fun interact(npc: NPCEntity, player: ServerPlayer): Boolean
    /** Don't add anything to this if you aren't registering the thing on the client as well. */
    fun streamCodec(): StreamCodec<RegistryFriendlyByteBuf, out NPCInteractConfiguration>
    /** Returns true if the given configuration is considered different to this one. If false, the new one will not replace the old one when editing. */
    fun isDifferentTo(other: NPCInteractConfiguration): Boolean
}
