/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.events.habitats

import com.cobblemon.mod.common.api.events.Cancelable
import com.cobblemon.mod.common.api.habitats.dto.HabitatSettingsDTO
import com.cobblemon.mod.common.block.habitat.HabitatBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer

interface SaveHabitatBlockSettingsEvent {
    val player: ServerPlayer
    val world: ServerLevel
    val blockPos: BlockPos
    val habitatBlockEntity: HabitatBlockEntity
    val settings: HabitatSettingsDTO

    class Pre(
        override val player: ServerPlayer,
        override val world: ServerLevel,
        override val blockPos: BlockPos,
        override val habitatBlockEntity: HabitatBlockEntity,
        override val settings: HabitatSettingsDTO
    ) : SaveHabitatBlockSettingsEvent, Cancelable()

     class Post(
        override val player: ServerPlayer,
        override val world: ServerLevel,
        override val blockPos: BlockPos,
        override val habitatBlockEntity: HabitatBlockEntity,
        override val settings: HabitatSettingsDTO
    ) : SaveHabitatBlockSettingsEvent
}