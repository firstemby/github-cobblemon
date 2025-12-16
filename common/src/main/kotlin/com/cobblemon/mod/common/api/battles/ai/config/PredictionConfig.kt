/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.battles.ai.config

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

/**
 * Configuration for opponent prediction and memory systems.
 * Controls how the AI tracks and predicts opponent behavior.
 *
 * @property enabled Whether prediction features are enabled
 * @property rememberRevealedMoves Whether to track moves the opponent has used
 * @property rememberRevealedItems Whether to track items the opponent has used/revealed
 * @property rememberRevealedAbilities Whether to track abilities the opponent has revealed
 * @property predictBasedOnTeamPreview Whether to make predictions based on team preview information
 * @property predictMovesFromSpecies Whether to predict likely moves based on the opponent's species
 * @property memoryDecay How much memory fades over turns (0.0 = perfect memory, 1.0 = no memory)
 *
 * @author Amo Aster
 * @since December 2025
 */
data class PredictionConfig(
    val enabled: Boolean = true,
    val rememberRevealedMoves: Boolean = true,
    val rememberRevealedItems: Boolean = true,
    val rememberRevealedAbilities: Boolean = true,
    val predictBasedOnTeamPreview: Boolean = true,
    val predictMovesFromSpecies: Boolean = true,
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
