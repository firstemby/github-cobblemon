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
import java.util.Optional

/**
 * Configuration for the AI personality system.
 *
 * @property enabled Whether personality traits affect AI decisions
 * @property basePersonality The base personality to use, or null for random generation
 * @property traitVariance The maximum random variance applied to each trait (0.0-0.5)
 * @property consistencyWithinBattle Whether the AI makes consistent decisions in similar situations
 *
 * @author Amo Aster
 * @since December 2025
 */
data class PersonalityConfig(
    val enabled: Boolean = true,
    @SerializedName("base_personality")
    val basePersonality: Personality? = null,
    @SerializedName("trait_variance")
    val traitVariance: Double = 0.15,
    @SerializedName("consistency_within_battle")
    val consistencyWithinBattle: Boolean = true
) {
    companion object {
        val CODEC: Codec<PersonalityConfig> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.BOOL.optionalFieldOf("enabled", true).forGetter { it.enabled },
                Personality.CODEC.optionalFieldOf("base_personality").forGetter { Optional.ofNullable(it.basePersonality) },
                Codec.DOUBLE.optionalFieldOf("trait_variance", 0.15).forGetter { it.traitVariance },
                Codec.BOOL.optionalFieldOf("consistency_within_battle", true).forGetter { it.consistencyWithinBattle }
            ).apply(instance) { enabled, basePersonality, traitVariance, consistencyWithinBattle ->
                PersonalityConfig(enabled, basePersonality.orElse(null), traitVariance, consistencyWithinBattle)
            }
        }

        val DEFAULT = PersonalityConfig()
        val DISABLED = PersonalityConfig(enabled = false)
    }

    /**
     * Generates a personality based on this configuration
     */
    fun generatePersonality(): Personality {
        if (!enabled) return Personality.NEUTRAL

        val base = basePersonality ?: Personality.random(traitVariance)
        return if (basePersonality != null && traitVariance > 0) {
            base.withVariance(traitVariance)
        } else {
            base
        }
    }
}
