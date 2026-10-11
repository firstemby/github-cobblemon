/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.battle

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Tracks client-side raid battle state for multi-player raid battles.
 * Provides targeting overrides and gimmick tracking for the raid4v1 format.
 */
object ClientRaidState {

    @JvmField
    @Volatile
    var isInRaidBattle = false

    @JvmField
    @Volatile
    var currentRaidBattleId: UUID? = null

    @JvmField
    var moveSelectionDelayTicks: Int = 60

    private val pendingGimmicks = ConcurrentHashMap<UUID, MutableSet<String>>()

    private data class ScheduledTask(
        val executeAtTick: Long,
        val task: Runnable
    )

    private val pendingTasks = ConcurrentLinkedQueue<ScheduledTask>()
    private var currentTick: Long = 0
    private var schedulerInitialized = false

    /**
     * Initialize the client-side scheduler for delayed tasks.
     */
    @JvmStatic
    fun initializeScheduler() {
        if (schedulerInitialized) return
        schedulerInitialized = true

        ClientTickEvents.END_CLIENT_TICK.register { _ ->
            onClientTick()
        }
    }

    /**
     * Schedule a task to run after a delay in ticks.
     */
    @JvmStatic
    fun schedule(delayTicks: Int, task: Runnable) {
        val executeAt = currentTick + delayTicks
        pendingTasks.add(ScheduledTask(executeAt, task))
    }

    /**
     * Schedule a task to run after the configured move selection delay.
     */
    @JvmStatic
    fun scheduleWithMoveDelay(task: Runnable) {
        schedule(moveSelectionDelayTicks, task)
    }

    /**
     * Cancel all pending tasks.
     */
    @JvmStatic
    fun cancelAllTasks() {
        pendingTasks.clear()
    }

    private fun onClientTick() {
        currentTick++

        val iterator = pendingTasks.iterator()
        while (iterator.hasNext()) {
            val scheduledTask = iterator.next()
            if (currentTick >= scheduledTask.executeAtTick) {
                iterator.remove()
                try {
                    Minecraft.getInstance().execute(scheduledTask.task)
                } catch (e: Exception) {
                    // Silently ignore task execution failures
                }
            }
        }
    }

    /**
     * Mark a gimmick as used for this turn in the given battle.
     */
    @JvmStatic
    fun markGimmickUsed(battleId: UUID, gimmickId: String) {
        pendingGimmicks.computeIfAbsent(battleId) { mutableSetOf() }.add(gimmickId)
    }

    /**
     * Get all gimmicks used this turn for a battle.
     */
    @JvmStatic
    fun getUsedGimmicks(battleId: UUID): Set<String> {
        return pendingGimmicks[battleId]?.toSet() ?: emptySet()
    }

    /**
     * Clear all pending gimmicks for a battle.
     */
    @JvmStatic
    fun clearGimmicksForBattle(battleId: UUID) {
        pendingGimmicks.remove(battleId)
    }

    /**
     * Clear all gimmick tracking data.
     */
    @JvmStatic
    fun clearAllGimmicks() {
        pendingGimmicks.clear()
    }

    /**
     * Clear all raid state. Called when battle ends or player disconnects.
     */
    @JvmStatic
    fun clearRaidState() {
        isInRaidBattle = false
        currentRaidBattleId = null
        cancelAllTasks()
        clearAllGimmicks()
    }
}
