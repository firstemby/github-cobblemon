/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.camera.model

data class ParameterDefinition(
    val type: ParameterType,
    val description: String? = null,
    val default: ParameterDefault? = null
)

enum class ParameterType {
    NUMBER,
    VEC3,
    STRING
}

sealed class ParameterDefault {
    data class NumberDefault(val value: Double) : ParameterDefault()
    data class Vec3Default(val x: Double, val y: Double, val z: Double) : ParameterDefault()
    data class StringDefault(val value: String) : ParameterDefault()
}
