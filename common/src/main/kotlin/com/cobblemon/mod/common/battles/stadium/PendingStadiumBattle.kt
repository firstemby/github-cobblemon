/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.stadium

import com.cobblemon.mod.common.battles.BattleFormat
import com.cobblemon.mod.common.battles.BattleSide
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

/**
 * Holds all data needed to start a stadium battle, allowing the actual
 * battle start to be deferred until a timeline event triggers it.
 */
data class PendingStadiumBattle(
    val battleFormat: BattleFormat,
    val side1: BattleSide,
    val side2: BattleSide,
    val viewers: List<ServerPlayer>,
    val config: StadiumBattleConfig,
    val snapshots: List<EntitySnapshot>,
    val entityA: LivingEntity,
    val entityB: LivingEntity
)

/**
 * Snapshot of an entity's position and rotation before teleportation,
 * used to restore state if the battle fails to start.
 */
data class EntitySnapshot(
    val entity: LivingEntity,
    val level: ServerLevel,
    val position: Vec3,
    val yaw: Float,
    val pitch: Float
)
