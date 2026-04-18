package com.cobblemon.mod.common.api.npc.premades

import com.bedrockk.molang.runtime.value.MoValue
import com.cobblemon.mod.common.api.npc.NPCClass
import com.cobblemon.mod.common.api.storage.party.NPCPartyStore
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.entity.npc.NPCEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.cobblemonResource
import com.google.gson.annotations.SerializedName
import net.minecraft.network.chat.MutableComponent
import net.minecraft.resources.ResourceLocation

/**
 * A snapshot of NPC settings that can be applied to other entities
 *
 * @author Hiroku
 * @since April 11th, 2026
 */
class PremadeNPC {
    var id: ResourceLocation = cobblemonResource("a")
    var displayName: MutableComponent = "".text()
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
    var party: List<Pokemon>? = null

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
            npc.party = NPCPartyStore(npc).also { pokemon.forEach(it::add) }
        }
    }

    fun copyFrom(
        npc: NPCEntity,
        includeVariation: Boolean,
        includeSettings: Boolean,
        includeParty: Boolean,
        includeClassification: Boolean
    ) {
        if (includeClassification) {
            classification = Classification().also {
                it.npcClass = npc.npc
                it.appliedAspects.clear()
                it.appliedAspects.addAll(npc.appliedAspects)
            }
        }

        if (includeSettings) {
            behaviourSettings = Behaviours().also {
                it.behaviours.clear()
                it.behaviours.addAll(npc.behaviours)
                it.settings.clear()
                it.settings.putAll(npc.config.map)
            }
        }

        if (includeVariation) {
            variation = Variation().also {
                it.variationAspects.clear()
                it.variationAspects.addAll(npc.variationAspects)
            }
        }

        if (includeParty) {
            party = npc.party?.toMutableList()
        }
    }
}