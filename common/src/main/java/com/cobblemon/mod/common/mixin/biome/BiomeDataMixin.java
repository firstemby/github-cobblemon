package com.cobblemon.mod.common.mixin.biome;

import com.cobblemon.mod.common.CobblemonBiomes;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.biome.BiomeData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Registers Cobblemon biomes during vanilla biome bootstrap.
 *
 * <p>
 * This runs while {@link BiomeData#bootstrap(BootstrapContext)} is building the biome
 * registry lookup used by world preset setup and world-creation flows.
 * Registering here ensures Cobblemon biome {@link ResourceKey}s
 * already exist before world type resolution (including SingleBiome preset usage).
 * </p>
 *
 * @author MeAlam
 * @since April 9th 2026
 */
@Mixin(BiomeData.class)
public class BiomeDataMixin {
	@Inject(method = "bootstrap", at = @At("TAIL"))
	private static void cobblemon$registerBiomes(BootstrapContext<Biome> context, CallbackInfo ci) {
		context.register(CobblemonBiomes.TEST_BIOME, CobblemonBiomes.createTestBiome());
	}
}
