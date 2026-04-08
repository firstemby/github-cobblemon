package com.cobblemon.mod.common.mixin.biome;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.CobblemonBiomes;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.biome.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@Mixin(value = MultiNoiseBiomeSource.class, priority = 900)
public abstract class MultiNoiseBiomeSourceMixin extends BiomeSource {

	@Shadow
	protected abstract Climate.ParameterList<Holder<Biome>> parameters();

	@Unique
	private Climate.ParameterList<Holder.Reference<Biome>> cobblemon$parameters;

	@WrapOperation(
			method = "parameters",
			at = @At(value = "INVOKE", target = "Lcom/mojang/datafixers/util/Either;map(Ljava/util/function/Function;Ljava/util/function/Function;)Ljava/lang/Object;")
	)
	@SuppressWarnings("unused")
	private Object cobblemon$injectEntries(Either<Climate.ParameterList<RegistryAccess.RegistryEntry<Biome>>, RegistryAccess.RegistryEntry<MultiNoiseBiomeSourceParameterList>> instance, Function<Climate.ParameterList<RegistryAccess.RegistryEntry<Biome>>, Climate.ParameterList<RegistryAccess.RegistryEntry<Biome>>> leftMap, Function<RegistryAccess.RegistryEntry<MultiNoiseBiomeSourceParameterList>, Climate.ParameterList<RegistryAccess.RegistryEntry<Biome>>> rightMap, Operation<Object> original) {
		synchronized (this) {
			if (cobblemon$parameters == null) {
				Cobblemon.LOGGER.info("Injecting biomes into MultiNoiseBiomeSource");
				@SuppressWarnings("unchecked")
				Climate.ParameterList<Holder.Reference<Biome>> originalEntries =
						(Climate.ParameterList<Holder.Reference<Biome>>) original.call(instance, leftMap, rightMap);

				List<Pair<Climate.ParameterPoint, Holder.Reference<Biome>>> parameterList =
						new ArrayList<>(originalEntries.values().size());

				HolderGetter<Biome> biomeEntryGetter = CobblemonBiomes.getBiomeLookupOrThrow();
				Holder.Reference<Biome> testBiome = biomeEntryGetter.getOrThrow(CobblemonBiomes.TEST_BIOME);
				int replaced = 0;

				for (Pair<Climate.ParameterPoint, Holder.Reference<Biome>> entry : originalEntries.values()) {
					Holder.Reference<Biome> biome = entry.getSecond();
					if (biome.is(Biomes.PLAINS)
							|| biome.is(Biomes.FOREST)
							|| biome.is(Biomes.BIRCH_FOREST)
							|| biome.is(Biomes.SAVANNA)
							|| biome.is(Biomes.TAIGA)
							|| biome.is(Biomes.SNOWY_PLAINS)
							|| biome.is(Biomes.DESERT)
							|| biome.is(Biomes.OCEAN)) {
						parameterList.add(new Pair<>(entry.getFirst(), testBiome));
						replaced++;
					} else {
						parameterList.add(entry);
					}
				}

				if (replaced == 0 && !parameterList.isEmpty()) {
					Pair<Climate.ParameterPoint, Holder.Reference<Biome>> first = parameterList.get(0);
					parameterList.set(0, new Pair<>(first.getFirst(), testBiome));
					replaced = 1;
				}

				Cobblemon.LOGGER.info("Remapped {} climate entries to {}", replaced, CobblemonBiomes.TEST_BIOME.location());

				cobblemon$parameters = new Climate.ParameterList<>(parameterList);
			}
			return cobblemon$parameters;
		}
	}
}
