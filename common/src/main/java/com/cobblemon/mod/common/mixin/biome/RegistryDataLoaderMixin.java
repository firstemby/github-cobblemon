package com.cobblemon.mod.common.mixin.biome;

import com.cobblemon.mod.common.CobblemonBiomes;
import com.cobblemon.mod.common.world.biome.CobblemonBiomeLoader;
import com.mojang.serialization.Decoder;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * Registers Cobblemon biomes during datapack registry loading.
 *
 * <p>Injected into {@link RegistryDataLoader} at the tail of
 * {@code loadContentsFromManager}.</p>
 *
 * <p>At this point, vanilla biome JSON has already been decoded, but registries are not frozen
 * yet, making this the safest point to add biome entries.</p>
 *
 * <p>Only applies when the currently loaded registry is {@link Registries#BIOME}.</p>
 *
 * @author MeAlam
 * @since April 9th 2026
 */
@Mixin(RegistryDataLoader.class)
public class RegistryDataLoaderMixin {
	@Inject(method = "loadContentsFromManager", at = @At("TAIL"))
	private static <E> void cobblemon$registerBiomes(
			ResourceManager resourceManager,
			RegistryOps.RegistryInfoLookup registryInfoLookup,
			WritableRegistry<E> registry,
			Decoder<E> codec,
			Map<ResourceKey<?>, Exception> loadingErrors,
			CallbackInfo ci
	) {
		if (!registry.key().equals(Registries.BIOME)) {
			return;
		}

		@SuppressWarnings("unchecked")
		WritableRegistry<Biome> biomeRegistry = (WritableRegistry<Biome>) registry;

		CobblemonBiomes.register(
				CobblemonBiomeLoader.load(resourceManager, registryInfoLookup)
		);
		CobblemonBiomes.INSTANCE.setRegistry(biomeRegistry);
	}
}
