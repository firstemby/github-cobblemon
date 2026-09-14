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

enum class SourceChannel { POSITION, ROTATION, FOV, ROLL, MODIFIER }

data class SourceTypeEntry(
    val typeId: String,
    private val labelKey: String,
    val defaultJson: () -> JsonObject
) {
    val label get() = I18n.get(labelKey)
}

object ShotSourceTypes {

    private val POSITION_ENTRIES = listOf(
        SourceTypeEntry("fixed", "cobblemon.cinematic_editor.source.fixed") {
            JsonObject().apply {
                addProperty("type", "fixed")
                add("position", vec3Json(0.0, 64.0, 0.0))
            }
        },
        SourceTypeEntry("keyframe", "cobblemon.cinematic_editor.source.keyframe") {
            JsonObject().apply {
                addProperty("type", "keyframe")
                addProperty("easing", "linear")
                add("keyframes", JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("time", 0f)
                        add("position", vec3Json(0.0, 64.0, 0.0))
                    })
                })
            }
        },
        SourceTypeEntry("spline", "cobblemon.cinematic_editor.source.spline") {
            JsonObject().apply {
                addProperty("type", "spline")
                addProperty("duration", 5f)
                add("controlPoints", JsonArray().apply {
                    add(vec3Json(0.0, 64.0, 0.0))
                    add(vec3Json(10.0, 64.0, 0.0))
                })
            }
        },
        SourceTypeEntry("orbit", "cobblemon.cinematic_editor.source.orbit") {
            JsonObject().apply {
                addProperty("type", "orbit")
                add("center", vec3Json(0.0, 64.0, 0.0))
                addProperty("radius", 10f)
                addProperty("angularVelocity", 30f)
                addProperty("startAngle", 0f)
                addProperty("height", 0f)
            }
        },
        SourceTypeEntry("follow_actor", "cobblemon.cinematic_editor.source.follow_actor") {
            JsonObject().apply {
                addProperty("type", "follow_actor")
                addProperty("actorId", "")
                add("offset", vec3Json(0.0, 2.0, -3.0))
                add("fallback", vec3Json(0.0, 64.0, 0.0))
            }
        },
        SourceTypeEntry("dolly", "cobblemon.cinematic_editor.source.dolly") {
            JsonObject().apply {
                addProperty("type", "dolly")
                add("from", vec3Json(0.0, 64.0, 0.0))
                add("to", vec3Json(10.0, 64.0, 0.0))
                addProperty("duration", 5f)
            }
        },
        SourceTypeEntry("truck", "cobblemon.cinematic_editor.source.truck") {
            JsonObject().apply {
                addProperty("type", "truck")
                add("origin", vec3Json(0.0, 64.0, 0.0))
                add("axis", vec3Json(1.0, 0.0, 0.0))
                addProperty("velocity", 1f)
            }
        },
        SourceTypeEntry("crane", "cobblemon.cinematic_editor.source.crane") {
            JsonObject().apply {
                addProperty("type", "crane")
                add("pivot", vec3Json(0.0, 64.0, 0.0))
                addProperty("armLength", 10f)
                addProperty("startAngle", 0f)
                addProperty("angularVelocity", 30f)
                addProperty("height", 0f)
            }
        },
        SourceTypeEntry("variable_vec3", "cobblemon.cinematic_editor.source.variable_molang_var") {
            JsonObject().apply {
                addProperty("type", "variable_vec3")
                addProperty("prefix", "arena_center")
                add("offset", vec3Json(0.0, 0.0, 0.0))
                add("fallback", vec3Json(0.0, 64.0, 0.0))
            }
        },
        SourceTypeEntry("expression_vec3", "cobblemon.cinematic_editor.source.expression_molang_expr") {
            JsonObject().apply {
                addProperty("type", "expression_vec3")
                addProperty("x", "0.0"); addProperty("y", "64.0"); addProperty("z", "0.0")
                add("fallback", vec3Json(0.0, 64.0, 0.0))
            }
        }
    )

    private val ROTATION_ENTRIES = listOf(
        SourceTypeEntry("fixed", "cobblemon.cinematic_editor.source.fixed_yaw_pitch") {
            JsonObject().apply {
                addProperty("type", "fixed")
                addProperty("yaw", 0f)
                addProperty("pitch", 0f)
            }
        },
        SourceTypeEntry("look_at", "cobblemon.cinematic_editor.source.look_at") {
            JsonObject().apply {
                addProperty("type", "look_at")
                add("target", JsonObject().apply {
                    addProperty("type", "position")
                    addProperty("x", 0.0)
                    addProperty("y", 64.0)
                    addProperty("z", 0.0)
                })
            }
        },
        SourceTypeEntry("keyframe", "cobblemon.cinematic_editor.source.keyframe_rotation") {
            JsonObject().apply {
                addProperty("type", "keyframe")
                add("keyframes", JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("time", 0f); addProperty("yaw", 0f); addProperty("pitch", 0f)
                    })
                })
            }
        },
        SourceTypeEntry("look_along_path", "cobblemon.cinematic_editor.source.look_along_path") {
            JsonObject().apply {
                addProperty("type", "look_along_path")
                addProperty("epsilon", 1f / 60f)
            }
        }
    )

    private val FOV_ENTRIES = listOf(
        SourceTypeEntry("fixed", "cobblemon.cinematic_editor.source.fixed") {
            JsonObject().apply {
                addProperty("type", "fixed")
                addProperty("value", 70f)
            }
        },
        SourceTypeEntry("keyframe", "cobblemon.cinematic_editor.source.keyframe") {
            JsonObject().apply {
                addProperty("type", "keyframe")
                add("keyframes", JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("time", 0f); addProperty("fov", 70f)
                    })
                })
            }
        },
        SourceTypeEntry("expression", "cobblemon.cinematic_editor.source.expression") {
            JsonObject().apply {
                addProperty("type", "expression")
                addProperty("expression", "70.0")
                addProperty("fallback", 70f)
            }
        }
    )

    private val ROLL_ENTRIES = listOf(
        SourceTypeEntry("fixed", "cobblemon.cinematic_editor.source.fixed") {
            JsonObject().apply {
                addProperty("type", "fixed")
                addProperty("value", 0f)
            }
        },
        SourceTypeEntry("keyframe", "cobblemon.cinematic_editor.source.keyframe") {
            JsonObject().apply {
                addProperty("type", "keyframe")
                add("keyframes", JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("time", 0f); addProperty("roll", 0f)
                    })
                })
            }
        },
        SourceTypeEntry("shake", "cobblemon.cinematic_editor.source.shake_derived") {
            JsonObject().apply {
                addProperty("type", "shake")
                addProperty("amplitude", 0f)
                addProperty("frequency", 8f)
                addProperty("decay", 0f)
            }
        }
    )

    private val MODIFIER_ENTRIES = listOf(
        SourceTypeEntry("smoothing", "cobblemon.cinematic_editor.source.smoothing") {
            JsonObject().apply {
                addProperty("type", "smoothing")
                addProperty("position", 0f)
                addProperty("rotation", 0f)
                addProperty("fov", 0f)
            }
        },
        SourceTypeEntry("shake", "cobblemon.cinematic_editor.source.shake") {
            JsonObject().apply {
                addProperty("type", "shake")
                addProperty("positionAmplitude", 0f)
                addProperty("rotationAmplitude", 0f)
                addProperty("frequency", 8f)
                addProperty("decay", 0f)
            }
        },
        SourceTypeEntry("offset", "cobblemon.cinematic_editor.source.offset") {
            JsonObject().apply {
                addProperty("type", "offset")
                add("position", vec3Json(0.0, 0.0, 0.0))
                addProperty("yaw", 0f); addProperty("pitch", 0f)
                addProperty("fov", 0f); addProperty("roll", 0f)
            }
        },
        SourceTypeEntry("constraint", "cobblemon.cinematic_editor.source.constraint") {
            JsonObject().apply {
                addProperty("type", "constraint")
            }
        }
    )

    fun forChannel(channel: SourceChannel): List<SourceTypeEntry> = when (channel) {
        SourceChannel.POSITION -> POSITION_ENTRIES
        SourceChannel.ROTATION -> ROTATION_ENTRIES
        SourceChannel.FOV -> FOV_ENTRIES
        SourceChannel.ROLL -> ROLL_ENTRIES
        SourceChannel.MODIFIER -> MODIFIER_ENTRIES
    }

    fun entryFor(channel: SourceChannel, typeId: String?): SourceTypeEntry? =
        forChannel(channel).firstOrNull { it.typeId == typeId }

    fun defaultJsonFor(channel: SourceChannel, typeId: String): JsonObject =
        entryFor(channel, typeId)?.defaultJson()
            ?: error("No $channel source type '$typeId' in ShotSourceTypes")

    private fun vec3Json(x: Double, y: Double, z: Double): JsonObject = JsonObject().apply {
        addProperty("x", x); addProperty("y", y); addProperty("z", z)
    }
}
