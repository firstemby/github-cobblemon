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
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager

object PCBoxWallpaperRepository {
    lateinit var allWallpapers: Set<Pair<ResourceLocation, ResourceLocation?>>
    lateinit var availableWallpapers: MutableSet<ResourceLocation>
    val defaultWallpaper = cobblemonResource("textures/gui/pc/wallpaper/wallpaper_basic_05.png")

    fun findWallpapers(resourceManager: ResourceManager) {
        // Wallpaper resource and glow resource if available as a pair
        val resources = mutableListOf<Pair<ResourceLocation, ResourceLocation?>>()
        val wallpapers = mutableListOf<ResourceLocation>()

        val wallpaperPathList = mutableListOf<Pair<String, ResourceLocation>>()
        val wallpaperGlowPathList = mutableListOf<Pair<String, ResourceLocation>>()

        resourceManager.listResources("textures/gui/pc/wallpaper") { path -> path.endsWith(".png") }.keys.forEach { filePath ->
            val splitPath = filePath.toString().split("/")
            val fileName = splitPath[splitPath.lastIndex]
            if (splitPath[splitPath.lastIndex - 1] == "glow") {
                wallpaperGlowPathList.add(Pair(fileName, filePath))
            } else {
                wallpaperPathList.add(Pair(fileName, filePath))
            }
        }

        for (resource in wallpaperPathList) {
            // Find matching glow resource if available
            val glowResource = wallpaperGlowPathList.find { it.first == resource.first }
            resources.add(Pair(resource.second, glowResource?.second))
            wallpapers.add(resource.second)
        }

        allWallpapers = resources.toSet()
        availableWallpapers = wallpapers.toMutableSet()
    }
}