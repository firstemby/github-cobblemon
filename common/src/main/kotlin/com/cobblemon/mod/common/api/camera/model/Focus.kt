/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.camera.model

import com.bedrockk.molang.runtime.MoLangRuntime
import com.bedrockk.molang.runtime.value.MoValue
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.molang.ExpressionLike
import com.cobblemon.mod.common.api.molang.ObjectValue
import com.cobblemon.mod.common.util.asExpressionLike
import com.cobblemon.mod.common.util.resolve
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3

/**
 * Represents where the camera should look.
 */
sealed class CameraFocus {
    abstract fun resolve(
        context: Map<String, MoValue>,
        runtime: MoLangRuntime,
        fallback: Vec3?
    ): Vec3?

    data class Point(val position: PositionExpression) : CameraFocus() {
        override fun resolve(
            context: Map<String, MoValue>,
            runtime: MoLangRuntime,
            fallback: Vec3?
        ): Vec3 {
            return position.toVec3(context, runtime)
        }
    }

    data class Entity(val selector: ExpressionLike) : CameraFocus() {
        override fun resolve(
            context: Map<String, MoValue>,
            runtime: MoLangRuntime,
            fallback: Vec3?
        ): Vec3? {
            return resolveEntity(selector, context, runtime)?.position() ?: fallback
        }
    }

    data class Bone(
        val selector: ExpressionLike,
        val boneName: String,
        val offset: PositionExpression = PositionExpression(
            CoordinateValue.Static(0.0),
            CoordinateValue.Static(0.0),
            CoordinateValue.Static(0.0)
        ),
        val smooth: Boolean = true
    ) : CameraFocus() {
        override fun resolve(
            context: Map<String, MoValue>,
            runtime: MoLangRuntime,
            fallback: Vec3?
        ): Vec3? {
            // Server-side: just resolve the entity position as fallback
            // Client-side bone resolution happens in BoneFocusResolver
            return resolveEntity(selector, context, runtime)?.position() ?: fallback
        }
    }

    companion object {
        /**
         * Resolves a MoLang entity selector to a live Entity, or null if the selector
         * doesn't resolve, the entity is removed, or the entity is dead.
         */
        fun resolveEntity(
            selector: ExpressionLike,
            context: Map<String, MoValue>,
            runtime: MoLangRuntime
        ): net.minecraft.world.entity.Entity? {
            return try {
                val result = runtime.resolve(selector, context)
                if (result is ObjectValue<*>) {
                    val entity = result.obj as? net.minecraft.world.entity.Entity
                    if (entity != null && !entity.isRemoved && entity.isAlive) entity else null
                } else null
            } catch (e: Exception) {
                Cobblemon.LOGGER.error("[CameraFocus] Failed to resolve entity from selector", e)
                null
            }
        }

        fun fromJson(obj: JsonObject): CameraFocus {
            val type = obj.get("type")?.asString
                ?: throw IllegalArgumentException("CameraFocus missing 'type' field")

            return when (type) {
                "entity" -> {
                    val selector = obj.get("selector")?.asString
                        ?: throw IllegalArgumentException("Entity focus missing 'selector' field")
                    Entity(selector.asExpressionLike())
                }
                "position", "point" -> {
                    val posElement = obj.get("position")
                        ?: obj // If no nested position, use the object itself
                    Point(PositionExpression.fromJson(posElement))
                }
                "bone" -> {
                    val selector = obj.get("selector")?.asString
                        ?: throw IllegalArgumentException("Bone focus missing 'selector' field")
                    val boneName = obj.get("bone")?.asString
                        ?: obj.get("boneName")?.asString
                        ?: throw IllegalArgumentException("Bone focus missing 'bone' field")
                    val offset = if (obj.has("offset")) {
                        PositionExpression.fromJson(obj.get("offset"))
                    } else {
                        PositionExpression(
                            CoordinateValue.Static(0.0),
                            CoordinateValue.Static(0.0),
                            CoordinateValue.Static(0.0)
                        )
                    }
                    val smooth = obj.get("smooth")?.asBoolean ?: true

                    Bone(selector.asExpressionLike(), boneName, offset, smooth)
                }
                else -> {
                    throw IllegalArgumentException("Unknown CameraFocus type: $type")
                }
            }
        }
    }
}

/**
 * Position defined by three coordinates (x, y, z), each either static or dynamic.
 */
data class PositionExpression(
    val x: CoordinateValue,
    val y: CoordinateValue,
    val z: CoordinateValue
) {
    fun toVec3(
        context: Map<String, MoValue> = emptyMap(),
        runtime: MoLangRuntime,
        origin: Vec3 = Vec3.ZERO,
        isRelative: Boolean = false
    ): Vec3 {
        val base = Vec3(
            x.evaluate(context, runtime),
            y.evaluate(context, runtime),
            z.evaluate(context, runtime)
        )

        return if (isRelative) base.add(origin) else base
    }

    fun isFinite(context: Map<String, MoValue>, runtime: MoLangRuntime): Boolean {
        val vec = toVec3(context, runtime)
        return vec.x.isFinite() && vec.y.isFinite() && vec.z.isFinite()
    }

    companion object {
        fun fromJson(element: JsonElement): PositionExpression {
            return when {
                element.isJsonObject -> {
                    val obj = element.asJsonObject
                    PositionExpression(
                        x = deserializeCoordinate(obj.get("x")),
                        y = deserializeCoordinate(obj.get("y")),
                        z = deserializeCoordinate(obj.get("z"))
                    )
                }
                element.isJsonArray -> {
                    val arr = element.asJsonArray
                    if (arr.size() != 3) {
                        throw IllegalArgumentException("Position array must have exactly 3 elements [x, y, z], got ${arr.size()}")
                    }
                    PositionExpression(
                        x = deserializeCoordinate(arr[0]),
                        y = deserializeCoordinate(arr[1]),
                        z = deserializeCoordinate(arr[2])
                    )
                }
                else -> {
                    throw IllegalArgumentException("Position must be either an object {x, y, z} or array [x, y, z]")
                }
            }
        }

        private fun deserializeCoordinate(element: JsonElement?): CoordinateValue {
            if (element == null) {
                return CoordinateValue.Static(0.0)
            }

            return when {
                element.isJsonPrimitive && element.asJsonPrimitive.isNumber -> {
                    CoordinateValue.Static(element.asDouble)
                }
                element.isJsonPrimitive && element.asJsonPrimitive.isString -> {
                    // String is treated as expression
                    CoordinateValue.Dynamic(element.asString.asExpressionLike())
                }
                element.isJsonObject -> {
                    val obj = element.asJsonObject
                    val type = obj.get("type")?.asString
                    val expression = obj.get("expression")?.asString

                    when (type) {
                        "expression" -> {
                            if (expression == null) {
                                throw IllegalArgumentException("Expression coordinate missing 'expression' field")
                            }
                            CoordinateValue.Dynamic(expression.asExpressionLike())
                        }
                        else -> {
                            throw IllegalArgumentException("Unknown coordinate type: $type")
                        }
                    }
                }
                else -> {
                    throw IllegalArgumentException("Invalid coordinate value: $element")
                }
            }
        }
    }
}

sealed class CoordinateValue {
    data class Static(val value: Double) : CoordinateValue()
    data class Dynamic(val expression: ExpressionLike) : CoordinateValue()

    fun evaluate(
        context: Map<String, MoValue>,
        runtime: MoLangRuntime
    ): Double {
        return when (this) {
            is Static -> value
            is Dynamic -> {
                try {
                    runtime.resolve(expression, context).asDouble()
                } catch (e: Exception) {
                    // Log error but don't crash - coordinate expressions shouldn't break cameras
                    0.0
                }
            }
        }
    }
}
