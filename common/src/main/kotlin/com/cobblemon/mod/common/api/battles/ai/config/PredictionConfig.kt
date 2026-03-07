/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.battles.ai.config

import com.google.gson.annotations.SerializedName
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

/** Configuration for opponent prediction and memory systems. */
data class PredictionConfig(
    val enabled: Boolean = true,
    @SerializedName("remember_revealed_moves")
    val rememberRevealedMoves: Boolean = true,
    @SerializedName("remember_revealed_items")
    val rememberRevealedItems: Boolean = true,
    @SerializedName("remember_revealed_abilities")
    val rememberRevealedAbilities: Boolean = true,
    @SerializedName("predict_based_on_team_preview")
    val predictBasedOnTeamPreview: Boolean = true,
    @SerializedName("predict_moves_from_species")
    val predictMovesFromSpecies: Boolean = true,
    @SerializedName("memory_decay")
    val memoryDecay: Double = 0.0
) {
    companion object {
        val CODEC: Codec<PredictionConfig> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.BOOL.optionalFieldOf("enabled", true).forGetter { it.enabled },
                Codec.BOOL.optionalFieldOf("remember_revealed_moves", true).forGetter { it.rememberRevealedMoves },
                Codec.BOOL.optionalFieldOf("remember_revealed_items", true).forGetter { it.rememberRevealedItems },
                Codec.BOOL.optionalFieldOf("remember_revealed_abilities", true).forGetter { it.rememberRevealedAbilities },
                Codec.BOOL.optionalFieldOf("predict_based_on_team_preview", true).forGetter { it.predictBasedOnTeamPreview },
                Codec.BOOL.optionalFieldOf("predict_moves_from_species", true).forGetter { it.predictMovesFromSpecies },
                Codec.DOUBLE.optionalFieldOf("memory_decay", 0.0).forGetter { it.memoryDecay }
            ).apply(instance, ::PredictionConfig)
        }

        val DEFAULT = PredictionConfig()
        val DISABLED = PredictionConfig(enabled = false)
        val PERFECT_MEMORY = PredictionConfig(memoryDecay = 0.0)
    }
}
