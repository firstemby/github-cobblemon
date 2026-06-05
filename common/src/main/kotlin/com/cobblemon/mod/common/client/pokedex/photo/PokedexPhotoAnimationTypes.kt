/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.pokedex.photo

import com.cobblemon.mod.common.api.data.JsonDataRegistry
import com.cobblemon.mod.common.api.reactive.SimpleObservable
import com.cobblemon.mod.common.util.cobblemonResource
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.packs.PackType
import java.util.Locale

object PokedexPhotoAnimationTypes : JsonDataRegistry<PokedexPhotoAnimationType> {
    override val id = cobblemonResource("pokedex_photo_animation_types")
    override val type = PackType.CLIENT_RESOURCES
    override val observable = SimpleObservable<PokedexPhotoAnimationTypes>()
    override val gson = GsonBuilder()
        .disableHtmlEscaping()
        .setPrettyPrinting()
        .create()
    override val typeToken: TypeToken<PokedexPhotoAnimationType> = TypeToken.get(PokedexPhotoAnimationType::class.java)
    override val resourcePath = "pokedex/photo_animation_types"

    private val types = linkedMapOf<ResourceLocation, PokedexPhotoAnimationType>()

    override fun sync(player: ServerPlayer) {}

    override fun reload(data: Map<ResourceLocation, PokedexPhotoAnimationType>) {
        types.clear()
        types.putAll(data)
        observable.emit(this)
    }

    fun bestMatchFor(animationNames: Collection<String>): PokedexPhotoAnimationType? {
        val normalizedNames = animationNames
            .map(::normalize)
            .filter { it.isNotBlank() }
            .toSet()
        if (normalizedNames.isEmpty()) return null

        return types.values
            .filter { type -> normalizedNames.any(type.match::matches) }
            .maxWithOrNull(compareBy<PokedexPhotoAnimationType> { it.priority }.thenBy { it.poseScore })
    }

    private fun normalize(value: String): String {
        return value.lowercase(Locale.ROOT).substringAfterLast('.')
    }
}

class PokedexPhotoAnimationType {
    var priority: Int = 0
    var poseScore: Int = 0
    var match: PokedexPhotoAnimationMatch = PokedexPhotoAnimationMatch()
}

class PokedexPhotoAnimationMatch {
    var exact: List<String> = emptyList()
    var contains: List<String> = emptyList()
    @SerializedName("contains_any")
    var containsAny: List<String> = emptyList()
    @SerializedName("not_contains")
    var notContains: List<String> = emptyList()

    fun matches(animationName: String): Boolean {
        val normalized = normalize(animationName)
        if (notContains.any { normalized.contains(normalize(it)) }) return false

        val hasExact = exact.any { normalized == normalize(it) }
        val hasContains = contains.isNotEmpty() && contains.all { normalized.contains(normalize(it)) }
        val hasContainsAny = containsAny.any { normalized.contains(normalize(it)) }

        return hasExact || hasContains || hasContainsAny
    }

    private fun normalize(value: String): String {
        return value.lowercase(Locale.ROOT).substringAfterLast('.')
    }
}
