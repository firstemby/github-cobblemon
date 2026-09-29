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
import com.cobblemon.mod.common.api.molang.ExpressionLike
import com.cobblemon.mod.common.util.asExpressionLike
import com.cobblemon.mod.common.util.resolve
import com.google.gson.JsonElement
import net.minecraft.world.phys.Vec3

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
    data class Static(val value: Double) : CoordinateValue() {
        init { require(value.isFinite()) { "Static coordinate must be finite" } }
    }
    data class Dynamic(val expression: ExpressionLike) : CoordinateValue()

    fun evaluate(
        context: Map<String, MoValue>,
        runtime: MoLangRuntime
    ): Double {
        return when (this) {
            is Static -> value
            is Dynamic -> runtime.resolve(expression, context).asDouble().also {
                require(it.isFinite()) { "Dynamic coordinate must evaluate to a finite value" }
            }
        }
    }
}
