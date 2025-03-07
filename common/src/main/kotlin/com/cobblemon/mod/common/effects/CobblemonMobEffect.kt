/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.effects

import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectCategory

/**
 *
 * A base class for creating custom [MobEffect] instances.
 *
 * @author Conan
 * @since December 21st, 2024
 */

class CobblemonMobEffect(category: MobEffectCategory, color: Int) : MobEffect(category, color) {
    override fun shouldApplyEffectTickThisTick(duration: Int, amplifier: Int): Boolean {
        return true
    }
}