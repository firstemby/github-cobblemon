/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.events

import com.cobblemon.mod.common.CobblemonFlows
import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.platform.events.PlatformEvents
import com.cobblemon.mod.common.util.cobblemonResource

/**
 * Handles the registration of the default Cobblemon event hooks into flows.
 */
object FlowHandler {
    fun setup() {
        CobblemonEvents.POKEMON_CAPTURED.subscribe { CobblemonFlows.run(cobblemonResource("pokemon_captured"), it.context) }
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
        CobblemonEvents.THROWN_POKEBALL_HIT.subscribe { CobblemonFlows.run(cobblemonResource("thrown_pokeball_hit"), it.context) }
        CobblemonEvents.LEVEL_UP_EVENT.subscribe { CobblemonFlows.run(cobblemonResource("level_up"), it.context, it.functions) }
        CobblemonEvents.POKEMON_FAINTED.subscribe { CobblemonFlows.run(cobblemonResource("pokemon_fainted"), it.context) }
        CobblemonEvents.POKEMON_GAINED.subscribe { CobblemonFlows.run(cobblemonResource("pokemon_gained"), it.context, it.functions) }
        CobblemonEvents.POKE_BALL_CAPTURE_CALCULATED.subscribe { CobblemonFlows.run(cobblemonResource("poke_ball_capture_calculated"), it.context, it.functions) }
        CobblemonEvents.EVOLUTION_TESTED.subscribe { CobblemonFlows.run(cobblemonResource("evolution_tested"), it.context, it.functions) }
        CobblemonEvents.EVOLUTION_ACCEPTED.subscribe { CobblemonFlows.run(cobblemonResource("evolution_accepted"), it.context, it.functions) }
        CobblemonEvents.EVOLUTION_COMPLETE.subscribe { CobblemonFlows.run(cobblemonResource("evolution_completed"), it.context) }
        CobblemonEvents.WALLPAPER_UNLOCKED_EVENT.subscribe { CobblemonFlows.run(cobblemonResource("wallpaper_unlocked"), it.context, it.functions) }

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


