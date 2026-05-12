/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.pokemon.feature

import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.aspect.AspectProvider
import com.cobblemon.mod.common.api.properties.CustomPokemonPropertyType
import com.cobblemon.mod.common.client.gui.summary.featurerenderers.SummarySpeciesFeatureRenderer
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.readString
import com.google.gson.JsonObject
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.RegistryFriendlyByteBuf

class BattleFormFeature(formName: String, enabled: Boolean): FlagSpeciesFeature(formName, enabled) {

    override fun saveToJSON(pokemonJSON: JsonObject): JsonObject {
        return pokemonJSON
    }

    override fun saveToNBT(pokemonNBT: CompoundTag): CompoundTag {
        return pokemonNBT
    }

}

class BattleFlagSpeciesFeatureProvider : SynchronizedSpeciesFeatureProvider<BattleFormFeature>,
    CustomPokemonPropertyType<BattleFormFeature>, AspectProvider {
    override var keys: List<String>
    override val needsKey get() = true
    var default: String? = null
    var isAspect = true
    override var visible: Boolean = false
    override fun invoke(buffer: RegistryFriendlyByteBuf, name: String): BattleFormFeature? {
        return if (name in keys) {
            BattleFormFeature(name, false).also { it.loadFromBuffer(buffer) }
        } else {
            null
        }
    }

    override fun examples() = setOf("true", "false")

    internal constructor() {
        this.keys = emptyList()
    }

    constructor(keys: List<String>) {
        this.keys = keys
    }

    constructor(keys: List<String>, default: Boolean) {
        this.keys = keys
        this.default = default.toString()
    }

    constructor(vararg keys: String) : this(keys.toList())

    override fun invoke(pokemon: Pokemon): BattleFormFeature? {
        return pokemon.getFeature(keys.first())
            ?: when (default) {
                in setOf("true", "false") -> BattleFormFeature(keys.first(), default.toBoolean())
                else -> null
            }
    }

    override fun saveToBuffer(buffer: RegistryFriendlyByteBuf, toClient: Boolean) {
        buffer.writeCollection(keys) { _, value -> buffer.writeUtf(value) }
        buffer.writeNullable(default) { _, value -> buffer.writeUtf(value) }
        buffer.writeBoolean(isAspect)
    }

    override fun loadFromBuffer(buffer: RegistryFriendlyByteBuf) {
        keys = buffer.readList { it.readString() }
        default = buffer.readNullable { it.readString() }
        isAspect = buffer.readBoolean()
    }

    override fun invoke(nbt: CompoundTag): BattleFormFeature? {
        return if (nbt.contains(keys.first())) {
            BattleFormFeature(keys.first(), false).also { it.loadFromNBT(nbt) }
        } else null
    }

    override fun invoke(json: JsonObject): BattleFormFeature? {
        return if (json.has(keys.first())) {
            BattleFormFeature(keys.first(), false).also { it.loadFromJSON(json) }
        } else null
    }

    override fun get(pokemon: Pokemon) = pokemon.getFeature<BattleFormFeature>(keys.first())

    override fun getRenderer(pokemon: Pokemon): SummarySpeciesFeatureRenderer<BattleFormFeature>? {
        return null
    }

    override fun fromString(value: String?): BattleFormFeature? {
        val isWeirdValue = value != null && value !in examples()

        if (isWeirdValue) {
            return null
        }

        return if (value == null) {
            BattleFormFeature(keys.first(), true)
        } else {
            BattleFormFeature(keys.first(), value.toBoolean())
        }
    }

    override fun provide(pokemon: Pokemon): Set<String> {
        return if (isAspect && pokemon.getFeature<FlagSpeciesFeature>(keys.first())?.enabled == true) {
            setOf(keys.first())
        } else {
            emptySet()
        }
    }

    override fun provide(properties: PokemonProperties): Set<String> {
        return if (isAspect && properties.customProperties.filterIsInstance<FlagSpeciesFeature>().find { it.name == keys.first() }?.enabled == true) {
            setOf(keys.first())
        } else {
            emptySet()
        }
    }
}