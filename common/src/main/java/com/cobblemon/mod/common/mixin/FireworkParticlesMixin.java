/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.client.particle.FireworkParticles;
import net.minecraft.world.item.component.FireworkExplosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(FireworkParticles.Starter.class)
public abstract class FireworkParticlesMixin {

    @Shadow
    protected abstract void createParticleShape(double speed, double[][] coords, IntList colors, IntList fadeColors, boolean trail, boolean twinkle, boolean isCreeper);

    @Inject(
        method = "tick()V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/particle/Particle;setColor(FFF)V",
            shift = At.Shift.BEFORE,
            by = 2
        )
    )
    private void cobblemon$createPokeballParticleShape(
        CallbackInfo ci,
        @Local FireworkExplosion fireworkExplosion2,
        @Local(ordinal = 0) int bl3,
        @Local(ordinal = 0) int bl4,
        @Local(ordinal = 0) IntList intList,
        @Local(ordinal = 0) IntList intList2
    ) {
        if (fireworkExplosion2.shape().getSerializedName().equals("pokeball")) {
            final double[][] POKEBALL_PARTICLE_COORDS = new double[][]{
                {0, 0.5},
                {0.05, 0.497494},
                {0.1, 0.489898},
                {0.15, 0.47697},
                {0.2, 0.458258},
                {0.25, 0.433013},
                {0.3, 0.4},
                {0.35, 0.357071},
                {0.4, 0.3},
                {0.45, 0.217945},
                {0.4975, 0.049937},
                {0.086606, 0.049994},
                {0.05, 0.086603},
                {0, 0.1},
                {0.05, 0.049994},

                {0.05, -0.049994},
                {0, -0.1},
                {0.05, -0.086603},
                {0.086606, -0.049994},
                {0.0, -0.049937},
                {0.4975, -0.049937},
                {0.45, -0.217945},
                {0.4, -0.3},
                {0.35, -0.357071},
                {0.3, -0.4},
                {0.25, -0.433013},
                {0.2, -0.458258},
                {0.15, -0.47697},
                {0.1, -0.489898},
                {0.05, -0.497494},
                {0, -0.5},
            };

            boolean b3 = bl3 == 1;
            boolean b4 = bl4 == 1;

            this.createParticleShape(0.5, POKEBALL_PARTICLE_COORDS, intList, intList2, b3, b4, true);
        }
    }
}
