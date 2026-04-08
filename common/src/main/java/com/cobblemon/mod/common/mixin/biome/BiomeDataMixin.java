package com.cobblemon.mod.common.mixin.biome;

import com.cobblemon.mod.common.CobblemonBiomes;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.biome.BiomeData;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BiomeData.class)
public class BiomeDataMixin {
	@Inject(method = "bootstrap", at = @At("TAIL"))
	private static void cobblemon$registerTestBiome(BootstrapContext<Biome> context, CallbackInfo ci) {
		context.register(CobblemonBiomes.TEST_BIOME, CobblemonBiomes.createTestBiome());
	}
}
