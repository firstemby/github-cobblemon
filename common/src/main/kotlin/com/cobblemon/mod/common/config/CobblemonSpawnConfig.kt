/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.config

import com.cobblemon.mod.common.Cobblemon

import com.google.gson.GsonBuilder

import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory

import java.io.File
import java.io.FileReader
import java.io.FileWriter

class CobblemonSpawnConfig(configPath: String) {
  private val file = File(configPath)

  private var blacklistedMobs: MutableList<String> = mutableListOf()

  init {
    if (Cobblemon.config.blacklistVanillaMobs) {
      load()
      save()
    }
  }

  fun load() {
    if (!file.exists()) {
      blacklistedMobs = mutableListOf()
      return
    }

    val reader = FileReader(file)
    blacklistedMobs = GSON.fromJson(reader, blacklistedMobs::class.java)
  }

  fun save() {
    val writer = FileWriter(file)
    GSON.toJson(blacklistedMobs, writer)
    writer.close()
  }

  fun <T: Entity> isBlacklisted(type: EntityType<T>): Boolean {
    // Non-mobs can always spawn
    if (!Cobblemon.config.blacklistVanillaMobs || type.category == MobCategory.MISC) return false

    val location = type.builtInRegistryHolder().key().location()
    return isBlacklisted(location.toString())
  }

  fun isBlacklisted(id: String): Boolean {
    return id.substringBefore(":").equals("minecraft") && blacklistedMobs.contains(id.substringAfter(":"))
  }

  companion object {
    val GSON = GsonBuilder()
      .disableHtmlEscaping()
      .setPrettyPrinting()
      .create()
  }
}