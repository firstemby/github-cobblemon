/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.camera.model

enum class HudElement(val id: String) {
    CROSSHAIR("crosshair"),
    HOTBAR("hotbar"),
    EXPERIENCE_BAR("experience_bar"),
    PLAYER_HEALTH("player_health"),
    ARMOR("armor"),
    FOOD("food"),
    AIR("air"),
    EFFECTS("effects"),
    BOSS_BAR("boss_bar"),
    SCOREBOARD("scoreboard"),
    CHAT("chat"),
    PLAYER_LIST("player_list"),
    SELECTED_ITEM_NAME("selected_item_name"),
    COBBLEMON_OVERLAY("cobblemon_overlay");

    companion object {
        val ALL: Set<HudElement> = entries.toSet()

        private val BY_ID = entries.associateBy { it.id }

        fun fromId(id: String): HudElement? = BY_ID[id]
    }
}
