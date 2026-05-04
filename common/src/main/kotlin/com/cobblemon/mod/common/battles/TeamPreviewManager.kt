/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles

import com.cobblemon.mod.common.CobblemonNetwork.sendPacket
import com.cobblemon.mod.common.net.messages.client.battle.CloseTeamPreviewPacket
import com.cobblemon.mod.common.net.messages.client.battle.TeamPreviewPacket
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.getPlayer
import com.cobblemon.mod.common.util.party
import com.cobblemon.mod.common.util.safeParty
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import java.time.Duration
import java.time.Instant
import java.util.UUID

object TeamPreviewManager {
    private val previews = mutableListOf<TeamPreview>()
    val TEAM_PREVIEW_TIME_SECONDS = 60

    fun tick() {
        val expired = previews.filter { Duration.between(it.started, Instant.now()).seconds >= TEAM_PREVIEW_TIME_SECONDS }
        expired.forEach { cancel(it) }

        val ready = previews.filter {
            hasEligibleSelections(it.sideA) && hasEligibleSelections(it.sideB)
        }
        ready.forEach { start(it) }
    }

    fun add(sideA: TeamPreviewSide, sideB: TeamPreviewSide, openTeamSheet: Boolean, onStart: (TeamPreview) -> Unit, onCancel: (TeamPreview) -> Unit) {
        if (previews.any { it.sideA.uuid == sideA.uuid || it.sideB.uuid == sideA.uuid || it.sideA.uuid == sideB.uuid || it.sideB.uuid == sideB.uuid }) return
        val now = Instant.now()
        previews.add(TeamPreview(sideA, sideB, now, onStart, onCancel))
        if (sideA.isPlayer) {
            sideA.uuid.getPlayer()?.sendPacket(
                TeamPreviewPacket(
                    sideA.neededSelections,
                    sideA.party,
                    sideB.party,
                    openTeamSheet,
                    now.plusSeconds(TEAM_PREVIEW_TIME_SECONDS.toLong()),
                    sideB.name
                )
            )
        }
        if (sideB.isPlayer) {
            sideB.uuid.getPlayer()?.sendPacket(
                TeamPreviewPacket(
                    sideB.neededSelections,
                    sideB.party,
                    sideA.party,
                    openTeamSheet,
                    now.plusSeconds(TEAM_PREVIEW_TIME_SECONDS.toLong()),
                    sideA.name
                )
            )
        }
    }

    fun remove(player: ServerPlayer) {
        val preview = previews.firstOrNull { it.sideA.isPlayer && it.sideA.uuid == player.uuid || it.sideB.isPlayer && it.sideB.uuid == player.uuid } ?: return
        cancel(preview)
    }

    fun cancel(preview: TeamPreview) {
        preview.onCancel(preview)
        if (preview.sideA.isPlayer) {
            preview.sideA.uuid.getPlayer()?.sendPacket(CloseTeamPreviewPacket())
        }
        if (preview.sideB.isPlayer) {
            preview.sideB.uuid.getPlayer()?.sendPacket(CloseTeamPreviewPacket())
        }
        previews.remove(preview)
    }

    fun start(preview: TeamPreview) {
        preview.onStart(preview)
        if (preview.sideA.isPlayer) {
            preview.sideA.uuid.getPlayer()?.sendPacket(CloseTeamPreviewPacket())
        }
        if (preview.sideB.isPlayer) {
            preview.sideB.uuid.getPlayer()?.sendPacket(CloseTeamPreviewPacket())
        }
        previews.remove(preview)
    }

    fun changeSelections(uuid: UUID, selections: Set<Int>) {
        val preview = previews.firstOrNull { it.sideA.uuid == uuid || it.sideB.uuid == uuid } ?: return
        val isSideA = preview.sideA.uuid == uuid
        previews.remove(preview)
        if (isSideA) {
            previews.add(
                preview.copy(
                    sideA = preview.sideA.copy(selections = selections),
                )
            )
        }
        else {
            previews.add(
                preview.copy(
                    sideB = preview.sideB.copy(selections = selections),
                )
            )
        }
    }

    fun hasEligibleSelections(side: TeamPreviewSide): Boolean {
        if (side.selections.size < side.neededSelections) return false
        if (side.selections.any { side.party.size <= it || side.party[it] == null }) return false
        return true
    }
}

data class TeamPreview(
    val sideA: TeamPreviewSide,
    val sideB: TeamPreviewSide,
    val started: Instant,
    val onStart: (TeamPreview) -> Unit,
    val onCancel: (TeamPreview) -> Unit
)

data class TeamPreviewSide(
    val uuid: UUID,
    val name: Component,
    val isPlayer: Boolean,
    val party: List<Pokemon?>,
    val selections: Set<Int>,
    val neededSelections: Int
) {
    constructor(player: ServerPlayer, neededSelections: Int): this(player.uuid, player.name, true, player.party().toGappyList(), emptySet(), neededSelections)
    constructor(pokemon: Pokemon, name: Component? = null): this(pokemon.uuid, name ?: pokemon.getDisplayName(), false, listOf(null, null, pokemon, null, null, null, null), setOf(2), 1)
}