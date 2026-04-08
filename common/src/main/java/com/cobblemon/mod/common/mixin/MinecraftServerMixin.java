package com.cobblemon.mod.common.mixin;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.CobblemonBiomes;
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
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.Proxy;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {

	@Inject(method = "<init>", at = @At("RETURN"))
	private void cobblemon$onInit(
			Thread serverThread,
			LevelStorageSource.LevelStorageAccess storageSource,
			PackRepository packRepository,
			WorldStem worldStem,
			Proxy proxy,
			DataFixer fixerUpper,
			Services services,
			ChunkProgressListenerFactory progressListenerFactory,
			CallbackInfo ci
	) {
		//CobblemonBiomes.init((MinecraftServer) (Object) this);
	}

	@WrapOperation(method = "<init>", at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/core/LayeredRegistryAccess;compositeAccess()Lnet/minecraft/core/RegistryAccess$Frozen;",
			ordinal = 0
	))
	@SuppressWarnings("unused")
	private RegistryAccess.Frozen cobblemon$earlyCaptureRegistries
			(LayeredRegistryAccess<RegistryLayer> instance, Operation<RegistryAccess.Frozen> original) {
		CobblemonBiomes.setRegistryManager(instance);

		return original.call(instance);
	}
}
