/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain.sense

import com.cobblemon.mod.common.api.ai.biggerbrain.BiggerBrain
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.memory.MemoryModuleType

/**
 * A sense that an entity can use to gather information and store it in its brain's memory. This is an analog to a
 * Minecraft SensorType. Unlike SensorTypes, Senses are instantiated per entity and can handle cooldowns in a more
 * bespoke way. The remaining cooldown time is something that is set by the sense itself rather than it being forced,
 * so the cooldown can differ.
 *
 * @author Hiroku
 * @since December 29th, 2025
 */
interface Sense {
    companion object {
        val types = mutableMapOf(
            cobblemonResource("battling_pokemon") to BattlingPokemonSense::class.java,
            cobblemonResource("drowsy") to DrowsySense::class.java,
            cobblemonResource("nearby_entities") to NearbyEntitiesSense::class.java,
            cobblemonResource("npc_battling") to NPCBattlingSense::class.java,
            cobblemonResource("pokemon_adult") to PokemonAdultSense::class.java,
            cobblemonResource("hurt_by") to HurtBySense::class.java,
            cobblemonResource("jukebox_playing") to JukeboxSense::class.java,
            cobblemonResource("nearby_sweet_berry_bush") to SweetBerryBushSense::class.java,
            cobblemonResource("pokemon_nearby_wanted_item") to PokemonItemSense.Config::class.java,
            cobblemonResource("flower_sense") to FlowerSense.Config::class.java,
            cobblemonResource("beehive_sense") to BeeHiveSense.Config::class.java,
            cobblemonResource("sacc_leaf_sense") to SaccLeafSense.Config::class.java,
            )
    }

    /** The ticks until it will next be ticked. This is also displayed in the debug overlay. */
    var ticksUntilNextExecution: Int

    /** The set of memories that this sense may write to or read from. This ensures they are registered in the brain. */
    val memories: Set<MemoryModuleType<*>>

    /** Performs the sensing logic. Alter [ticksUntilNextExecution] to apply a cooldown if necessary. */
    fun tick(entity: LivingEntity, brain: BiggerBrain)

    /** Attempts to merge the [other] Sense into this one. Returns true if it was merged and therefore should not be added to the brain separately. */
    fun merge(other: Sense): Boolean
}
