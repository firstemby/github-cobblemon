/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.tooltips

import com.cobblemon.mod.common.api.cooking.Flavour
import com.cobblemon.mod.common.api.text.*
import com.cobblemon.mod.common.util.lang
import net.minecraft.network.chat.Component

val seasoningHeader by lazy { lang("item_class.seasoning").blue() }
private val flavorSubHeader by lazy { lang("seasoning_flavor_header").blue() }

fun generateAdditionalFlavorTooltip(flavours: Map<Flavour, Int>): MutableList<Component> {
    val resultLines = mutableListOf<Component>()
    resultLines.add(flavorSubHeader)

    val combinedFlavorsLine = Component.literal("")
    flavours.filter { it.key != Flavour.MILD }.forEach { (flavour, value) ->
        var flavourText = lang("seasoning_flavor.${flavour.name.lowercase()}").withStyle(flavour.chatFormatting)

        if (combinedFlavorsLine.string.isNotEmpty()) {
            combinedFlavorsLine.append(" ")
        }

        combinedFlavorsLine.append(flavourText).append(" $value")
    }

    resultLines.add(combinedFlavorsLine)
    return resultLines
}