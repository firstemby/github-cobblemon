/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain

import com.cobblemon.mod.common.api.ai.biggerbrain.objective.Objective
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.ObjectiveStatus
import kotlin.reflect.jvm.jvmName
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.world.entity.LivingEntity

/**
 * A DTO of sorts that is sent to debugging players to keep them informed about the state of an entity's Cobblemon AI.
 *
 * @author Hiroku
 * @since December 29th, 2025
 */
class BiggerBrainState(
    val senses: List<SenseState>,
    val objectives: List<ObjectiveState>,
    val fixation: String? = null
) {
    class SenseState(val className: String, val ticksUntilNextExecution: Int) {
        fun encode(buffer: FriendlyByteBuf) {
            buffer.writeUtf(className)
            buffer.writeInt(ticksUntilNextExecution)
        }

        companion object {
            fun decode(buffer: FriendlyByteBuf): SenseState {
                return SenseState(buffer.readUtf(), buffer.readInt())
            }
        }
    }

    class ObjectiveState(val description: String, val status: ObjectiveStatus) {
        fun encode(buffer: FriendlyByteBuf) {
            buffer.writeUtf(description)
            buffer.writeEnum(status)
        }

        companion object {
            fun decode(buffer: FriendlyByteBuf): ObjectiveState {
                return ObjectiveState(buffer.readUtf(), buffer.readEnum(ObjectiveStatus::class.java))
            }
        }
    }

    constructor(entity: LivingEntity, brain: BiggerBrain, objectiveStatuses: Map<Objective, ObjectiveStatus>): this(
        senses = brain.senses.map { SenseState(className = it::class.simpleName ?: it::class.jvmName, it.ticksUntilNextExecution) },
        objectives = objectiveStatuses.entries.map { ObjectiveState(it.key.getDescription(entity), it.value) },
        fixation = brain.fixation?.getDescription(entity)
    )

    fun encode(buffer: FriendlyByteBuf) {
        buffer.writeCollection(senses) { _, sense -> sense.encode(buffer) }
        buffer.writeCollection(objectives) { _, objective -> objective.encode(buffer) }
        buffer.writeNullable(fixation) { _, fixation -> buffer.writeUtf(fixation) }
    }

    companion object {
        fun decode(buffer: FriendlyByteBuf): BiggerBrainState {
            val senses = buffer.readList { SenseState.decode(it) }
            val objectives = buffer.readList { ObjectiveState.decode(it) }
            val fixation = buffer.readNullable { buffer.readUtf() }
            return BiggerBrainState(senses, objectives, fixation)
        }
    }
}