/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin.biome;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.util.biome.RegistryExtensions;
import com.cobblemon.mod.common.world.biome.CobblemonBiome;
import com.cobblemon.mod.common.world.biome.CobblemonBiomeLoader;
import com.cobblemon.mod.common.world.biome.CobblemonBiomePlacement;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Ensures compatibility with Biolith by applying this mixin before its biome modifications.
 *
 * <p>
 * Biolith uses mixins (see
 * <a href="https://github.com/TerraformersMC/Biolith/blob/1.21.1/common/src/main/java/com/terraformersmc/biolith/impl/mixin/MixinMultiNoiseBiomeSource.java">
 * MixinMultiNoiseBiomeSource
 * </a>)
 * with a priority of {@code 900}, allowing it to run before Terrablender.
 * </p>
 *
 * <p>
 * Because Biolith includes functionality to disable vanilla biomes, this mixin must
 * execute before Biolith’s transformations to ensure compatibility and prevent
 * unintended biome removal or conflicts.
 * </p>
 *
 * <p>
 * Each biome declares its placements in its JSON ({@code "parameters"}), optionally targeting
 * a multi-noise preset via {@code "preset"} (defaults to {@code minecraft:overworld}).
 * Only placements matching the preset used by this biome source are appended.
 * </p>
 *
 * <p>
 * Also logs, once per biome per chunk, whenever a data-driven biome is actually generated
 * (with block coordinates and a ready-to-use /tp command). See {@link #DEBUG_LOG_BIOME_SIGHTINGS}.
 * </p>
 *
 * @author MeAlam
 * @since April 9th 2026
 */
@Mixin(value = MultiNoiseBiomeSource.class, priority = 850)
public abstract class MultiNoiseBiomeSourceMixin extends BiomeSource {

	/**
	 * Flip to false to silence the per-chunk biome sighting log.
	 */
	@Unique
	private static final boolean DEBUG_LOG_BIOME_SIGHTINGS = false;

	/**
	 * Chunks (as packed long) in which each data-driven biome was already logged.
	 */
	@Unique
	private static final Map<ResourceLocation, Set<Long>> cobblemon$loggedChunks = new ConcurrentHashMap<>();

	@Shadow
	protected abstract Climate.ParameterList<Holder<Biome>> parameters();

	@Unique
	private Climate.ParameterList<Holder.Reference<Biome>> cobblemon$parameters;

	/**
	 * Resolves the registry id of the preset a biome source was built from.
	 * Handles both holder flavours so it does not depend on the exact runtime type.
	 */
	@Unique
	private static ResourceLocation cobblemon$resolvePresetId(Object source) {
		if (source instanceof Holder<?> holder) {
			return holder.unwrapKey().map(ResourceKey::location).orElse(null);
		}
		if (source instanceof RegistryAccess.RegistryEntry<?> entry) {
			return entry.key().location();
		}
		return null;
	}

	@WrapOperation(
			method = "parameters",
			at = @At(value = "INVOKE", target = "Lcom/mojang/datafixers/util/Either;map(Ljava/util/function/Function;Ljava/util/function/Function;)Ljava/lang/Object;"),
			remap = false
	)
	private Object cobblemon$injectBiomes(
			Either<Climate.ParameterList<RegistryAccess.RegistryEntry<Biome>>,
					RegistryAccess.RegistryEntry<MultiNoiseBiomeSourceParameterList>> instance,
			Function<Climate.ParameterList<RegistryAccess.RegistryEntry<Biome>>,
					Climate.ParameterList<RegistryAccess.RegistryEntry<Biome>>> leftMap,
			Function<RegistryAccess.RegistryEntry<MultiNoiseBiomeSourceParameterList>,
					Climate.ParameterList<RegistryAccess.RegistryEntry<Biome>>> rightMap,
			Operation<Object> original) {
		synchronized (this) {
			if (cobblemon$parameters == null) {
				/* NOTE:
				 * Minecraft also does this exact cast on the return of this operation, so we know it's safe.
				 */
				@SuppressWarnings("unchecked")
				Climate.ParameterList<Holder.Reference<Biome>> originalEntries =
						(Climate.ParameterList<Holder.Reference<Biome>>) original.call(instance, leftMap, rightMap);

				Optional<?> presetSource = instance.right();
				ResourceLocation presetId = cobblemon$resolvePresetId(presetSource.orElse(null));

				if (presetId == null) {
					cobblemon$parameters = originalEntries;
					return cobblemon$parameters;
				}

				Map<ResourceLocation, CobblemonBiome> loadedBiomes = CobblemonBiomeLoader.getLoadedBiomes();
				List<Pair<Climate.ParameterPoint, Holder.Reference<Biome>>> parameterList =
						new ArrayList<>(originalEntries.values().size() + loadedBiomes.size());
				parameterList.addAll(originalEntries.values());

				HolderGetter<Biome> biomeEntryGetter = RegistryExtensions.getRegistryLookup(Registries.BIOME).orElseThrow();
				int appended = 0;
				int skipped = 0;

				for (Map.Entry<ResourceLocation, CobblemonBiome> loadedEntry : loadedBiomes.entrySet()) {
					List<CobblemonBiomePlacement> matching = new ArrayList<>();
					for (CobblemonBiomePlacement placement : loadedEntry.getValue().getPlacements()) {
						if (placement.getPreset().equals(presetId)) {
							matching.add(placement);
						}
					}

					if (matching.isEmpty()) {
						continue;
					}

					ResourceKey<Biome> biomeKey = ResourceKey.create(Registries.BIOME, loadedEntry.getKey());
					Optional<Holder.Reference<Biome>> biomeHolder = biomeEntryGetter.get(biomeKey);

					if (biomeHolder.isEmpty()) {
						skipped++;
						Cobblemon.LOGGER.warn("MEALAM Skipping cobblemon biome climate entry for missing biome {}", loadedEntry.getKey());
						continue;
					}

					for (CobblemonBiomePlacement placement : matching) {
						parameterList.add(new Pair<>(placement.getPoint(), biomeHolder.get()));
						appended++;
					}
				}

				Cobblemon.LOGGER.info(
						"MEALAM Biome source climate entries for preset {}: base={}, appended={}, skipped={}.",
						presetId,
						originalEntries.values().size(),
						appended,
						skipped
				);

				cobblemon$parameters = new Climate.ParameterList<>(parameterList);
			}
			return cobblemon$parameters;
		}
	}

	/**
	 * TODO
	 * TEMPORARY: Logs where data-driven biomes are actually generated.
	 * Coordinates arrive in quart units (4x4x4 blocks), so they are converted to blocks.
	 * Logged once per biome per chunk to avoid spam.
	 */
	@Inject(method = "getNoiseBiome(IIILnet/minecraft/world/level/biome/Climate$Sampler;)Lnet/minecraft/core/Holder;", at = @At("RETURN"))
	private void cobblemon$logBiomeSightings(
			int quartX,
			int quartY,
			int quartZ,
			Climate.Sampler sampler,
			CallbackInfoReturnable<Holder<Biome>> cir
	) {
		if (!DEBUG_LOG_BIOME_SIGHTINGS) {
			return;
		}

		Optional<ResourceKey<Biome>> key = cir.getReturnValue().unwrapKey();
		if (key.isEmpty()) {
			return;
		}

		ResourceLocation location = key.get().location();
		if (!CobblemonBiomeLoader.getLoadedBiomes().containsKey(location)) {
			return;
		}

		int blockX = quartX << 2;
		int blockY = quartY << 2;
		int blockZ = quartZ << 2;
		long chunk = ChunkPos.asLong(blockX >> 4, blockZ >> 4);

		Set<Long> seen = cobblemon$loggedChunks.computeIfAbsent(location, k -> ConcurrentHashMap.newKeySet());
		if (seen.add(chunk)) {
			Cobblemon.LOGGER.info(
					"[BIOME] Biome {} generated at x={} y={} z={} (chunk {}, {}) -> /tp @s {} {} {}",
					location,
					blockX, blockY, blockZ,
					blockX >> 4, blockZ >> 4,
					blockX, blockY, blockZ
			);
		}
	}
}