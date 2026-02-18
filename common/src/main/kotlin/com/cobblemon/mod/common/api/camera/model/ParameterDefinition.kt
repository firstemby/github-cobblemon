/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.camera.model

/**
 * Defines a parameter that can be passed to a camera timeline.
 *
 * Parameters allow timelines to be reusable with different values.
 * For example, an arena camera can accept `arena_center` as a Vec3 parameter
 * to position the camera relative to any arena location.
 *
 * @property type The type of the parameter (NUMBER or VEC3)
 * @property description Optional description for documentation
 * @property default Optional default value. If null, the parameter is required.
 */
data class ParameterDefinition(
    val type: ParameterType,
    val description: String? = null,
    val default: ParameterDefault? = null
) {
    /**
     * Whether this parameter must be provided when starting the timeline.
     * Parameters with defaults are optional.
     */
    val isRequired: Boolean get() = default == null

    /**
     * Gets the variable names this parameter will inject into the MoLang runtime.
     * - NUMBER type: returns just the parameter name
     * - VEC3 type: returns name_x, name_y, name_z
     */
    fun getVariableNames(paramName: String): List<String> {
        return when (type) {
            ParameterType.NUMBER -> listOf(paramName)
            ParameterType.VEC3 -> listOf("${paramName}_x", "${paramName}_y", "${paramName}_z")
        }
    }
}

/**
 * Types of parameters supported by camera timelines.
 */
enum class ParameterType {
    /**
     * A single numeric value (Double).
     * Accessible in MoLang as `variable.param_name`
     */
    NUMBER,

    /**
     * A 3D vector value.
     * Expands to three variables in MoLang:
     * - `variable.param_name_x`
     * - `variable.param_name_y`
     * - `variable.param_name_z`
     */
    VEC3
}

/**
 * Default value for a parameter.
 */
sealed class ParameterDefault {
    /**
     * Default value for a NUMBER parameter.
     */
    data class NumberDefault(val value: Double) : ParameterDefault()

    /**
     * Default value for a VEC3 parameter.
     */
    data class Vec3Default(val x: Double, val y: Double, val z: Double) : ParameterDefault()
}
