package com.cobblemon.mod.common.mixin;

import com.cobblemon.mod.common.CobblemonBiomes;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.RegistryLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {

	/**
	 * Wraps the earliest {@code compositeAccess()} call in {@link MinecraftServer}'s constructor
	 * so Cobblemon can capture the server registry access as soon as it exists.
	 *
	 * <p>This sets {@link CobblemonBiomes#registryManager} before downstream initialization
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
	private RegistryAccess.Frozen cobblemon$earlyCaptureRegistries
	(LayeredRegistryAccess<RegistryLayer> instance, Operation<RegistryAccess.Frozen> original) {
		CobblemonBiomes.registryManager = instance.compositeAccess();

		return original.call(instance);
	}
}
