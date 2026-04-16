/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin;

import com.cobblemon.mod.common.block.dispenser.DispenserBehaviorRegistry;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DispenseItemBehavior.class)
public interface DispenseItemBehaviourMixin {
    @Inject(method = "bootStrap", at = @At("TAIL"))
    private static void addCobblemonDispenserBehaviors(CallbackInfo ci) {
        DispenserBehaviorRegistry.INSTANCE.registerDispenserBehaviors();
    }
}
