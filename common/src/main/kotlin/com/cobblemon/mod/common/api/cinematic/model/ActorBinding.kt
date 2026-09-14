/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.model

import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity

sealed class ActorBinding {
    abstract val kind: ActorKind

    /** Visible player actors override server position and suppress movement packets. */
    data class Player(
        val player: ServerPlayer,
        val fallbackPokemon: PokemonProperties? = null
    ) : ActorBinding() {
        override val kind: ActorKind = ActorKind.PlayerTakeover
    }

    data class Real(val entity: Entity) : ActorBinding() {
        override val kind: ActorKind = ActorKind.RealEntity
    }

    data class SynthPokemon(
        val properties: PokemonProperties,
        val aspects: Set<String> = emptySet()
    ) : ActorBinding() {
        override val kind: ActorKind = ActorKind.SynthesizedPokemon
    }

    data class SynthBedrock(
        val category: ResourceLocation,
        val aspects: Set<String> = emptySet(),
        val scale: Float = 1.0f
    ) : ActorBinding() {
        override val kind: ActorKind = ActorKind.SynthesizedBedrock

        init {
            require(scale.isFinite() && scale > 0f) { "SynthBedrock scale must be positive, got $scale" }
        }
    }
}
