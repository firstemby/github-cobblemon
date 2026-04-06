/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.npc.configuration.interaction

import com.bedrockk.molang.runtime.MoLangRuntime
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMoLangValue
import com.cobblemon.mod.common.api.molang.MoLangFunctions.setup
import com.cobblemon.mod.common.api.npc.configuration.NPCInteractConfiguration
import com.cobblemon.mod.common.api.scripting.CobblemonScripts
import com.cobblemon.mod.common.entity.npc.NPCEntity
import com.cobblemon.mod.common.util.DataKeys
import com.cobblemon.mod.common.util.withQueryValue
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer

/**
 * An [NPCInteractConfiguration] which runs a referenced MoLang script from [CobblemonScripts].
 *
 * @author Hiroku
 * @since July 5th, 2024
 */
class ScriptNPCInteractionConfiguration : NPCInteractConfiguration {
    companion object {
        val runtime = MoLangRuntime().setup()

        val CODEC: MapCodec<ScriptNPCInteractionConfiguration> = RecordCodecBuilder.mapCodec { instance ->
            instance.group(
                Identifier.CODEC.fieldOf(DataKeys.NPC_INTERACT_SCRIPT).forGetter{it.script}
            ).apply(instance) { script ->
                ScriptNPCInteractionConfiguration().also { it.script = script }
            }
        }

        val STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC.codec())
    }

    override val type: String = "script"
    var script: Identifier = Identifier.fromNamespaceAndPath("cobblemon", "scripts/test.molang")

    override fun interact(npc: NPCEntity, player: ServerPlayer): Boolean {
        val script = CobblemonScripts.scripts[script] ?: return false
        runtime.withQueryValue("npc", npc.struct)
        runtime.withQueryValue("player", player.asMoLangValue())
        // Context being here is just backwards compatibility
        val context = mapOf(
            "npc" to npc.struct,
            "player" to player.asMoLangValue()
        )
        script.resolve(runtime, context)
        return true
    }

    override fun streamCodec(): StreamCodec<RegistryFriendlyByteBuf, out NPCInteractConfiguration> = STREAM_CODEC

    override fun isDifferentTo(other: NPCInteractConfiguration) = other !is ScriptNPCInteractionConfiguration || other.script != script
}
