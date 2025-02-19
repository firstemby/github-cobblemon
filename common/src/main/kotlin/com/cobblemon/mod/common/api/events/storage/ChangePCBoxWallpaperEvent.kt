/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.events.storage

import com.cobblemon.mod.common.api.events.Cancelable
import com.cobblemon.mod.common.api.storage.pc.PCBox
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer

/**
 * Events fired whenever a player changes their PC Wallpaper.
 * Has a cancelable [Pre] event and a [Post] which gets fired after the change.
 *
 * @author JustAHuman-xD
 * @since February 14th, 2025
 */
interface ChangePCBoxWallpaperEvent {

    /**
     * The [ServerPlayer] who is changing their wallpaper
     */
    val player: ServerPlayer

    /**
     * The [PCBox] whose wallpaper is being changed
     */
    val box: PCBox

    /**
     * The location of the wallpaper that is being changed to. Can be modified in the [Pre] event.
     * NOTE: Changing this to a wallpaper that does not exist on the client, will result in a purple/black fallback texture being displayed.
     */
    val wallpaper: ResourceLocation

    class Pre(
        override val player: ServerPlayer,
        override val box: PCBox,
        override var wallpaper: ResourceLocation
    ) : ChangePCBoxWallpaperEvent, Cancelable()

    class Post(
        override val player: ServerPlayer,
        override val box: PCBox,
        override val wallpaper: ResourceLocation
    ) : ChangePCBoxWallpaperEvent
}