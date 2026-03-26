package com.cobblemon.mod.common.mixin.biome;

import com.cobblemon.mod.common.duck.biome.BiomeSourceDuck;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import org.spongepowered.asm.mixin.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

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
