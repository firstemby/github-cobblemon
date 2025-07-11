/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin.pokeball;

import com.cobblemon.mod.common.entity.pokeball.EmptyPokeballMarker;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ThrowableProjectile.class)
public abstract class ThrowableProjectileMixin extends ProjectileMixin {
    public ThrowableProjectileMixin(EntityType<? extends ThrowableProjectile> type, Level world) {
        super(type, world);
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void cobblemon$tickPokeballs(CallbackInfo ci) {
        /// [ThrowableProjectile.tick] does a lot of things we actively do *not* want it to do. However its
        /// superclasses do a lot of stuff in tick() that we *need* them to do. Ergo we inject this tiny mixin which
        /// will check in essence `this instanceof EmptyPokeballEntity` and then call the minimal super.tick() && return
        /// marker.isBouncy() is required since we may not have bouncy balls all the time and if we don't then this
        /// mixin will break stuff.
        if (this instanceof EmptyPokeballMarker) {
            super.tick();
            ci.cancel();
        }
    }
}
