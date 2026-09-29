/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.camera

import com.cobblemon.mod.common.mixin.accessor.CameraAccessor
import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.BlockGetter

class GameCamera : Camera() {

    fun prepare(client: Minecraft, partialTick: Float) {
        val level = client.level ?: return
        val cameraEntity = client.cameraEntity ?: client.player ?: return
        super.setup(level, cameraEntity, true, false, partialTick)
    }

    override fun setup(level: BlockGetter, entity: Entity, detached: Boolean, thirdPersonReverse: Boolean, partialTick: Float) {
        super.setup(level, entity, false, thirdPersonReverse, partialTick)
        (this as CameraAccessor).setDetached(true)
    }
}
