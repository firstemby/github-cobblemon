/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.events

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents.BATTLE_VICTORY
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent
import com.cobblemon.mod.common.api.tags.CobblemonItemTags
import com.cobblemon.mod.common.util.getPlayer
import net.minecraft.world.entity.EquipmentSlot
import java.util.*

object ExpShareHatHandler : EventHandler {
    override fun registerListeners() {
        BATTLE_VICTORY.subscribe(Priority.NORMAL, ::onWinBattle)
    }

    fun onWinBattle(event: BattleVictoryEvent) {
        if (!event.battle.isPvW || event.wasWildCapture) return
        if (!Cobblemon.config.expShareHatPlayerXpEnabled) return

        val xpAmount = Cobblemon.config.expShareHatPlayerXpAmount
        if (xpAmount <= 0) return

        event.winners
            .flatMap { it.getPlayerUUIDs().mapNotNull(UUID::getPlayer) }
            .filter { player ->
                val headItem = player.getItemBySlot(EquipmentSlot.HEAD)
                headItem.`is`(CobblemonItemTags.EXPERIENCE_SHARE)
            }
            .forEach { player ->
                player.giveExperiencePoints(xpAmount)
            }
    }
}