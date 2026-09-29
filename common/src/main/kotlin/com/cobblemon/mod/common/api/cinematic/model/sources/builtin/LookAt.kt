/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.model.sources.builtin

import com.bedrockk.molang.runtime.MoLangRuntime
import com.cobblemon.mod.common.api.camera.CameraRotation
import com.cobblemon.mod.common.api.cinematic.model.sources.RotationSource
import com.cobblemon.mod.common.api.molang.ExpressionLike
import com.cobblemon.mod.common.util.asExpressionLike
import com.google.gson.JsonObject
import net.minecraft.world.phys.Vec3
import kotlin.math.atan2
import kotlin.math.sqrt

sealed class LookAtTarget {
    data class Position(val pos: Vec3) : LookAtTarget() {
        init {
            require(pos.x.isFinite() && pos.y.isFinite() && pos.z.isFinite()) { "Look-at position must be finite" }
        }
    }

    data class Actor(val actorId: String) : LookAtTarget() {
        init { require(actorId.isNotBlank()) { "Look-at actor id must be non-blank" } }
    }
    data class Actors(val actorIds: List<String>) : LookAtTarget() {
        init {
            require(actorIds.size in 1..16)
            require(actorIds.all(String::isNotBlank)) { "Look-at actor ids must be non-blank" }
        }
    }

    data class Variable(val prefix: String, val fallback: Vec3 = Vec3.ZERO) : LookAtTarget() {
        @Transient private val position = VariableVec3Position(prefix, fallback = fallback)

        fun evaluate(runtime: MoLangRuntime?): Vec3 = position.evaluate(runtime)
    }

    data class Expression(
        val xExpression: ExpressionLike,
        val yExpression: ExpressionLike,
        val zExpression: ExpressionLike,
        val fallback: Vec3 = Vec3.ZERO
    ) : LookAtTarget() {
        @Transient private val position = ExpressionVec3Position(xExpression, yExpression, zExpression, fallback)

        fun evaluate(runtime: MoLangRuntime?): Vec3 = position.evaluate(runtime)
    }

    /** Bones resolve client-side; offsets are in world coordinates. */
    data class ActorBone(
        val actorId: String,
        val boneName: String,
        val offset: Vec3 = Vec3.ZERO,
        val smooth: Boolean = true
    ) : LookAtTarget() {
        init {
            require(actorId.isNotBlank()) { "Look-at bone actor id must be non-blank" }
            require(boneName.isNotBlank()) { "Look-at bone name must be non-blank" }
            require(offset.x.isFinite() && offset.y.isFinite() && offset.z.isFinite()) { "Look-at bone offset must be finite" }
        }
    }
}

data class LookAt(
    val target: LookAtTarget
) : RotationSource() {
    override val type: String = "look_at"

    fun computeRotation(cameraPos: Vec3, targetPos: Vec3): CameraRotation {
        val dx = targetPos.x - cameraPos.x
        val dy = targetPos.y - cameraPos.y
        val dz = targetPos.z - cameraPos.z
        val horizontal = sqrt(dx * dx + dz * dz)
        val yaw = Math.toDegrees(atan2(-dx, dz)).toFloat()
        val pitch = Math.toDegrees(-atan2(dy, horizontal)).toFloat()
        return CameraRotation(yaw = yaw, pitch = pitch)
    }

    companion object {
        const val TYPE = "look_at"

        fun fromJson(obj: JsonObject): LookAt {
            val targetObj = obj.getAsJsonObject("target")
                ?: throw IllegalArgumentException("LookAt missing 'target'")
            val targetType = targetObj.get("type")?.asString ?: "position"
            val target = when (targetType) {
                "actors" -> LookAtTarget.Actors(targetObj.getAsJsonArray("actorIds").map { it.asString })
                "position" -> LookAtTarget.Position(
                    Vec3(
                        targetObj.get("x").asDouble,
                        targetObj.get("y").asDouble,
                        targetObj.get("z").asDouble
                    )
                )
                "actor" -> LookAtTarget.Actor(
                    targetObj.get("actorId")?.asString
                        ?: throw IllegalArgumentException("LookAt actor target missing 'actorId'")
                )
                "actor_bone" -> {
                    val actorId = targetObj.get("actorId")?.asString
                        ?: throw IllegalArgumentException("LookAt actor_bone target missing 'actorId'")
                    val boneName = targetObj.get("bone")?.asString
                        ?: throw IllegalArgumentException("LookAt actor_bone target missing 'bone'")
                    val off = targetObj.getAsJsonObject("offset")
                    val offset = if (off != null) {
                        Vec3(off.get("x").asDouble, off.get("y").asDouble, off.get("z").asDouble)
                    } else Vec3.ZERO
                    val smooth = targetObj.get("smooth")?.asBoolean ?: true
                    LookAtTarget.ActorBone(actorId, boneName, offset, smooth)
                }
                "variable" -> {
                    val prefix = targetObj.get("prefix")?.asString
                        ?: throw IllegalArgumentException("LookAt variable target missing 'prefix'")
                    val fb = targetObj.getAsJsonObject("fallback")
                    val fallback = if (fb != null) {
                        Vec3(fb.get("x").asDouble, fb.get("y").asDouble, fb.get("z").asDouble)
                    } else Vec3.ZERO
                    LookAtTarget.Variable(prefix, fallback)
                }
                "expression" -> {
                    val x = targetObj.get("x")?.asString
                        ?: throw IllegalArgumentException("LookAt expression target missing 'x'")
                    val y = targetObj.get("y")?.asString
                        ?: throw IllegalArgumentException("LookAt expression target missing 'y'")
                    val z = targetObj.get("z")?.asString
                        ?: throw IllegalArgumentException("LookAt expression target missing 'z'")
                    val fb = targetObj.getAsJsonObject("fallback")
                    val fallback = if (fb != null) {
                        Vec3(fb.get("x").asDouble, fb.get("y").asDouble, fb.get("z").asDouble)
                    } else Vec3.ZERO
                    LookAtTarget.Expression(
                        xExpression = x.asExpressionLike(),
                        yExpression = y.asExpressionLike(),
                        zExpression = z.asExpressionLike(),
                        fallback = fallback
                    )
                }
                else -> throw IllegalArgumentException("LookAt target unknown type '$targetType'")
            }
            return LookAt(target)
        }
    }
}
