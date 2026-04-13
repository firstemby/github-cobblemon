/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.gui.scripted.widget

enum class WidgetType(val id: String) {
    PANEL("panel"),
    LABEL("label"),
    IMAGE("image"),
    FILL("fill"),
    BUTTON("button"),
    TOGGLE("toggle"),
    SLIDER("slider"),
    TEXT_INPUT("text_input"),
    LIST("list"),
    PROGRESS_BAR("progress_bar"),
    SCROLL_PANEL("scroll_panel"),
    POKEMON_RENDER("pokemon_render"),
    CAMERA_FEED("camera_feed"),
    COMPOSITOR_OUTPUT("compositor_output"),
    MOVE_DISPLAY("move_display"),
    NUMBER_INPUT("number_input"),
    ITEM_SLOT("item_slot");

    companion object {
        private val BY_ID = entries.associateBy { it.id }
        fun fromId(id: String): WidgetType? = BY_ID[id]
    }
}
