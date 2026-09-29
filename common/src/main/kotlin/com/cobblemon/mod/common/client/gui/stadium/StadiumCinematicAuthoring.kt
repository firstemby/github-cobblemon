/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.stadium

import com.cobblemon.mod.common.net.messages.client.stadium.StadiumCinematicPacket
import java.util.UUID
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos

object StadiumCinematicAuthoring {
    var available = false
    var hasUnsavedChanges: () -> Boolean = { false }
    var open: (BlockPos, UUID) -> Unit = { _, _ -> }
    var receive: (StadiumCinematicPacket, Minecraft) -> Unit = { _, _ -> }
}
