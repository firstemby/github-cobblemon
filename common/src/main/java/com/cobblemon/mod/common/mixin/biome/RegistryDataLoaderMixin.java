package com.cobblemon.mod.common.mixin.biome;

import com.cobblemon.mod.common.CobblemonBiomes;
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

@Mixin(RegistryDataLoader.class)
public class RegistryDataLoaderMixin {
	@Inject(method = "loadContentsFromManager", at = @At("TAIL"))
	private static <E> void cobblemon$onLoadContentsFromManager(
			ResourceManager resourceManager,
			RegistryOps.RegistryInfoLookup registryInfoLookup,
			WritableRegistry<E> writableRegistry,
			Decoder<E> decoder,
			Map<ResourceKey<?>, Exception> errors,
			CallbackInfo ci
	) {
		if (!writableRegistry.key().equals(Registries.BIOME)) {
			return;
		}

		@SuppressWarnings("unchecked")
		WritableRegistry<Biome> biomeRegistry = (WritableRegistry<Biome>) writableRegistry;
		CobblemonBiomes.init(biomeRegistry);
	}
}
