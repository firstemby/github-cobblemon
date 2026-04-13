/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.gui.scripted.event

enum class WidgetEventType(val id: String) {
    CLICK("on_click"),
    CHANGE("on_change"),
    SUBMIT("on_submit"),
    HOVER_ENTER("on_hover_enter"),
    HOVER_EXIT("on_hover_exit"),
    SCROLL("on_scroll"),
    KEY("on_key");

    val clientId: String get() = "${id}_client"
}

data class WidgetEvent(
    val type: WidgetEventType,
    val widgetId: String,
    val data: Map<String, String> = emptyMap()
)
