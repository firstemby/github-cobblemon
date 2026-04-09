package com.cobblemon.mod.common.mixin;

import com.cobblemon.mod.common.Cobblemon;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.datafixers.DataFixer;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.Services;
import net.minecraft.server.WorldStem;
import net.minecraft.server.level.progress.ChunkProgressListenerFactory;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Proxy;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {

	@Inject(method = "<init>", at = @At("RETURN"))
	private void cobblemon$onInit(
			Thread serverThread, LevelStorageSource.LevelStorageAccess storageSource, PackRepository packRepository, WorldStem worldStem, java.net.Proxy proxy, DataFixer fixerUpper, Services services, ChunkProgressListenerFactory progressListenerFactory, CallbackInfo ci
	) {
		Cobblemon.LOGGER.info("SERVER is initializingSSSSSS...");
	}
	
	/**
	 * Wraps the earliest {@code compositeAccess()} call in {@link MinecraftServer}'s constructor
	 * so Cobblemon can capture the server registry access as soon as it exists.
	 *
	 * <p>This sets {@link Cobblemon#registryManager} before downstream initialization
	 * depends on biome lookups, while still delegating to the original call unchanged.</p>
	 *
	 * @author MeAlam
	 * @since April 9th 2026
	 */
	@WrapOperation(method = "<init>", at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/core/LayeredRegistryAccess;compositeAccess()Lnet/minecraft/core/RegistryAccess$Frozen;",
			ordinal = 0
	))
	private RegistryAccess.Frozen cobblemon$captureRegistryAccess
	(LayeredRegistryAccess<RegistryLayer> instance, Operation<RegistryAccess.Frozen> original) {
		Cobblemon.registryManager = instance.compositeAccess();

		return original.call(instance);
	}
}
