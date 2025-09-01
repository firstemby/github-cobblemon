/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.entity.pokemon

import com.bedrockk.molang.runtime.MoLangRuntime
import com.bedrockk.molang.runtime.value.StringValue
import com.cobblemon.mod.common.api.molang.ExpressionLike
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMostSpecificMoLangValue
import com.cobblemon.mod.common.api.molang.MoLangFunctions.setup
import com.cobblemon.mod.common.api.moves.animations.ActionEffectContext
import com.cobblemon.mod.common.api.moves.animations.ActionEffectTimeline
import com.cobblemon.mod.common.api.pokemon.status.Statuses
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.asExpressionLike
import com.cobblemon.mod.common.util.codec.EXPRESSION_LIKE_CODEC
import com.cobblemon.mod.common.util.isInBattle
import com.cobblemon.mod.common.util.resolveBoolean
import com.cobblemon.mod.common.util.withQueryValue
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player

/**
 * The reference object for a skill that a Pokémon can use in the overworld.
 *
 * @author Hiroku
 * @since August 31st, 2025
 */
class PokeSkill(
    val name: String,
    val actionEffect: ActionEffectTimeline,
    val icon: ResourceLocation,
    val cooldownTicks: Int,
    val accessCondition: ExpressionLike = "true".asExpressionLike(),
    val useCondition: ExpressionLike = "true".asExpressionLike()
) {
    companion object {
        val runtime = MoLangRuntime().setup()
        val CODEC = RecordCodecBuilder.create<PokeSkill> { instance ->
            instance.group(
                Codec.STRING.fieldOf("name").forGetter { it.name },
                Codec.unit<ActionEffectTimeline> { ActionEffectTimeline.NONE }.fieldOf("actionEffect").forGetter { it.actionEffect },
                ResourceLocation.CODEC.fieldOf("icon").forGetter { it.icon },
                Codec.INT.fieldOf("cooldownTicks").forGetter { it.cooldownTicks },
                EXPRESSION_LIKE_CODEC.fieldOf("accessCondition").forGetter { it.accessCondition },
                EXPRESSION_LIKE_CODEC.fieldOf("useCondition").forGetter { it.useCondition }
            ).apply(instance, ::PokeSkill)
        }
        val PACKET_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC)
    }

    @Transient
    lateinit var id: ResourceLocation

    fun canAccess(player: Player, pokemon: Pokemon): Boolean {
        runtime.withQueryValue("player", player.asMostSpecificMoLangValue())
        runtime.withQueryValue("pokemon", pokemon.struct)
        val hasAccess = runtime.resolveBoolean(accessCondition)
        return hasAccess
    }

    fun canUse(player: Player, pokemon: Pokemon): Boolean {
        val status = pokemon.status?.status
        if (id in pokemon.skillCooldownsTicks || player.isInBattle() || status == Statuses.SLEEP || status == Statuses.FROZEN) {
            return false
        }
        runtime.withQueryValue("player", player.asMostSpecificMoLangValue())
        runtime.withQueryValue("pokemon", pokemon.struct)
        val canUse = runtime.resolveBoolean(useCondition)
        return canUse
    }

    fun use(
        player: ServerPlayer,
        pokemon: Pokemon,
        // context information!!!
    ): Boolean {
        if (!canUse(player, pokemon)) return false
        val runtime = MoLangRuntime().setup()
        runtime.withQueryValue("player", player.asMostSpecificMoLangValue())
        runtime.withQueryValue("pokemon", pokemon.struct)
        runtime.withQueryValue("skill_id", StringValue(id.toString()))
        // put the context in the coconut
        // blablabalbalbal doing that now

        // build the context
        val actionEffectContext = ActionEffectContext(
            actionEffect = actionEffect,
            runtime = runtime
        )

        // run the thing. Store the context on the pokemon so we know we have one running? prevent it from doing another? idk man
        val future = actionEffect.run(actionEffectContext)

        return true
    }
}