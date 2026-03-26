package com.cobblemon.mod.common.duck.biome;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

import java.util.List;

public interface BiomeSourceDuck {
	void addPossibleBiomes(List<Holder<Biome>> biomes);
}
