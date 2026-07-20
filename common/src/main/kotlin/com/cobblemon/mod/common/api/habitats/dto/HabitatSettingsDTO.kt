/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.habitats.dto

import com.cobblemon.mod.common.api.habitats.HabitatPhaseOrder
import com.cobblemon.mod.common.api.habitats.HabitatPool
import com.cobblemon.mod.common.api.habitats.spawningstyle.ActivatedHabitatSpawning
import com.cobblemon.mod.common.api.habitats.spawningstyle.NaturalHabitatSpawning
import com.cobblemon.mod.common.block.habitat.HabitatBlockEntity
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

class HabitatSettingsDTO() {
    var phaseOrder = HabitatPhaseOrder.SIMPLE
    lateinit var levelRange: IntRange
    var modifiers: String = ""
    lateinit var mimicId: ResourceLocation
    var isActivatedSpawning = false
    var pool: HabitatPoolSettingsDTO = HabitatPoolSettingsDTO(HabitatPool.default())
    var activatedSettings: ActivatedHabitatSettingsDTO? = null
    var naturalSettings: NaturalHabitatSettingsDTO? = null

    constructor(habitatBlockEntity: HabitatBlockEntity) : this() {
        this.phaseOrder = habitatBlockEntity.phaseOrder
        this.levelRange = habitatBlockEntity.levelRange
        this.modifiers = habitatBlockEntity.modifiers.asString()
        this.mimicId = habitatBlockEntity.mimicId
        this.pool = HabitatPoolSettingsDTO(habitatBlockEntity.pool)
        val style = habitatBlockEntity.spawningStyle
        if (style is ActivatedHabitatSpawning) {
            this.isActivatedSpawning = true
            this.activatedSettings = ActivatedHabitatSettingsDTO(style)
        } else if (style is NaturalHabitatSpawning) {
            this.naturalSettings = NaturalHabitatSettingsDTO(style)
        }
    }

    fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeEnum(phaseOrder)
        buffer.writeInt(levelRange.first)
        buffer.writeInt(levelRange.last)
        buffer.writeUtf(modifiers)
        buffer.writeResourceLocation(mimicId)
        pool.encode(buffer)
        buffer.writeBoolean(isActivatedSpawning)
        if (isActivatedSpawning) {
            activatedSettings?.encode(buffer)
        } else {
            naturalSettings?.encode(buffer)
        }
    }

    fun decode(buffer: RegistryFriendlyByteBuf) {
        this.phaseOrder = buffer.readEnum(HabitatPhaseOrder::class.java)
        val levelRangeStart = buffer.readInt()
        val levelRangeEnd = buffer.readInt()
        this.levelRange = levelRangeStart..levelRangeEnd
        this.modifiers = buffer.readUtf()
        this.mimicId = buffer.readResourceLocation()
        this.pool = HabitatPoolSettingsDTO()
        this.pool.decode(buffer)
        if (buffer.readBoolean()) {
            this.isActivatedSpawning = true
            this.naturalSettings = null
            val activatedSettings = ActivatedHabitatSettingsDTO()
            activatedSettings.decode(buffer)
            this.activatedSettings = activatedSettings
        } else {
            this.activatedSettings = null
            val naturalSettings = NaturalHabitatSettingsDTO()
            naturalSettings.decode(buffer)
            this.naturalSettings = naturalSettings
        }
    }
}