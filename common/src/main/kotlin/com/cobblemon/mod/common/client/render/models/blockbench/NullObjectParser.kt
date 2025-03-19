/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.models.blockbench

/**
 * Uses the name of null objects in a model to get modifier values for a locator.
 * @author Josh
 */
object NullObjectParser {
    fun parseNullObject(input: String): NullObjectModifier {
        // Extract modifiedLocator
        val modifiedLocator = input.replaceFirst("^_null_".toRegex(), "").replace("\\[.*\\]$".toRegex(), "")

        // Extract modifiers
        val modifiers: MutableMap<String, Float> = HashMap()
        val start = input.indexOf('[')
        val end = input.indexOf(']')

        if (start != -1 && end != -1 && end > start) {
            val modifierPart = input.substring(start + 1, end)
            val modifierPairs = modifierPart.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

            for (pair in modifierPairs) {
                val keyValue = pair.split("=".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                if (keyValue.size == 2) {
                    val key = keyValue[0].trim { it <= ' ' }
                    try {
                        val value = keyValue[1].trim { it <= ' ' }.toFloat()
                        modifiers[key] = value
                    } catch (e: NumberFormatException) {
                        System.err.println("Invalid number format for: " + keyValue[1])
                    }
                }
            }
        }

        return NullObjectModifier(modifiedLocator, modifiers)
    }

    /**
     * Stores modifiers for a particular locator.
     */
    class NullObjectModifier(val locatorName: String, val modifiers: Map<String, Float>)
}