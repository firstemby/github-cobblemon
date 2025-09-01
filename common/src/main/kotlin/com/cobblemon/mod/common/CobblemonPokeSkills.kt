/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common

import com.cobblemon.mod.common.CobblemonNetwork.sendPacket
import com.cobblemon.mod.common.api.data.JsonDataRegistry
import com.cobblemon.mod.common.api.molang.ExpressionLike
import com.cobblemon.mod.common.api.moves.animations.ActionEffectTimeline
import com.cobblemon.mod.common.api.reactive.SimpleObservable
import com.cobblemon.mod.common.entity.pokemon.PokeSkill
import com.cobblemon.mod.common.net.messages.client.data.PokeSkillsRegistrySyncPacket
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.adapters.ActionEffectTimelineAdapter
import com.cobblemon.mod.common.util.adapters.ExpressionLikeAdapter
import com.cobblemon.mod.common.util.adapters.IdentifierAdapter
import com.cobblemon.mod.common.util.adapters.TextAdapter
import com.cobblemon.mod.common.util.cobblemonResource
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.packs.PackType
import net.minecraft.world.entity.player.Player

/**
 * The JSON data registry for all the possible [PokeSkill]s.
 *
 * @author Hiroku
 * @since August 31st, 2025
 */
object CobblemonPokeSkills : JsonDataRegistry<PokeSkill> {
    override val id = cobblemonResource("poke_skills")
    override val type = PackType.SERVER_DATA
    override val observable = SimpleObservable<CobblemonPokeSkills>()
    override val typeToken = TypeToken.get(PokeSkill::class.java)
    override val resourcePath = "poke_skills"
    override val gson = GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(ExpressionLike::class.java, ExpressionLikeAdapter)
        .registerTypeAdapter(ActionEffectTimeline::class.java, ActionEffectTimelineAdapter)
        .registerTypeAdapter(ResourceLocation::class.java, IdentifierAdapter)
        .registerTypeAdapter(Component::class.java, TextAdapter)
        .create()

    var skills = mapOf<ResourceLocation, PokeSkill>()

    fun getAccessibleSkills(player: Player, pokemon: Pokemon): List<PokeSkill> {
        return skills.values.filter { it.canAccess(player, pokemon) }
    }

    override fun reload(data: Map<ResourceLocation, PokeSkill>) {
        data.forEach { (id, skill) -> skill.id = id }
        skills = data
        observable.emit(this)
        Cobblemon.LOGGER.info("Loaded ${skills.size} PokéSkills")
    }

    override fun sync(player: ServerPlayer) {
        player.sendPacket(PokeSkillsRegistrySyncPacket(skills.values.toList()))
    }
}