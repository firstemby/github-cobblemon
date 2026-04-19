/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.npc.premades

import com.bedrockk.molang.runtime.value.MoValue
import com.cobblemon.mod.common.api.npc.NPCClass
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.PokemonPropertyExtractor
import com.cobblemon.mod.common.api.storage.party.NPCPartyStore
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.entity.npc.NPCEntity
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.toProperties
import com.google.gson.annotations.SerializedName
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

/**
 * A snapshot of NPC settings that can be applied to other entities
 *
 * @author Hiroku
 * @since April 11th, 2026
 */
class PremadeNPC {
    @Transient
    var id: ResourceLocation = cobblemonResource("a")
    var displayName: Component = "".text()
    @Transient
    var editable = true

    // Classification
    var classification: Classification? = null

    // Variation
    var variation: Variation? = null

    // Behaviour Settings
    @SerializedName("behaviourSettings", alternate = ["behaviorSettings"])
    var behaviourSettings: Behaviours? = null

    // Party
    var party: List<PokemonProperties>? = null

    class Variation {
        var variationAspects = mutableSetOf<String>()
    }

    class Behaviours {
        @SerializedName("behaviours", alternate = ["behaviors"])
        var behaviours = mutableListOf<ResourceLocation>()
        var settings = mutableMapOf<String, MoValue>()
    }

    class Classification {
        lateinit var npcClass: NPCClass
        var appliedAspects = mutableSetOf<String>()
    }

    fun applyTo(
        npc: NPCEntity,
    ) {
        classification?.let {
            npc.npc = it.npcClass
            npc.appliedAspects.clear()
            npc.appliedAspects.addAll(it.appliedAspects)
        }
        behaviourSettings?.let {
            npc.config.map.clear()
            npc.config.map.putAll(it.settings)
            npc.behaviours.clear()
            npc.behaviours.addAll(it.behaviours)
            npc.behavioursAreCustom = true
            npc.remakeBrain()
        }
        variation?.let {
            npc.variationAspects.clear()
            npc.variationAspects.addAll(it.variationAspects)
        }
        party?.let { pokemon ->
            npc.party = NPCPartyStore(npc).also { pokemon.map(PokemonProperties::create).forEach(it::add) }
        }
    }

    fun copyFrom(
        npc: NPCEntity,
        includeVariation: Boolean,
        includeSettings: Boolean,
        includeParty: Boolean,
        includeClassification: Boolean
    ): PremadeNPC {
        classification = if (includeClassification) {
            Classification().also {
                it.npcClass = npc.npc
                it.appliedAspects.clear()
                it.appliedAspects.addAll(npc.appliedAspects)
            }
        } else {
            null
        }

        behaviourSettings = if (includeSettings) {
            Behaviours().also {
                it.behaviours.clear()
                it.behaviours.addAll(npc.behaviours)
                it.settings.clear()
                it.settings.putAll(npc.config.map)
            }
        } else {
            null
        }

        variation = if (includeVariation) {
            Variation().also {
                it.variationAspects.clear()
                it.variationAspects.addAll(npc.variationAspects)
            }
        } else {
            null
        }

        party = if (includeParty && npc.party.let { it != null && it.size() > 0 }) {
            npc.party!!.map { it.createPokemonProperties(PokemonPropertyExtractor.ALL).asString().toProperties() }
        } else {
            null
        }

        return this
    }
}