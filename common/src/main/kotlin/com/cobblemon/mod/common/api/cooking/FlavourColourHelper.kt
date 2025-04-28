/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cooking

import net.minecraft.util.FastColor
import net.minecraft.world.item.ItemStack

private val colourMap = mapOf(
    Flavour.SPICY to 0xFEB37D,
    Flavour.DRY to 0x8AE9FC,
    Flavour.SWEET to 0xFFBEED,
    Flavour.BITTER to 0x9EED8F,
    Flavour.SOUR to 0xFCF38A
)

private val bubbleColourMap = mapOf(
    Flavour.SPICY to 0xFFD9AD,
    Flavour.DRY to 0xBCF8FE,
    Flavour.SWEET to 0xFEE3F9,
    Flavour.BITTER to 0xC8F7BC,
    Flavour.SOUR to 0xFDFAB8
)

fun getColourMixFromSeasonings(seasonings: List<ItemStack>, forBubbles: Boolean = false): Int? {
    val flavors = seasonings
        .flatMap { Seasonings.getFlavoursFromItemStack(it).entries }
        .groupingBy { it.key }
        .fold(0) { acc, entry -> acc + entry.value }

    val maxFlavorValue = flavors.values.maxOrNull()
    val dominantFlavors = flavors.filter { it.value == maxFlavorValue }.map { it.key }

    return getColourMixFromFlavours(dominantFlavors, forBubbles)
}

fun getColourMixFromFlavours(dominantFlavours: List<Flavour>, forBubbles: Boolean = false): Int? {
    val colors =
        dominantFlavours.mapNotNull { if (forBubbles) bubbleColourMap[it] else colourMap[it] }
            .map { FastColor.ARGB32.opaque(it) }

    if (colors.isEmpty()) return null

    val (alphaSum, redSum, greenSum, blueSum) = colors.fold(IntArray(4)) { acc, color ->
        acc[0] += FastColor.ARGB32.alpha(color)
        acc[1] += FastColor.ARGB32.red(color)
        acc[2] += FastColor.ARGB32.green(color)
        acc[3] += FastColor.ARGB32.blue(color)
        acc
    }

    return FastColor.ARGB32.color(
        alphaSum / colors.size,
        redSum / colors.size,
        greenSum / colors.size,
        blueSum / colors.size
    )
}