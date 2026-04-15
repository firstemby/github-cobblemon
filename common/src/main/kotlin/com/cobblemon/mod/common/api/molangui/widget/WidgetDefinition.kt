/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molangui.widget

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
    ITEM_SLOT("item_slot"),
    SLOT_GRID("slot_grid");

    companion object {
        private val BY_ID = entries.associateBy { it.id }
        fun fromId(id: String): WidgetType? = BY_ID[id]
    }
}

enum class Anchor(val id: String, val xFactor: Float, val yFactor: Float) {
    TOP_LEFT("top_left", 0f, 0f),
    TOP_CENTER("top_center", 0.5f, 0f),
    TOP_RIGHT("top_right", 1f, 0f),
    CENTER_LEFT("center_left", 0f, 0.5f),
    CENTER("center", 0.5f, 0.5f),
    CENTER_RIGHT("center_right", 1f, 0.5f),
    BOTTOM_LEFT("bottom_left", 0f, 1f),
    BOTTOM_CENTER("bottom_center", 0.5f, 1f),
    BOTTOM_RIGHT("bottom_right", 1f, 1f);

    companion object {
        private val BY_ID = entries.associateBy { it.id }
        fun fromId(id: String): Anchor = BY_ID[id] ?: TOP_LEFT
    }
}
