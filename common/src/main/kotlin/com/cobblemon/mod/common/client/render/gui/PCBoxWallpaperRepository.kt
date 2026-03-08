/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.gui

import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.endsWith
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.ResourceManager

object PCBoxWallpaperRepository {
    lateinit var allWallpapers: Set<Triple<Identifier, Identifier?, Identifier?>>
    lateinit var availableWallpapers: MutableSet<Identifier>
    val defaultWallpaper = cobblemonResource("textures/gui/pc/wallpaper/basic/wallpaper_basic_05.png")

    fun findWallpapers(resourceManager: ResourceManager) {
        // Wallpaper resource, alternate wallpaper resource, and glow resource, if available as a triple
        val resources = mutableListOf<Triple<Identifier, Identifier?, Identifier?>>()
        val wallpapers = mutableListOf<Identifier>()

        val wallpaperPathList = mutableListOf<Pair<String, Identifier>>()
        val altWallpaperPathList = mutableListOf<Pair<String, Identifier>>()
        val wallpaperGlowPathList = mutableListOf<Pair<String, Identifier>>()

        resourceManager.listResources("textures/gui/pc/wallpaper") { path -> path.endsWith(".png") }.keys.forEach { filePath ->
            val splitPath = filePath.toString().split("/")
            val fileName = splitPath[splitPath.lastIndex]
            val directory = splitPath[splitPath.lastIndex - 1]
            if (directory == "glow") {
                wallpaperGlowPathList.add(Pair(fileName, filePath))
            } else if (directory == "alt") {
                altWallpaperPathList.add(Pair(fileName, filePath))
            } else {
                wallpaperPathList.add(Pair(fileName, filePath))
            }
        }

        for (resource in wallpaperPathList) {
            // Find matching alternate wallpaper resource and glow resource if available
            val glowResource = wallpaperGlowPathList.find { it.first == resource.first }
            val altResource = altWallpaperPathList.find { it.first == resource.first }
            resources.add(Triple(resource.second, altResource?.second, glowResource?.second))
            wallpapers.add(resource.second)
        }

        allWallpapers = resources.toSet()
        availableWallpapers = wallpapers.toMutableSet()
    }
}
