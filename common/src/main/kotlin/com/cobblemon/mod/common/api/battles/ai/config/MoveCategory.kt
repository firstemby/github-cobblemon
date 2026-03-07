/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.battles.ai.config

import com.mojang.serialization.Codec

/**
 * Categories for classifying battle moves for AI decision-making.
 * These categories help the AI understand the purpose of moves beyond
 * just their damage type.
 *
 * @author Amo Aster
 * @since December 2025
 */
enum class MoveCategory {
    /** Direct damage-dealing moves */
    DAMAGE,
    /** Status-inflicting moves (burn, paralysis, sleep, etc.) */
    STATUS,
    /** HP recovery moves (Recover, Roost, etc.) */
    HEAL,
    /** Status curing moves (Aromatherapy, Heal Bell, etc.) */
    CURE,
    /** Stat-boosting moves for self or allies (Swords Dance, Calm Mind, etc.) */
    BUFF,
    /** Stat-lowering moves for opponents (Intimidate effects, Growl, etc.) */
    MALUS,
    /** Entry hazard moves (Stealth Rock, Spikes, etc.) */
    HAZARD,
    /** Hazard removal moves (Rapid Spin, Defog, etc.) */
    HAZARD_CONTROL,
    /** Weather-setting moves (Rain Dance, Sunny Day, etc.) */
    WEATHER,
    /** Terrain-setting moves (Electric Terrain, Grassy Terrain, etc.) */
    TERRAIN,
    /** Moves with increased priority (Quick Attack, Extreme Speed, etc.) */
    PRIORITY,
    /** Pivot moves that deal damage and switch (U-turn, Volt Switch, etc.) */
    PIVOT,
    /** Protection moves (Protect, Detect, etc.) */
    PROTECTION,
    /** Trapping moves (Mean Look, Block, etc.) */
    TRAP,
    /** Phazing moves that force switches (Roar, Whirlwind, etc.) */
    PHAZE,
    /** Redirection moves (Follow Me, Rage Powder, etc.) */
    REDIRECTION,
    /** Speed control moves (Tailwind, Trick Room, Icy Wind, etc.) */
    SPEED_CONTROL,
    /** Support moves that help allies (Helping Hand, etc.) */
    SUPPORT,
    /** Flinch-inducing moves (Fake Out, Rock Slide, etc.) */
    FLINCH;

    companion object {
        val CODEC: Codec<MoveCategory> = Codec.STRING.xmap(
            { name -> valueOf(name.uppercase()) },
            { category -> category.name.lowercase() }
        )
    }
}
