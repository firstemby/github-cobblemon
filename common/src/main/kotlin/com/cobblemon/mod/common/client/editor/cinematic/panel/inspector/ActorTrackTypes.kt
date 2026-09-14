/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic.panel.inspector

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.minecraft.client.resources.language.I18n

/** Track body JSON excludes type, which is stored separately in EditorTrack.trackType. */
data class ActorTrackEntry(
    val trackType: String,
    private val labelKey: String,
    val defaultBody: () -> JsonObject
) {
    val label get() = I18n.get(labelKey)
}

object ActorTrackTypes {

    private val ENTRIES: List<ActorTrackEntry> = listOf(
        ActorTrackEntry("keyframe_movement", "cobblemon.cinematic_editor.track.keyframe_movement") {
            JsonObject().apply {
                add("keyframes", JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("time", 0f)
                        add("position", vec3Json(0.0, 64.0, 0.0))
                    })
                })
            }
        },
        ActorTrackEntry("spline_movement", "cobblemon.cinematic_editor.track.spline_movement") {
            JsonObject().apply {
                addProperty("duration", 5f)
                add("controlPoints", JsonArray().apply {
                    add(vec3Json(0.0, 64.0, 0.0))
                    add(vec3Json(10.0, 64.0, 0.0))
                })
            }
        },
        ActorTrackEntry("pathfinding", "cobblemon.cinematic_editor.track.waypoint_pathfinding") {
            JsonObject().apply {
                addProperty("duration", 30f)
                addProperty("speedMultiplier", 1f)
                addProperty("travelMode", "auto")
                add("controlPoints", JsonArray().apply {
                    add(vec3Json(0.0, 64.0, 0.0))
                    add(vec3Json(4.0, 64.0, 0.0))
                })
            }
        },
        ActorTrackEntry("follow_actor", "cobblemon.cinematic_editor.track.follow_actor") {
            JsonObject().apply {
                addProperty("targetActorId", "")
                add("offset", vec3Json(0.0, 0.0, 0.0))
                addProperty("lag", 0f)
            }
        },
        ActorTrackEntry("look_at", "cobblemon.cinematic_editor.track.look_at_vec3") {
            JsonObject().apply {
                add("target", vec3Json(0.0, 64.0, 0.0))
            }
        },
        ActorTrackEntry("animation", "cobblemon.cinematic_editor.track.animation_cues") {
            JsonObject().apply {
                add("cues", JsonArray())
            }
        },
        ActorTrackEntry("attach", "cobblemon.cinematic_editor.track.attach_to_actor") {
            JsonObject().apply {
                addProperty("parentActorId", "")
                add("offset", vec3Json(0.0, 0.0, 0.0))
            }
        },
        ActorTrackEntry("pose_lock", "cobblemon.cinematic_editor.track.pose_lock") {
            JsonObject().apply {
                addProperty("pose", "STAND")
            }
        }
    )

    fun entries(): List<ActorTrackEntry> = ENTRIES

    fun entryFor(trackType: String): ActorTrackEntry? =
        ENTRIES.firstOrNull { it.trackType == trackType }

    private fun vec3Json(x: Double, y: Double, z: Double): JsonObject = JsonObject().apply {
        addProperty("x", x); addProperty("y", y); addProperty("z", z)
    }
}
