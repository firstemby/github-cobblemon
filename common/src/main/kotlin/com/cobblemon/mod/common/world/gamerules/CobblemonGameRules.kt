/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.world.gamerules

import com.cobblemon.mod.common.platform.PlatformRegistry
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.serialization.Codec
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.world.flag.FeatureFlagSet
import net.minecraft.world.level.gamerules.GameRule
import net.minecraft.world.level.gamerules.GameRuleCategory
import net.minecraft.world.level.gamerules.GameRuleType
import net.minecraft.world.level.gamerules.GameRuleTypeVisitor

object CobblemonGameRules  : PlatformRegistry<Registry<GameRule<*>>, ResourceKey<Registry<GameRule<*>>>, GameRule<*>>() {

    override val registry: Registry<GameRule<*>> = BuiltInRegistries.GAME_RULE
    override val resourceKey: ResourceKey<Registry<GameRule<*>>> = Registries.GAME_RULE

    @JvmField
    val BATTLE_INVULNERABILITY: GameRule<Boolean> = create("battle_invulnerability", registerBoolean(GameRuleCategory.PLAYER, false))
    @JvmField
    val DO_POKEMON_SPAWNING: GameRule<Boolean> = create("do_pokemon_spawning", registerBoolean(GameRuleCategory.SPAWNING, true))
    @JvmField
    val DO_POKEMON_LOOT: GameRule<Boolean> = create("do_pokemon_loot", registerBoolean(GameRuleCategory.DROPS, true))
    @JvmField
    val MOB_TARGET_IN_BATTLE: GameRule<Boolean> = create("mob_target_in_battle", registerBoolean(GameRuleCategory.MOBS, true))
    @JvmField
    val SHINY_STARTERS: GameRule<Boolean> = create("do_shiny_starters", registerBoolean(GameRuleCategory.MISC, false))
    @JvmField
    val HEALERS_HEAL_PC: GameRule<Boolean> = create("healers_heal_pc", registerBoolean(GameRuleCategory.MISC, false))

    private fun registerBoolean(gameRuleCategory: GameRuleCategory, initialValue: Boolean): GameRule<Boolean> {
        return GameRule(
            gameRuleCategory,
            GameRuleType.BOOL,
            BoolArgumentType.bool(),
            GameRuleTypeVisitor::visitBoolean,
            Codec.BOOL,
            { if (it) 1 else 0 },
            initialValue,
            FeatureFlagSet.of())
    }
}