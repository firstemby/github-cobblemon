/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.cinematic.model.ActorBinding
import com.cobblemon.mod.common.api.cinematic.model.ActorKind
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorActor
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import net.minecraft.resources.ResourceLocation

/** Real roles use synthetic stand-ins; previews never bind the editor's player. */
object EditorActorBindingProvider {

    fun collect(scene: EditorScene): Map<String, ActorBinding> {
        val out = LinkedHashMap<String, ActorBinding>(scene.actors.size)
        scene.actors.values.forEach { actor ->
            tryBind(actor)?.let { out[actor.id] = it }
        }
        return out
    }

    private fun tryBind(actor: EditorActor): ActorBinding? = when (actor.kind) {
        ActorKind.PlayerTakeover -> null
        ActorKind.RealEntity -> synthPokemon(actor)
        ActorKind.SynthesizedPokemon -> synthPokemon(actor)
        ActorKind.SynthesizedBedrock -> synthBedrock(actor)
    }

    private fun synthPokemon(actor: EditorActor): ActorBinding? {
        val species = actor.previewSpecies.trim()
        if (species.isBlank()) {
            Cobblemon.LOGGER.debug("[EditorBindings] skip '${actor.id}' — no previewSpecies set")
            return null
        }
        val properties = try {
            PokemonProperties.parse(species)
        } catch (t: Exception) {
            Cobblemon.LOGGER.warn("[EditorBindings] failed to parse species '$species' for '${actor.id}': ${t.message}")
            return null
        }
        return ActorBinding.SynthPokemon(
            properties = properties,
            aspects = actor.previewAspects.toSet()
        )
    }

    private fun synthBedrock(actor: EditorActor): ActorBinding? {
        val raw = actor.previewCategory.trim()
        if (raw.isBlank()) {
            Cobblemon.LOGGER.debug("[EditorBindings] skip '${actor.id}' — no previewCategory set")
            return null
        }
        val category = parseResourceLocation(raw) ?: run {
            Cobblemon.LOGGER.warn("[EditorBindings] invalid category '$raw' for '${actor.id}'")
            return null
        }
        val scale = if (actor.previewScale.isFinite() && actor.previewScale > 0f) actor.previewScale else 1f
        return ActorBinding.SynthBedrock(
            category = category,
            aspects = actor.previewAspects.toSet(),
            scale = scale
        )
    }

    private fun parseResourceLocation(value: String): ResourceLocation? = try {
        if (value.contains(':')) ResourceLocation.parse(value)
        else ResourceLocation.fromNamespaceAndPath("cobblemon", value)
    } catch (_: IllegalArgumentException) { null }
}
