/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.item.interactive

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.effects.CobblemonMobEffects
import com.cobblemon.mod.common.api.text.red
import com.cobblemon.mod.common.item.CobblemonItem
import com.cobblemon.mod.common.item.interactive.RepelItem.Companion.MAX_REPEL_DURATION
import com.cobblemon.mod.common.item.interactive.RepelItem.Companion.REPEL_DURATION
import com.cobblemon.mod.common.item.interactive.RepelItem.Companion.SUPER_REPEL_DURATION
import com.cobblemon.mod.common.util.effectLang
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

class RepelItem(val type: RepelType) : CobblemonItem(Properties()) {

    companion object {
        const val REPEL_DURATION = 2000
        const val SUPER_REPEL_DURATION = 4000
        const val MAX_REPEL_DURATION = 5000
    }

    override fun use(level: Level, player: Player, usedHand: InteractionHand): InteractionResultHolder<ItemStack> {
        if (level.isClientSide) {
            return super.use(level, player, usedHand)
        }

        val hasEmptyParty = Cobblemon.storage.getParty(player as ServerPlayer).filterNotNull().isEmpty()
        if (hasEmptyParty) {
            player.sendSystemMessage(effectLang("errors.no_party").red())
            return super.use(level, player, usedHand)
        }

        if (player.hasEffect(CobblemonMobEffects.REPEL)) {
            player.sendSystemMessage(effectLang("errors.has_effect").red())
            return super.use(level, player, usedHand)
        }

        player.addEffect(MobEffectInstance(CobblemonMobEffects.REPEL, this.type.returnDuration(type)))
        if (!player.isCreative) {
            player.getItemInHand(usedHand).shrink(1)
        }

        return super.use(level, player, usedHand)
    }

}

enum class RepelType {
    REPEL,
    SUPER_REPEL,
    MAX_REPEL;

    fun returnDuration(type: RepelType) : Int {
        val duration = when (type) {
            REPEL -> REPEL_DURATION
            SUPER_REPEL -> SUPER_REPEL_DURATION
            MAX_REPEL -> MAX_REPEL_DURATION
        }

        return duration
    }
}
