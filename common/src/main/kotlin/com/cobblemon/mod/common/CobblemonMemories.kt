/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common

import com.cobblemon.mod.common.api.ai.CobblemonAttackTargetData
import com.cobblemon.mod.common.api.ai.CobblemonWanderControl
import com.cobblemon.mod.common.api.dialogue.ActiveDialogue
import com.cobblemon.mod.common.api.moves.animations.ActionEffectContext
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.ListCodec
import com.mojang.serialization.codecs.PrimitiveCodec
import java.util.Optional
import java.util.UUID
import net.minecraft.core.BlockPos
import net.minecraft.core.UUIDUtil
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.player.Player

object CobblemonMemories {
    val memories = mutableMapOf<String, MemoryModuleType<*>>()

    /*
     * When a memory doesn't have a codec provided, it's because it doesn't
     * get serialized during relogs. This makes sense for things like battles which
     * do not survive a relog therefore any memory of it should be temporary.
     */
    @JvmField
    val BATTLING_POKEMON = register("battling_pokemon", ListCodec(UUIDUtil.CODEC, 0, 31))
    @JvmField
    val NPC_BATTLING = register("npc_battling", PrimitiveCodec.BOOL)
    @JvmField
    val DIALOGUES = register<List<ActiveDialogue>>("npc_dialogues")
    @JvmField
    val ACTIVE_ACTION_EFFECT = register<ActionEffectContext>("active_action_effect")
    @JvmField
    val POTENTIAL_MATE = register<PokemonEntity>("potential_mate")
    @JvmField
    val MATE_SCAN_COOLDOWN = register("mate_scan_cooldown", PrimitiveCodec.BOOL)
    @JvmField
    val MATES_MET = register("mates_met", PrimitiveCodec.BOOL)
    @JvmField
    val NEST_LOCATION = register<BlockPos>("nest_location")
    @JvmField
    val PATERNAL_NEST_LOCATION = register<BlockPos>("paternal_nest", BlockPos.CODEC)
    @JvmField
    val POKEMON_FLYING = register("pokemon_flying", PrimitiveCodec.BOOL)
    @JvmField
    val POKEMON_DROWSY = register("pokemon_drowsy", PrimitiveCodec.BOOL)
    @JvmField
    val POKEMON_SLEEPING = register("pokemon_sleeping", PrimitiveCodec.BOOL)
    @JvmField
    val POKEMON_BATTLE = register<UUID>("pokemon_battle")
    @JvmField
    val PATH_COOLDOWN = register<Boolean>("path_cooldown")
    @JvmField
    val TARGETED_BATTLE_POKEMON = register<UUID>("targeted_battle_pokemon")
    @JvmField
    val NEAREST_VISIBLE_ATTACKER = register<LivingEntity>("nearest_visible_attacker")
    @JvmField
    val NEARBY_GROWABLE_CROPS = register<BlockPos>("nearby_growable_crops")
    @JvmField
    val EGG_THIEF = register<Player>("egg_thief") // Stores the player stealing the egg
    @JvmField
    val EGG_THIEF_LAST_SEEN_TICK = register("egg_thief_last_seen_tick", PrimitiveCodec.LONG)
    @JvmField
    val EGG_LOCATION = register<BlockPos>("egg_location") // Stores the last known location of the egg
    @JvmField
    val EGG_IN_NEST = register("egg_in_nest", PrimitiveCodec.BOOL)
    @JvmField
    val HOLDING_EGG = register("holding_egg", PrimitiveCodec.BOOL)
    @JvmField
    val EGG_SEARCH_COUNTDOWN = register ("egg_search_countdown", PrimitiveCodec.BOOL)
    @JvmField
    val IDEAL_NEST_LOCATION = register<BlockPos>("ideal_nest_location")
    @JvmField
    val NEST_DESPAWN_IMMUNE_UNTIL_TICK = register("nest_despawn_immune_until_tick", PrimitiveCodec.LONG)
    @JvmField
    val NEARBY_SWEET_BERRY_BUSH = register<BlockPos>("nearest_sweet_berry_bush")
    @JvmField
    val DISABLE_WALK_TO_BERRY_BUSH = register<Boolean>("disable_walk_to_berry_bush")
    @JvmField
    val IS_CONSUMING_ITEM = register<Boolean>("is_consuming_item")
    @JvmField
    val HIVE_LOCATION = register<BlockPos>("hive_location", BlockPos.CODEC)
    @JvmField
    val HIVE_BLACKLIST = register<List<BlockPos>>("hive_blacklist")
    @JvmField
    val HIVE_COOLDOWN = register<Boolean>("hive_cooldown")
    @JvmField
    val NEARBY_FLOWER = register<BlockPos>("nearby_flower", BlockPos.CODEC)
    @JvmField
    val PATH_TO_NEARBY_FLOWER_COOLDOWN = register<Boolean>("path_to_nearby_flower_cooldown")
    @JvmField
    val HAS_NECTAR = register<Boolean>("has_nectar", PrimitiveCodec.BOOL)
    @JvmField
    val NEARBY_SACC_LEAVES = register<BlockPos>("nearby_sacc_leaves", BlockPos.CODEC)
    /** who am i following rn? */
    @JvmField
    val HERD_LEADER = register<String>("herd_leader", PrimitiveCodec.STRING)
    /** how many are following me rn? */
    @JvmField
    val HERD_SIZE = register<Int>("herd_size") // Don't bother saving it, we'll try to keep count roughly.
    @JvmField
    val ATTACK_TARGET_DATA = register<CobblemonAttackTargetData>("attack_target_data") // This is used to store additional information about the attack target, such as when to give up pursuit.
    @JvmField
    val WANDER_CONTROL = register<CobblemonWanderControl>("wander_control")
    @JvmField
    val DISABLE_WALK_TO_WANTED_ITEM = register<Boolean>("disable_walk_to_wanted_item")
    @JvmField
    val TIME_TRYING_TO_REACH_WANTED_ITEM = register<Int>("time_trying_to_wanted_item")

    @JvmStatic
    fun <U> register(id: String, codec: Codec<U>): MemoryModuleType<U> {
        val memoryModule = MemoryModuleType(Optional.of(codec))
        memories[id] = memoryModule
        return memoryModule
    }

    @JvmStatic
    fun <U> register(id: String): MemoryModuleType<U> {
        val memoryModule = MemoryModuleType<U>(Optional.empty())
        memories[id] = memoryModule
        return memoryModule
    }
}
