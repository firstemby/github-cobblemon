/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin;

import com.cobblemon.mod.common.world.feature.PlacedFeatureLocator;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Reports successful feature placements to {@link PlacedFeatureLocator}, which keeps a session-lifetime,
 * per-dimension index of locatable Cobblemon features for `/locatefeature` to answer from.
 */
@Mixin(PlacedFeature.class)
public abstract class PlacedFeatureMixin {

	@Inject(method = "placeWithContext", at = @At("RETURN"))
	private void cobblemon$recordPlacement(PlacementContext context, RandomSource random, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ()) {
			PlacedFeatureLocator.onFeaturePlaced((PlacedFeature) (Object) this, context, pos);
		}
	}
}