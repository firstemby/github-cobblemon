/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.effects

import com.cobblemon.mod.common.platform.PlatformRegistry
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.core.Holder
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectCategory

object CobblemonMobEffects : PlatformRegistry<Registry<MobEffect>, ResourceKey<Registry<MobEffect>>, MobEffect>() {

    var REPEL: Holder<MobEffect>

    fun register(name: String, effect: MobEffect): Holder<MobEffect> {
        return Registry.registerForHolder(
            BuiltInRegistries.MOB_EFFECT,
            cobblemonResource(name),
            effect
        )
    }

    init {
        REPEL = register("repel", CobblemonMobEffect(MobEffectCategory.BENEFICIAL, 0x98D982))
    }

    override val registry = BuiltInRegistries.MOB_EFFECT
    override val resourceKey = Registries.MOB_EFFECT
}
