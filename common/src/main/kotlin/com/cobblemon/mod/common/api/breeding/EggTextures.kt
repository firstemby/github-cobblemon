/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.breeding

import com.cobblemon.mod.common.util.asIdentifierDefaultingNamespace
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager

/**
 * Loads in all of the model JSONs that fit the egg texture format (models/item/egg/{something}_egg.json) and provides a
 * method of retrieving the logical texture based on an identifier and variant. A poor man's variation JSON.
 *
 * @author Hiroku
 * @since April 8th, 2026
 */
object EggTextures {
    val eggTextures = mutableMapOf<ResourceLocation, MutableMap<String, ResourceLocation>>()

    fun reload(resourceManager: ResourceManager) {
        resourceManager.listResources("models/item/egg") { true }.forEach { location, _ ->
            // The identifier will be the first part of the path after models/item/egg/, and all paths end with _egg.json,
            // and the last term before _egg.json separated by underscore is the 'variant'
            val path = location.toString().replace("models/item/egg/", "" ).replace("_egg.json", "")
            val (identifier, variant) = if (path.contains("_")) {
                val parts = path.split("_")
                val variantPart = parts.last()
                val identifierPart = parts.dropLast(1).joinToString("_")
                identifierPart.asIdentifierDefaultingNamespace() to variantPart.replace("-", "")
            } else {
                path.asIdentifierDefaultingNamespace() to "default"
            }

            val suffix = if (variant == "default") "" else "_$variant"

            val eggTextures = eggTextures.getOrPut(identifier.withSuffix("_egg")) { mutableMapOf<String, ResourceLocation>() }
            eggTextures[variant] = location.withPath("egg/${identifier.path}${suffix}_egg")
        }
    }

    @JvmStatic
    fun getEggTexture(identifier: ResourceLocation, variant: String = "default") : ResourceLocation {
        val fallback = cobblemonResource("egg/mystery_egg")
        val eggTextures = eggTextures[identifier] ?: return fallback
        return eggTextures[variant] ?: eggTextures["default"] ?: fallback
    }
}