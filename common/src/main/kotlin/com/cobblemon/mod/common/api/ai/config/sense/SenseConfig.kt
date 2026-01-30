/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.config.sense

import com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext
import com.cobblemon.mod.common.api.ai.biggerbrain.sense.BattlingPokemonSense
import com.cobblemon.mod.common.api.ai.biggerbrain.sense.DrowsySense
import com.cobblemon.mod.common.api.ai.biggerbrain.sense.EggSense
import com.cobblemon.mod.common.api.ai.biggerbrain.sense.HurtBySense
import com.cobblemon.mod.common.api.ai.biggerbrain.sense.IdealNestPlacementSense
import com.cobblemon.mod.common.api.ai.biggerbrain.sense.MateSense
import com.cobblemon.mod.common.api.ai.biggerbrain.sense.NPCBattlingSense
import com.cobblemon.mod.common.api.ai.biggerbrain.sense.NestSense
import com.cobblemon.mod.common.api.ai.biggerbrain.sense.NearbyEntitiesSense
import com.cobblemon.mod.common.api.ai.biggerbrain.sense.PokemonDisturbanceSense
import com.cobblemon.mod.common.api.ai.biggerbrain.sense.PokemonAdultSense
import com.cobblemon.mod.common.api.ai.biggerbrain.sense.Sense
import com.cobblemon.mod.common.api.npc.configuration.MoLangConfigVariable
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.world.entity.LivingEntity

/**
 * Creates a [Sense] for an entity.
 *
 * @author Hiroku
 * @since December 29th, 2025
 */
interface SenseConfig {
    companion object {
        val types = mutableMapOf(
            cobblemonResource("nearby_entities") to NearbyEntitiesSense.Config::class.java,
            cobblemonResource("drowsy") to DrowsySense.Config::class.java,
            cobblemonResource("hurt_by") to HurtBySense.Config::class.java,
            cobblemonResource("battling_pokemon") to BattlingPokemonSense.Config::class.java,
            cobblemonResource("npc_battling") to NPCBattlingSense.Config::class.java,
            cobblemonResource("pokemon_adult") to PokemonAdultSense.Config::class.java,
            cobblemonResource("mate_sensor") to MateSense.Config::class.java,
            cobblemonResource("nest_sensor") to NestSense.Config::class.java,
            cobblemonResource("egg_sensor") to EggSense.Config::class.java,
            cobblemonResource("ideal_nest_location_sensor") to IdealNestPlacementSense.Config::class.java,
            cobblemonResource("pokemon_disturbance") to PokemonDisturbanceSense.Config::class.java
        )
    }

    /** The variables that this sense config uses. These are used to declare variables on the entity cleanly. */
    fun getVariables(entity: LivingEntity, behaviourConfigurationContext: BehaviourConfigurationContext): List<MoLangConfigVariable> = emptyList()
    fun create(entity: LivingEntity, behaviourConfigurationContext: BehaviourConfigurationContext): Sense?
}
