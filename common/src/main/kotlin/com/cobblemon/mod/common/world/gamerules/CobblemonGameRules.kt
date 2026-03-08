/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.world.gamerules

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.mixin.invoker.BooleanRuleInvoker
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.serialization.Codec
import net.minecraft.world.flag.FeatureFlagSet
import net.minecraft.world.level.gamerules.*

object CobblemonGameRules {

    @JvmField
    val BATTLE_INVULNERABILITY: GameRule<Boolean> = Cobblemon.implementation.registerGameRule("battleInvulnerability", registerBoolean(GameRuleCategory.PLAYER, false))
    @JvmField
    val DO_POKEMON_SPAWNING: GameRule<Boolean> = Cobblemon.implementation.registerGameRule("doPokemonSpawning", registerBoolean(GameRuleCategory.SPAWNING, true))
    @JvmField
    val DO_POKEMON_LOOT: GameRule<Boolean> = Cobblemon.implementation.registerGameRule("doPokemonLoot", registerBoolean(GameRuleCategory.DROPS, true))
    @JvmField
    val MOB_TARGET_IN_BATTLE: GameRule<Boolean> = Cobblemon.implementation.registerGameRule("mobTargetInBattle", registerBoolean(GameRuleCategory.MOBS, true))
    @JvmField
    val SHINY_STARTERS: GameRule<Boolean> = Cobblemon.implementation.registerGameRule("doShinyStarters", registerBoolean(GameRuleCategory.MISC, false))
    @JvmField
    val HEALERS_HEAL_PC: GameRule<Boolean> = Cobblemon.implementation.registerGameRule("healersHealPC", registerBoolean(GameRuleCategory.MISC, false))

    private fun registerBoolean(gameRuleCategory: GameRuleCategory, initialValue: Boolean): GameRule<Boolean> {
        return GameRule<Boolean>(
            gameRuleCategory,
            GameRuleType.BOOL,
            BoolArgumentType.bool(),
            GameRuleTypeVisitor::visitBoolean,
            Codec.BOOL,
            { bl: Boolean -> if (bl) 1 else 0 },
            initialValue,
            FeatureFlagSet.of())
    }
}