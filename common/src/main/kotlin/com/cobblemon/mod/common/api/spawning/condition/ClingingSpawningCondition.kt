/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.spawning.condition

import com.cobblemon.mod.common.api.conditional.RegistryLikeCondition
import com.cobblemon.mod.common.api.spawning.position.ClingingSpawnablePosition
import com.cobblemon.mod.common.util.Merger
import net.minecraft.core.Direction
import net.minecraft.world.level.block.Block

/**
 * Base type for a spawning condition that applies to some kind of [ClingingSpawnablePosition].
 *
 * The [attachedTo] list restricts which blocks the Pokemon may attach to, and [faces] restricts
 * which faces of those blocks are usable: "sides", "top", or "bottom".
 */
abstract class ClingingTypeSpawningCondition<T : ClingingSpawnablePosition> : AreaTypeSpawningCondition<T>() {
    var attachedTo: MutableList<RegistryLikeCondition<Block>>? = null
    var faces: MutableList<String>? = null

    override fun fits(spawnablePosition: T): Boolean {
        if (!super.fits(spawnablePosition)) return false
        return spawnablePosition.attachments.keys.any { allowsAttachment(spawnablePosition, it) }
    }

    fun allowsAttachment(spawnablePosition: ClingingSpawnablePosition, direction: Direction): Boolean {
        if (faces != null && faces!!.none { faceMatches(it, direction) }) return false
        if (attachedTo != null && attachedTo!!.none { it.fits(spawnablePosition.attachmentHolder(direction)) }) return false
        return true
    }

    private fun faceMatches(face: String, direction: Direction): Boolean = when (face.lowercase()) {
        "sides" -> direction.axis.isHorizontal
        "top" -> direction == Direction.DOWN
        "bottom" -> direction == Direction.UP
        else -> false
    }

    override fun copyFrom(other: SpawningCondition<*>, merger: Merger) {
        super.copyFrom(other, merger)
        if (other is ClingingTypeSpawningCondition) {
            attachedTo = merger.merge(attachedTo, other.attachedTo)?.toMutableList()
            faces = merger.merge(faces, other.faces)?.toMutableList()
        }
    }

    override fun isValid(): Boolean {
        val containsNullValues = attachedTo != null && attachedTo!!.any { it == null }
        return super.isValid() && !containsNullValues
    }
}

/**
 * A spawning condition for a [ClingingSpawnablePosition].
 */
class ClingingSpawningCondition : ClingingTypeSpawningCondition<ClingingSpawnablePosition>() {
    override fun spawnablePositionClass() = ClingingSpawnablePosition::class.java
    companion object {
        const val NAME = "clinging"
    }
}
