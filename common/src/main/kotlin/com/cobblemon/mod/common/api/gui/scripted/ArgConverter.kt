/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.gui.scripted

import org.graalvm.polyglot.Value
import org.graalvm.polyglot.proxy.ProxyObject

/**
 * Converts between wire format (Map<String, String>) and GraalJS types.
 */
object ArgConverter {

    /**
     * Converts a wire-format argument map to a ProxyObject for use in JS.
     * Numbers are auto-detected and converted to Double.
     */
    fun toJsObject(args: Map<String, String>, parameters: Map<String, ScriptedGuiParameter> = emptyMap()): ProxyObject {
        val map = mutableMapOf<String, Any?>()
        for ((key, value) in args) {
            val paramDef = parameters[key]
            map[key] = when (paramDef?.type) {
                "number" -> value.toDoubleOrNull() ?: value
                "string" -> value
                else -> value.toDoubleOrNull() ?: value
            }
        }
        return object : ProxyObject {
            override fun getMember(key: String): Any? = map[key]
            override fun getMemberKeys(): Any = map.keys
            override fun hasMember(key: String): Boolean = key in map
            override fun putMember(key: String, value: Value?) { map[key] = value }
        }
    }

    /**
     * Merges partial updates into an existing argument map.
     */
    fun mergeArgs(existing: Map<String, String>, updates: Map<String, String>): Map<String, String> {
        return existing + updates
    }
}
