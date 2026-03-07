/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.battles.ai.config

import com.cobblemon.mod.common.api.battles.ai.config.effects.ContactEffect
import com.cobblemon.mod.common.api.battles.ai.config.effects.DamageEffect
import com.cobblemon.mod.common.api.battles.ai.config.effects.EnvironmentEffect
import com.cobblemon.mod.common.api.battles.ai.config.effects.HpEffect
import com.cobblemon.mod.common.api.battles.ai.config.effects.PriorityEffect
import com.cobblemon.mod.common.api.battles.ai.config.effects.RestrictionEffect
import com.cobblemon.mod.common.api.battles.ai.config.effects.StatEffect
import com.cobblemon.mod.common.api.battles.ai.config.effects.StatusEffect
import com.cobblemon.mod.common.api.battles.ai.config.effects.SurvivalEffect

/** Unified held item effects definition for AI decision-making. */
data class HeldItemEffects(
    val item: String,
    val categories: List<String> = emptyList(),
    val damage: DamageEffect? = null,
    val stats: StatEffect? = null,
    val hp: HpEffect? = null,
    val restrictions: RestrictionEffect? = null,
    val contact: ContactEffect? = null,
    val priority: PriorityEffect? = null,
    val survival: SurvivalEffect? = null,
    val environment: EnvironmentEffect? = null,
    val status: StatusEffect? = null,
    val properties: Map<String, Any> = emptyMap()
) {
    fun hasCategory(category: String): Boolean {
        return categories.any { it.equals(category, ignoreCase = true) }
    }

    fun getDamageMultiplier(): Double = damage?.multiplier ?: 1.0

    fun getTypeMultiplier(type: String): Double = damage?.getTypeMultiplier(type) ?: 1.0

    fun boostsType(type: String): Boolean = damage?.boostsType(type) ?: false

    fun getSuperEffectiveMultiplier(): Double = damage?.superEffectiveMultiplier ?: 1.0

    fun getStatMultiplier(stat: String): Double = stats?.getStatMultiplier(stat) ?: 1.0

    fun getAccuracyMultiplier(): Double = stats?.accuracyMultiplier ?: 1.0

    fun getCriticalHitModifier(): Int = stats?.criticalHitModifier ?: 0

    fun getRecoveryPerTurn(): Double = hp?.recoveryPerTurn ?: 0.0

    fun getRecoilPercent(): Double = hp?.recoilPercent ?: 0.0

    fun locksMove(): Boolean = restrictions?.locksMove ?: false

    fun blocksStatusMoves(): Boolean = restrictions?.blocksStatusMoves ?: false

    fun getContactDamagePercent(): Double = contact?.damagePercent ?: 0.0

    fun canSurviveOhko(): Boolean = survival?.survivesOhko ?: false

    fun isConsumable(): Boolean = survival?.consumable ?: false

    fun getWeatherBoost(): String? = environment?.weatherBoost

    fun getTerrainBoost(): String? = environment?.terrainBoost

    fun getStatusImmunities(): List<String> = status?.immunities ?: emptyList()

    fun hasProperty(key: String): Boolean = properties.containsKey(key.lowercase())

    fun getProperty(key: String, default: Double = 0.0): Double {
        val value = properties[key.lowercase()] ?: return default
        return when (value) {
            is Number -> value.toDouble()
            is Boolean -> if (value) 1.0 else 0.0
            else -> default
        }
    }

    fun getPropertyString(key: String, default: String = ""): String {
        val value = properties[key.lowercase()] ?: return default
        return value.toString()
    }

    companion object {
        val EMPTY = HeldItemEffects(item = "")
    }
}
