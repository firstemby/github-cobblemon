/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin.biome;


import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.biome.BiomeData;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TODO (MeAlam):<br>
 * This mixin is currently disabled, as it does not appear to be required at this time.
 * It is retained for potential future use if biome registration issues arise.
 *
 * <p>
 * When enabled, this mixin registers biomes into the bootstrap context
 * of Minecraft's {@code BiomeData} class.
 * </p>
 *
 * <p>
 * Example usage:
 * <pre>{@code
 * context.register(MY_BIOME_KEY, createMyBiome());
 *
 * // e.g.
 * context.register(
 *     CobblemonBiomes.TEST_BIOME,
 *     CobblemonBiomes.createTestBiome()
 * );
 * }</pre>
 *
 * @author MeAlam
 * @since April 9th 2026
 */
@Mixin(BiomeData.class)
public class BiomeDataMixin {
	@Inject(method = "bootstrap", at = @At("TAIL"))
	private static void cobblemon$registerBiomes(BootstrapContext<Biome> context, CallbackInfo ci) {

	}
}