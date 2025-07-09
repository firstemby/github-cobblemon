/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.item

import com.cobblemon.mod.common.api.pokeball.PokeBalls
import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity
import com.cobblemon.mod.common.pokeball.PokeBall
import com.cobblemon.mod.common.util.math.geometry.toRadians
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Rarity
import net.minecraft.world.item.UseAnim
import net.minecraft.world.level.Level
import kotlin.math.cos
import kotlin.math.min

class PokeBallItem(
        val pokeBall: PokeBall
) : CobblemonItem(Properties().apply {
    when (pokeBall.name) {
        // Master balls are a netherite product and should be fire immune
        PokeBalls.MASTER_BALL.name -> fireResistant().rarity(Rarity.EPIC)
        PokeBalls.CHERISH_BALL.name -> rarity(Rarity.EPIC)
        PokeBalls.ANCIENT_ORIGIN_BALL.name -> rarity(Rarity.EPIC)
        PokeBalls.BEAST_BALL.name -> rarity(Rarity.RARE)
    }
}) {

    override fun use(world: Level, player: Player, usedHand: InteractionHand): InteractionResultHolder<ItemStack> {
        val itemStack = player.getItemInHand(usedHand)
        player.startUsingItem(usedHand)
        return InteractionResultHolder.consume(itemStack)
    }

    override fun releaseUsing(stack: ItemStack, level: Level, player: LivingEntity, remainingUseDuration: Int) {
        if (player is ServerPlayer) {
            val ticksHeld = getUseDuration(stack, player) - remainingUseDuration
            val force = getPowerForTime(ticksHeld)

            if (force > 0.1) {
                val inaccuracy = when {
                    ticksHeld < 10 -> 3f
                    ticksHeld < 20 -> 2f
                    ticksHeld < 30 -> 0f
                    else -> 1f
                }

                throwPokeBall(level, player, min(force, 1f), inaccuracy)
                player.cooldowns.addCooldown(this, 10)
                stack.consume(1, player)
            }
        } else {
            super.releaseUsing(stack, level, player, remainingUseDuration)
        }
    }

    fun getPowerForTime(charge: Int): Float {
        var f = charge.toFloat() / 20.0f
        f = (f * f + f * 2.0f) / 3.0f
        if (f > 1.0f) {
            f = 1.0f
        }

        return f
    }

    override fun onUseTick(level: Level, livingEntity: LivingEntity, stack: ItemStack, remainingUseDuration: Int) {
        if (level.isClientSide && livingEntity is Player) {
            val ticksHeld = getUseDuration(stack, livingEntity) - remainingUseDuration
            if (ticksHeld == 20) {
                livingEntity.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, livingEntity.soundSource, 1.0F, 1.0F)
            }
        }
        super.onUseTick(level, livingEntity, stack, remainingUseDuration)
    }

    override fun getUseDuration(stack: ItemStack, entity: LivingEntity): Int = 72000

    override fun getUseAnimation(stack: ItemStack): UseAnim? = UseAnim.BOW

    private fun throwPokeBall(world: Level, player: ServerPlayer, force: Float, inaccuracy: Float) {
        val pokeBallEntity = EmptyPokeBallEntity(pokeBall, player.level(), player).apply {
            val overhandFactor: Float = if (player.xRot < 0) {
                5f * cos(player.xRot.toRadians())
            } else {
                5f
            }

            shootFromRotation(player, player.xRot - overhandFactor, player.yRot, 0.0f, pokeBall.throwPower * force, inaccuracy)
            setPos(position().add(deltaMovement.normalize().scale(1.0)))
            owner = player
        }
        world.addFreshEntity(pokeBallEntity)
    }
}