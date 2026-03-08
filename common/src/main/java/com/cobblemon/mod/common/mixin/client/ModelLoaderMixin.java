/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin.client;

import com.cobblemon.mod.common.client.CobblemonBakingOverrides;
import java.util.List;
import java.util.Map;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.resources.model.BlockStateModelLoader;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelIdentifier;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelBakery.class)
public abstract class ModelLoaderMixin {

    @Shadow abstract UnbakedModel getModel(Identifier Identifier);

    @Shadow protected abstract void registerModel(ModelIdentifier modelId, UnbakedModel unbakedModel);

    @Inject(
        method = "<init>",
        at = @At("TAIL")
    )
    public void init(BlockColors blockColors,
        ProfilerFiller profiler,
        Map<Identifier, BlockModel> jsonUnbakedModels,
        Map<Identifier, List<BlockStateModelLoader.LoadedJson>> blockStates,
        CallbackInfo ci) {
        CobblemonBakingOverrides.INSTANCE.getModels().forEach(bakingOverride -> {
            var unbakedModel = this.getModel(bakingOverride.getModelLocation());
            this.registerModel(bakingOverride.getModelIdentifier(), unbakedModel);
        });
    }
}
