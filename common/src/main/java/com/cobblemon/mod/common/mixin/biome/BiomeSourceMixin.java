/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin.biome;

import com.cobblemon.mod.common.duck.biome.BiomeSourceDuck;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * TODO (MeAlam):<br>
 * This mixin is currently disabled, as it does not appear to be required at this time.
 * It is retained for potential future use if biome injection becomes necessary.
 *
 * <p>
 * When enabled, this mixin extends the {@code BiomeSource} by modifying the
 * {@code possibleBiomes} supplier to include additional biomes at runtime.
 * It merges existing biomes with new ones while ensuring uniqueness.
 * </p>
 *
 * <p>
 * Example usage:
 * <pre>{@code
 * ((BiomeSourceDuck) biomeSource).addPossibleBiomes(List.of(
 *     biomeHolderA,
 *     biomeHolderB
 * ));
 *
 * // Existing and new biomes are merged with duplicates removed.
 * }</pre>
 * </p>
 *
 * @author MeAlam
 * @since April 9th 2026
 */
@Mixin(BiomeSource.class)
public abstract class BiomeSourceMixin implements BiomeResolver, BiomeSourceDuck {
	@Mutable
	@Final
	@Shadow
	private Supplier<Set<Holder<Biome>>> possibleBiomes;

	@Override
	public void addPossibleBiomes(List<Holder<Biome>> biomes) {
		List<Holder<Biome>> combined = new ArrayList<>();
		combined.addAll(this.possibleBiomes.get());
		combined.addAll(biomes);

		this.possibleBiomes = () -> new ObjectLinkedOpenHashSet<>(combined.stream().distinct().collect(Collectors.toList()));
	}
}
