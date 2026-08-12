/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin.client;

import com.cobblemon.mod.common.client.CobblemonBakingOverrides;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.resources.model.BlockStateModelLoader;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Mixin(ModelBakery.class)
public abstract class ModelLoaderMixin {
    @Shadow abstract UnbakedModel getModel(ResourceLocation resourceLocation);

    @Shadow protected abstract void registerModel(ModelResourceLocation modelId, UnbakedModel unbakedModel);

    @Shadow protected abstract void loadItemModelAndDependencies(ResourceLocation resourceLocation);

    @Unique
    List<String> dynamicFolders = Arrays.asList("egg", "tm");

    @Inject(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/profiling/ProfilerFiller;popPush(Ljava/lang/String;)V",
                    ordinal = 1
            )
    )
    public void loadCustomItemModels(BlockColors blockColors,
                                     ProfilerFiller profiler,
                                     Map<ResourceLocation, BlockModel> jsonUnbakedModels,
                                     Map<ResourceLocation, List<BlockStateModelLoader.LoadedJson>> blockStates,
                                     CallbackInfo ci
    ) {
        /*
         * The models/item/*.json's only get loaded if there's an item in the registry that actually asks for it. It
         * naively looks for things in the root with an item ID and calls upon it, and some of those JSONs reference
         * other model JSONs and so it cascades a little bit. Used for block models but more importantly here, sprites.
         *
         * The thing about the eggs (and TMs) is, we really want the eggs to be addon-compatible in a way that doesn't restrict
         * the species that can have eggs, so we can't pre-plan all of them. This mixin looks for the things inside the
         * egg subfolder (which won't get loaded automagically) and manually inserts them into the unbaked model list so
         * Minecraft loads them for us. We'll then dynamically call these models from the ItemRendererMixin; so it's
         * hacky at the init stage and hacky at the render stage. Pretty cool in the gameplay stage though.
         *
         * - Hiro
         */

        dynamicFolders.forEach(folder -> {
            Map<ResourceLocation, Resource> map = Minecraft.getInstance().getResourceManager().listResources("models/item/" + folder, (t) -> true);
            map.forEach((key, value) -> {
                ResourceLocation resourceLocation = new ResourceLocation(key.getNamespace(), key.getPath().replace("models/item/", "").replace(".json", ""));
                this.loadItemModelAndDependencies(resourceLocation);
            });
        });
    }

    @Inject(
            method = "<init>",
            at = @At("TAIL")
    )
    public void init(BlockColors blockColors,
                     ProfilerFiller profiler,
                     Map<ResourceLocation, BlockModel> jsonUnbakedModels,
                     Map<ResourceLocation, List<BlockStateModelLoader.LoadedJson>> blockStates,
                     CallbackInfo ci) {
        CobblemonBakingOverrides.INSTANCE.getModels().forEach(bakingOverride -> {
            var unbakedModel = this.getModel(bakingOverride.getModelLocation());
            this.registerModel(bakingOverride.getModelIdentifier(), unbakedModel);
        });
    }
}
