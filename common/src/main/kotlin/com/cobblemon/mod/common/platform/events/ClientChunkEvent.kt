/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.platform.events

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.level.chunk.LevelChunk

/**
 * Events fired for client side [LevelChunk] instances.
 *
 * @author Cobblemon Contributors
 * @since November 11th, 2025
 */
interface ClientChunkEvent {

    /** The chunk involved in the event. */
    val chunk: LevelChunk

    /** The client's [ClientLevel]. */
    val level: ClientLevel

    /** Event fired when [chunk] loads into the client's [level]. */
    data class Load(override val chunk: LevelChunk, override val level: ClientLevel) : ClientChunkEvent

    /** Event fired when [chunk] unloads from the client's [level]. */
    data class Unload(override val chunk: LevelChunk, override val level: ClientLevel) : ClientChunkEvent
}

