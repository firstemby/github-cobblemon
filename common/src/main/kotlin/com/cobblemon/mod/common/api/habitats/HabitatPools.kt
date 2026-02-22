/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.habitats

import com.cobblemon.mod.common.api.data.JsonDataRegistry
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.reactive.SimpleObservable
import com.cobblemon.mod.common.api.spawning.IntRanges
import com.cobblemon.mod.common.api.spawning.SpawnBucket
import com.cobblemon.mod.common.api.spawning.TimeRange
import com.cobblemon.mod.common.api.spawning.position.SpawnablePositionType
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemon.mod.common.util.adapters.HabitatPoolAdapter
import com.cobblemon.mod.common.util.adapters.IdentifierAdapter
import com.cobblemon.mod.common.util.adapters.IntRangeAdapter
import com.cobblemon.mod.common.util.adapters.IntRangesAdapter
import com.cobblemon.mod.common.util.adapters.RegisteredSpawnablePositionAdapter
import com.cobblemon.mod.common.util.adapters.SpawnBucketAdapter
import com.cobblemon.mod.common.util.adapters.SpeciesAdapter
import com.cobblemon.mod.common.util.adapters.pokemonPropertiesShortAdapter
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.fromJson
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.File
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.packs.PackType
import net.minecraft.world.level.storage.LevelResource

object HabitatPools : JsonDataRegistry<HabitatPool<*>> {
    override val id = cobblemonResource("habitat_pools")
    override val type = PackType.SERVER_DATA
    override val observable = SimpleObservable<HabitatPools>()
    override val typeToken: TypeToken<HabitatPool<*>> = TypeToken.get(HabitatPool::class.java)
    override val resourcePath = "habitat_pools"

    override val gson: Gson = GsonBuilder()
        .registerTypeAdapter(ResourceLocation::class.java, IdentifierAdapter)
        .registerTypeAdapter(HabitatPool::class.java, HabitatPoolAdapter)
        .registerTypeAdapter(SpawnablePositionType::class.java, RegisteredSpawnablePositionAdapter)
        .registerTypeAdapter(SpawnBucket::class.java, SpawnBucketAdapter)
        .registerTypeAdapter(Species::class.java, SpeciesAdapter)
        .registerTypeAdapter(IntRange::class.java, IntRangeAdapter)
        .registerTypeAdapter(TimeRange::class.java, TimeRange.adapter)
        .registerTypeAdapter(IntRanges::class.java, IntRangesAdapter.basic)
        .registerTypeAdapter(PokemonProperties::class.java, pokemonPropertiesShortAdapter)
        .setPrettyPrinting()
        .create()

    private val datapacked = mutableMapOf<ResourceLocation, HabitatPool<*>>()
    private val worldSaved = mutableMapOf<ResourceLocation, HabitatPool<*>>()

    val habitatPoolsById = mutableMapOf<ResourceLocation, HabitatPool<*>>()

    private lateinit var worldSavedPoolsFolder: File

    override fun sync(player: ServerPlayer) {}

    fun onServerLoading(server: MinecraftServer) {
        worldSavedPoolsFolder = server.getWorldPath(LevelResource.ROOT).resolve("habitat_pools").toFile()
        if (!worldSavedPoolsFolder.exists()) {
            return
        }

        worldSavedPoolsFolder.listFiles { it.endsWith(".json") }.forEach {
            val identifier = ResourceLocation.parse(it.nameWithoutExtension)
            val habitat = gson.fromJson<HabitatPool<*>>(it.reader())
            habitat.id = identifier
            habitat.validate()
            habitat.editable = true
            worldSaved[identifier] = habitat
        }
        setupHabitatPoolsById()
    }

    override fun reload(data: Map<ResourceLocation, HabitatPool<*>>) {
        datapacked.clear()
        datapacked.putAll(data)
        data.entries.forEach { (identifier, habitat) ->
            habitat.id = identifier
            habitat.validate()
            habitat.editable = false
        }
        setupHabitatPoolsById()
    }

    fun setupHabitatPoolsById() {
        habitatPoolsById.clear()
        habitatPoolsById.putAll(datapacked)
        habitatPoolsById.putAll(worldSaved)
    }
}