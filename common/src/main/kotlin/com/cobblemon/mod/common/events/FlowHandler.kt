/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.events

import com.bedrockk.molang.runtime.value.MoValue
import com.cobblemon.mod.common.CobblemonFlows
import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMoLangValue
import com.cobblemon.mod.common.platform.events.PlatformEvents
import com.cobblemon.mod.common.util.cobblemonResource

/**
 * Handles the registration of the default Cobblemon event hooks into flows.
 */
object FlowHandler {
    fun setup() {
        CobblemonEvents.STARTER_CHOSEN.subscribe { CobblemonFlows.run(cobblemonResource("starter_chosen"), it.getContext(), it.functions) }
        CobblemonEvents.POKEMON_CAPTURED.subscribe { CobblemonFlows.run(cobblemonResource("pokemon_captured"), it.context) }
        CobblemonEvents.POKEMON_ENTITY_SPAWN.subscribe { CobblemonFlows.run(cobblemonResource("pokemon_entity_spawn"), mutableMapOf<String, MoValue>("pokemon_entity" to it.entity.asMoLangValue())) }
        CobblemonEvents.BATTLE_VICTORY.subscribe { CobblemonFlows.run(cobblemonResource("battle_victory"), it.context) }
        CobblemonEvents.POKEDEX_DATA_CHANGED_PRE.subscribe { CobblemonFlows.run(cobblemonResource("pokedex_data_changed_pre"), it.getContext(), it.functions) }
        CobblemonEvents.POKEDEX_DATA_CHANGED_POST.subscribe { CobblemonFlows.run(cobblemonResource("pokedex_data_changed_post"), it.getContext()) }
        CobblemonEvents.FORME_CHANGE.subscribe { CobblemonFlows.run(cobblemonResource("forme_change"), it.context) }
        CobblemonEvents.MEGA_EVOLUTION.subscribe { CobblemonFlows.run(cobblemonResource("mega_evolution"), it.context) }
        CobblemonEvents.ZPOWER_USED.subscribe { CobblemonFlows.run(cobblemonResource("zpower_used"), it.context) }
        CobblemonEvents.TERASTALLIZATION.subscribe { CobblemonFlows.run(cobblemonResource("terastallization"), it.context) }
        CobblemonEvents.BATTLE_FAINTED.subscribe { CobblemonFlows.run(cobblemonResource("battle_fainted"), it.structContext) }
        CobblemonEvents.BATTLE_FLED.subscribe { CobblemonFlows.run(cobblemonResource("battle_fled"), it.context) }
        CobblemonEvents.BATTLE_STARTED_PRE.subscribe { CobblemonFlows.run(cobblemonResource("battle_started_pre"), it.context, it.functions) }
        CobblemonEvents.BATTLE_STARTED_POST.subscribe { CobblemonFlows.run(cobblemonResource("battle_started_post"), it.context) }
        CobblemonEvents.APRICORN_HARVESTED.subscribe { CobblemonFlows.run(cobblemonResource("apricorn_harvested"), it.context) }
        CobblemonEvents.BERRY_HARVEST.subscribe { CobblemonFlows.run(cobblemonResource("berry_harvested"), it.context) }
        CobblemonEvents.THROWN_POKEBALL_HIT.subscribe { CobblemonFlows.run(cobblemonResource("thrown_pokeball_hit"), it.context) }
        CobblemonEvents.POKEMON_HEALED.subscribe { CobblemonFlows.run(cobblemonResource("pokemon_healed"), it.context, it.functions) }
        CobblemonEvents.POKEMON_ASPECTS_CHANGED.subscribe { CobblemonFlows.run(cobblemonResource("pokemon_aspects_changed"), it.context) }
        CobblemonEvents.LEVEL_UP_EVENT.subscribe { CobblemonFlows.run(cobblemonResource("level_up"), it.context, it.functions) }
        CobblemonEvents.FRIENDSHIP_UPDATED.subscribe { CobblemonFlows.run(cobblemonResource("friendship_updated"), it.context, it.functions) }
        CobblemonEvents.FULLNESS_UPDATED.subscribe { CobblemonFlows.run(cobblemonResource("fullness_updated"), it.context, it.functions) }
        CobblemonEvents.HYPER_TRAINED_IV_PRE.subscribe { CobblemonFlows.run(cobblemonResource("hyper_trained_iv_pre"), it.context, it.functions) }
        CobblemonEvents.HYPER_TRAINED_IV_POST.subscribe { CobblemonFlows.run(cobblemonResource("hyper_trained_iv_post"), it.context) }
        CobblemonEvents.EV_GAINED_EVENT_PRE.subscribe { CobblemonFlows.run(cobblemonResource("ev_gained_pre"), it.context, it.functions) }
        CobblemonEvents.EV_GAINED_EVENT_POST.subscribe { CobblemonFlows.run(cobblemonResource("ev_gained_post"), it.context) }
        CobblemonEvents.EXPERIENCE_GAINED_EVENT_PRE.subscribe { CobblemonFlows.run(cobblemonResource("experience_gained_pre"), it.context, it.functions) }
        CobblemonEvents.EXPERIENCE_GAINED_EVENT_POST.subscribe { CobblemonFlows.run(cobblemonResource("experience_gained_post"), it.context) }
        CobblemonEvents.LOOT_DROPPED.subscribe { CobblemonFlows.run(cobblemonResource("loot_dropped"), it.context) }
        CobblemonEvents.POKEMON_FAINTED.subscribe { CobblemonFlows.run(cobblemonResource("pokemon_fainted"), it.context) }
        CobblemonEvents.POKEMON_GAINED.subscribe { CobblemonFlows.run(cobblemonResource("pokemon_gained"), it.context, it.functions) }
        CobblemonEvents.POKEMON_SEEN.subscribe { CobblemonFlows.run(cobblemonResource("pokemon_seen"), it.context, it.functions) }
        CobblemonEvents.POKEMON_SCANNED.subscribe { CobblemonFlows.run(cobblemonResource("pokemon_scanned"), it.context) }
        CobblemonEvents.POKEMON_SENT_PRE.subscribe { CobblemonFlows.run(cobblemonResource("pokemon_sent_pre"), it.context, it.functions) }
        CobblemonEvents.POKEMON_SENT_POST.subscribe { CobblemonFlows.run(cobblemonResource("pokemon_sent_post"), it.context) }
        CobblemonEvents.POKEMON_RELEASED_EVENT_PRE.subscribe { CobblemonFlows.run(cobblemonResource("pokemon_released_pre"), it.getContext(), it.functions) }
        CobblemonEvents.POKEMON_RELEASED_EVENT_POST.subscribe { CobblemonFlows.run(cobblemonResource("pokemon_released_post"), it.getContext()) }
        CobblemonEvents.POKEMON_CATCH_RATE.subscribe { CobblemonFlows.run(cobblemonResource("pokemon_catch_rate_calculated"), it.context, it.functions) }
        CobblemonEvents.POKE_BALL_CAPTURE_CALCULATED.subscribe { CobblemonFlows.run(cobblemonResource("poke_ball_capture_calculated"), it.context, it.functions) }
        CobblemonEvents.EVOLUTION_TESTED.subscribe { CobblemonFlows.run(cobblemonResource("evolution_tested"), it.context, it.functions) }
        CobblemonEvents.EVOLUTION_ACCEPTED.subscribe { CobblemonFlows.run(cobblemonResource("evolution_accepted"), it.context, it.functions) }
        CobblemonEvents.EVOLUTION_COMPLETE.subscribe { CobblemonFlows.run(cobblemonResource("evolution_completed"), it.context) }
        CobblemonEvents.POKEMON_NICKNAMED.subscribe { CobblemonFlows.run(cobblemonResource("pokemon_nicknamed"), it.getContext()) }
        CobblemonEvents.HELD_ITEM_PRE.subscribe { CobblemonFlows.run(cobblemonResource("held_item_pre"), it.getContext(), it.functions) }
        CobblemonEvents.HELD_ITEM_POST.subscribe { CobblemonFlows.run(cobblemonResource("held_item_post"), it.getContext()) }
        CobblemonEvents.COSMETIC_ITEM_PRE.subscribe { CobblemonFlows.run(cobblemonResource("cosmetic_item_pre"), it.getContext(), it.functions) }
        CobblemonEvents.COSMETIC_ITEM_POST.subscribe { CobblemonFlows.run(cobblemonResource("cosmetic_item_post"), it.getContext()) }
        CobblemonEvents.FOSSIL_REVIVED.subscribe { CobblemonFlows.run(cobblemonResource("fossil_revived"), it.context) }
        CobblemonEvents.BAIT_SET.subscribe { CobblemonFlows.run(cobblemonResource("bait_set"), it.context, it.functions) }
        CobblemonEvents.BAIT_SET_PRE.subscribe { CobblemonFlows.run(cobblemonResource("bait_set_pre"), it.context, it.functions) }
        CobblemonEvents.BAIT_CONSUMED.subscribe { CobblemonFlows.run(cobblemonResource("bait_consumed"), it.context) }
        CobblemonEvents.POKEROD_CAST_PRE.subscribe { CobblemonFlows.run(cobblemonResource("pokerod_cast_pre"), it.context, it.functions) }
        CobblemonEvents.POKEROD_CAST_POST.subscribe { CobblemonFlows.run(cobblemonResource("pokerod_cast_post"), it.context) }
        CobblemonEvents.POKEROD_REEL.subscribe { CobblemonFlows.run(cobblemonResource("pokerod_reel"), it.context, it.functions) }
        CobblemonEvents.BOBBER_SPAWN_POKEMON_PRE.subscribe { CobblemonFlows.run(cobblemonResource("bobber_spawn_pokemon_pre"), it.context, it.functions) }
        CobblemonEvents.BOBBER_SPAWN_POKEMON_POST.subscribe { CobblemonFlows.run(cobblemonResource("bobber_spawn_pokemon_post"), it.context) }
        CobblemonEvents.TRADE_COMPLETED.subscribe { CobblemonFlows.run(cobblemonResource("trade_completed"), it.context) }
        CobblemonEvents.WALLPAPER_UNLOCKED_EVENT.subscribe { CobblemonFlows.run(cobblemonResource("wallpaper_unlocked"), it.context, it.functions) }
        CobblemonEvents.CHANGE_PC_BOX_WALLPAPER_EVENT_PRE.subscribe { CobblemonFlows.run(cobblemonResource("change_pc_box_wallpaper_pre"), it.context, it.functions) }
        CobblemonEvents.CHANGE_PC_BOX_WALLPAPER_EVENT_POST.subscribe { CobblemonFlows.run(cobblemonResource("change_pc_box_wallpaper"), it.context) }
        CobblemonEvents.COLLECT_EGG.subscribe { CobblemonFlows.run(cobblemonResource("collect_egg"), it.context, it.functions) }
        CobblemonEvents.HATCH_EGG_PRE.subscribe { CobblemonFlows.run(cobblemonResource("hatch_egg_pre"), it.context, it.functions) }
        CobblemonEvents.HATCH_EGG_POST.subscribe { CobblemonFlows.run(cobblemonResource("hatch_egg_post"), it.context) }
        CobblemonEvents.SHOULDER_MOUNT.subscribe { CobblemonFlows.run(cobblemonResource("shoulder_mount"), it.context, it.functions) }

        PlatformEvents.SERVER_PLAYER_LOGIN.subscribe(priority = Priority.LOW) { CobblemonFlows.run(cobblemonResource("player_logged_in"), it.context) }
        PlatformEvents.SERVER_PLAYER_LOGOUT.subscribe(priority = Priority.HIGH) { CobblemonFlows.run(cobblemonResource("player_logged_out"), it.context) }

        PlatformEvents.SERVER_PLAYER_TICK_PRE.subscribe { CobblemonFlows.run(cobblemonResource("player_tick_pre"), it.context) }
        PlatformEvents.SERVER_PLAYER_TICK_POST.subscribe { CobblemonFlows.run(cobblemonResource("player_tick_post"), it.context) }

        PlatformEvents.SERVER_PLAYER_ADVANCEMENT_EARNED.subscribe { CobblemonFlows.run(cobblemonResource("advancement_earned"), it.context) }
        PlatformEvents.RIGHT_CLICK_BLOCK.subscribe { CobblemonFlows.run(cobblemonResource("right_clicked_block"), it.context, it.functions) }
        PlatformEvents.RIGHT_CLICK_ENTITY.subscribe { CobblemonFlows.run(cobblemonResource("right_clicked_entity"), it.context, it.functions) }
        PlatformEvents.PLAYER_DEATH.subscribe { CobblemonFlows.run(cobblemonResource("player_died"), it.context) }
    }
}
