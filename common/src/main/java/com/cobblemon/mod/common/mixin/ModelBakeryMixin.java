package com.cobblemon.mod.common.mixin;

import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.resources.model.BlockStateModelLoader;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;

@Mixin(ModelBakery.class)
public abstract class ModelBakeryMixin {

    @Final
    @Shadow
    private Map<ResourceLocation, BlockModel> modelResources;

    @Shadow
    protected abstract void loadItemModelAndDependencies(ResourceLocation modelLocation);

    @Inject(method = "<init>", at = @At("TAIL"))
    private void cobblemon$loadEggModels(
          BlockColors blockColors,
          ProfilerFiller profilerFiller,
          Map<ResourceLocation, BlockModel> modelResources,
          Map<ResourceLocation, List<BlockStateModelLoader.LoadedJson>> blockStateResources,
          CallbackInfo ci
    ) {
        for (ResourceLocation fileLocation : this.modelResources.keySet()) {
            if (!fileLocation.getNamespace().equals("cobblemon")) continue;
            String path = fileLocation.getPath();

            if (!path.startsWith("models/item/egg/")) continue;
            String modelPath = path.replace("models/item/", "").replace(".json", "");
            ResourceLocation modelId = new ResourceLocation("cobblemon", modelPath);
            this.loadItemModelAndDependencies(modelId);
        }
    }
}