/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.gui.scripted.widget

import com.cobblemon.mod.common.api.gui.scripted.layout.Anchor

/**
 * A parsed widget definition from JSON. Represents a node in the widget tree.
 * Properties are stored as WidgetProperty (static or Molang expression).
 * Children are stored as named WidgetDefinitions in the controls list.
 */
data class WidgetDefinition(
    val id: String,
    val type: WidgetType,
    val properties: Map<String, WidgetProperty> = emptyMap(),
    val children: List<WidgetDefinition> = emptyList(),
    val events: Map<String, String> = emptyMap(),
    val clientEvents: Map<String, String> = emptyMap(),
    val hoverOverrides: Map<String, WidgetProperty> = emptyMap(),
    val transitions: Map<String, TransitionConfig> = emptyMap(),
    val enterAnimation: AnimationConfig? = null,
    val exitAnimation: AnimationConfig? = null,
    val isStatic: Boolean = false
) {
    fun getProperty(name: String): WidgetProperty? = properties[name]
    fun getStringProperty(name: String): String? = (properties[name]?.raw as? String)
    fun getAnchor(): Anchor = Anchor.fromId(getStringProperty("anchor") ?: "top_left")
}

data class TransitionConfig(
    val duration: Float,
    val easing: String = "ease_out"
)

data class AnimationConfig(
    val type: String,
    val duration: Float = 0.3f,
    val easing: String = "ease_out"
)
