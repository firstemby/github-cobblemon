/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain

import com.bedrockk.molang.runtime.MoLangRuntime
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonNetwork.sendPacket
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.Distraction
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.EntityFixation
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.Objective
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.ObjectiveStatus
import com.cobblemon.mod.common.api.ai.biggerbrain.sense.Sense
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMostSpecificMoLangValue
import com.cobblemon.mod.common.api.molang.MoLangFunctions.setup
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.net.messages.client.debug.UpdateTrackedBrainDataPacket
import com.cobblemon.mod.common.util.getMemorySafely
import com.cobblemon.mod.common.util.getPlayer
import com.cobblemon.mod.common.util.withQueryValue
import java.util.UUID
import kotlin.collections.mutableListOf
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.MemoryStatus

/**
 * A better* version of Minecraft's [net.minecraft.world.entity.ai.Brain] system that makes development more
 * straightforward and allows easier debugging.
 *
 * We could name this thing better.
 *
 * There are three principles inside this system:
 *
 * 1: Senses
 *
 * Senses are simple ticking objects that are instantiated per entity and are expected to just update memories based
 * on properties about the entity or its surroundings. This is an analog of SensorType from Brains.
 *
 * 2: Objectives
 *
 * Objectives are also ticking objects that are instantiated per entity. They represent a goal for the entity and
 * run in a fixed order.
 *
 * 3: Fixations
 *
 * Fixations are a task that can be set on the brain that represents a more total commitment to a certain task. Only
 * a single fixation can be active at a time, and they can be 'distracted' by other objectives or potentially external
 * factors.
 *
 * These can also be debugged using the /debugcobblemonai command while looking at a Pokémon or NPC entity.
 *
 * *that remains to be seen
 *
 * @author Hiroku
 * @since December 29th, 2025
 */
class BiggerBrain(val entity: LivingEntity) {
    val runtime = MoLangRuntime().setup().withQueryValue("entity", entity.asMostSpecificMoLangValue())

    val senses = mutableListOf<Sense>()
    val objectives = mutableListOf<Objective>()
    val observedMemories = mutableMapOf<MemoryModuleType<*>, MutableList<Objective>>()

    var fixation: EntityFixation? = null

    var debuggingPlayers = mutableSetOf<UUID>()

    fun registerObjective(objective: Objective, observedMemories: List<MemoryModuleType<*>> = emptyList()) {
        objectives.add(objective)
        for (memory in observedMemories) {
            val list = this.observedMemories.getOrPut(memory, ::ArrayList)
            list.add(objective)
        }
    }

    fun isFixation(fixation: EntityFixation?): Boolean {
        return fixation != null && this.fixation == fixation
    }

    fun sendDebugMessage(message: String) {
        debuggingPlayers.forEach {
            it.getPlayer()?.sendSystemMessage(message.text())
        }
    }

    fun tick() {
        senses.forEach {
            it.ticksUntilNextExecution--
            if (it.ticksUntilNextExecution <= 0) {
                it.tick(entity, this)
            }
        }

        if (fixation?.tick(entity, this) == false) {
            fixation = null
        }

        val objectiveStatuses = mutableMapOf<Objective, ObjectiveStatus>()

        val activeObjectiveTags = mutableSetOf<String>()
        for (objective in objectives) {
            val status = objective.perform(entity, this, activeObjectiveTags)
            objectiveStatuses[objective] = status
        }

        if (debuggingPlayers.isNotEmpty()) {
            val state = BiggerBrainState(entity = entity, brain = this, objectiveStatuses = objectiveStatuses)
            val packet = UpdateTrackedBrainDataPacket(entity.id, state)
            debuggingPlayers.removeIf { uuid ->
                val player = uuid.getPlayer()
                if (player != null && Cobblemon.serverPlayerDebuggingPokemonBrainEntities[player.uuid] != entity.uuid) {
                    return@removeIf true
                } else {
                    player?.sendPacket(packet)
                }
                return@removeIf player == null
            }
        }
    }

    fun fixate(fixation: EntityFixation): EntityFixation {
        this.fixation = fixation
        return fixation
    }

    fun distract(distraction: Distraction): Boolean {
        if (fixation?.isDistracted(distraction, entity) != false) {
            this.fixation = null
            return true
        }
        return false
    }

    fun <T> setMemory(memory: MemoryModuleType<T>, value: T, expiry: Long? = null) {
        if (expiry != null) {
            entity.brain.setMemoryWithExpiry(memory, value, expiry)
        } else {
            entity.brain.setMemory(memory, value)
        }
        observedMemories[memory]?.forEach { objective ->
            objective.onMemoryChanged(entity, this, memory, value)
        }
    }

    fun <T> setMemoryIfChanged(memory: MemoryModuleType<T>, value: T, expiry: Long? = null) {
        if (entity.brain.getMemorySafely(memory).orElse(null) != value) {
            setMemory(memory, value, expiry)
        }
    }

    fun <T> eraseMemory(memory: MemoryModuleType<T>) {
        entity.brain.eraseMemory(memory)
        observedMemories[memory]?.forEach { objective ->
            objective.onMemoryChanged(entity, this, memory, null)
        }
    }

    fun <T> eraseMemoryIfPresent(memory: MemoryModuleType<T>) {
        if (entity.brain.hasMemoryValue(memory)) {
            eraseMemory(memory)
        }
    }

    fun hasMemoryValue(memory: MemoryModuleType<*>): Boolean {
        return entity.brain.checkMemory(memory, MemoryStatus.VALUE_PRESENT)
    }

    fun <T> getMemory(memory: MemoryModuleType<T>): T? {
        return entity.brain.getMemorySafely(memory).orElse(null)
    }
}