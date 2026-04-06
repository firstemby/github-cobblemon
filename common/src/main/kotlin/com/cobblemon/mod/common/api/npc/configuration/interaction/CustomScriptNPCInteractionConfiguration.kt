/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.npc.configuration.interaction

import com.cobblemon.mod.common.api.molang.ExpressionLike
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMoLangValue
import com.cobblemon.mod.common.api.npc.configuration.NPCInteractConfiguration
import com.cobblemon.mod.common.entity.npc.NPCEntity
import com.cobblemon.mod.common.util.DataKeys
import com.cobblemon.mod.common.util.asExpressionLike
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.server.level.ServerPlayer

/**
 * An [NPCInteractConfiguration] which has a MoLang script defined in the configuration directly
 * rather than a reference to a separately made script.
 *
 * @author Hiroku
 * @since July 5th, 2024
 */
class CustomScriptNPCInteractionConfiguration : NPCInteractConfiguration {

    companion object {
        val CODEC: MapCodec<CustomScriptNPCInteractionConfiguration> = RecordCodecBuilder.mapCodec { instance ->
            instance.group(
                ExpressionLike.CODEC.fieldOf(DataKeys.NPC_INTERACT_CUSTOM_SCRIPT).forGetter{it.script}
            ).apply(instance) { script ->
                CustomScriptNPCInteractionConfiguration().also { it.script = script }
            }
        }

        val STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC.codec())
    }

    override val type: String = "custom_script"
    var script: ExpressionLike = "1".asExpressionLike()

    override fun interact(npc: NPCEntity, player: ServerPlayer): Boolean {
        val context = mapOf(
            "npc" to npc.struct,
            "player" to player.asMoLangValue()
        )
        script.resolve(npc.runtime, context)
        return true
    }

    override fun streamCodec(): StreamCodec<RegistryFriendlyByteBuf, out NPCInteractConfiguration> = STREAM_CODEC

    override fun isDifferentTo(other: NPCInteractConfiguration): Boolean {
        return other !is CustomScriptNPCInteractionConfiguration || other.script.getString() != script.getString()
    }
}
