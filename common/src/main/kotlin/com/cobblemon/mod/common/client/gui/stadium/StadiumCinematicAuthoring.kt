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
