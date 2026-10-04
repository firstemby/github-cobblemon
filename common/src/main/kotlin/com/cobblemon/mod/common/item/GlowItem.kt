/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.item

import com.cobblemon.mod.common.CobblemonSounds
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemNameBlockItem
import net.minecraft.world.item.SignApplicator
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.SignBlockEntity
import net.minecraft.world.level.block.entity.SignText

class GlowItem(block: Block, settings: Properties) : ItemNameBlockItem(block, settings), SignApplicator {

    override fun tryApplyToSign(level: Level, sign: SignBlockEntity, isFront: Boolean, player: Player?): Boolean {
        if (sign.updateText({ signText: SignText? -> signText!!.setHasGlowingText(true) }, isFront)) {
            level.playSound(
                null as Player?,
                sign.blockPos,
                CobblemonSounds.BRIGHT_POWDER_USE,
                SoundSource.BLOCKS,
                1.0f,
                1.0f
            )
            return true
        } else {
            return false
        }
    }

}