package com.cobblemon.mod.common.mixin;

import com.cobblemon.mod.common.CobblemonBiomes;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.RegistryLayer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {

	@WrapOperation(method = "<init>", at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/core/LayeredRegistryAccess;compositeAccess()Lnet/minecraft/core/RegistryAccess$Frozen;",
			opcode = Opcodes.PUTFIELD,
			ordinal = 0
	))
	@SuppressWarnings("unused")
	private RegistryAccess.Frozen biolith$earlyCaptureRegistries(LayeredRegistryAccess<RegistryLayer> instance, Operation<RegistryAccess.Frozen> original) {
		CobblemonBiomes.setRegistryManager(instance);

		return original.call(instance);
	}
}
