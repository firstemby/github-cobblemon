/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin.client;

import com.cobblemon.mod.common.item.PokeBallItem;
import com.cobblemon.mod.common.item.PokedexItem;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntityRenderer.class)
public class ItemEntityRendererMixin {
    @Shadow @Final private ItemModelResolver itemModelResolver;

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void cobblemon$overrideDroppedItemModel(ItemEntity entity, ItemEntityRenderState state, float partialTick, CallbackInfo ci) {
        Identifier modelId = cobblemon$getGroundModel(entity.getItem());
        if (modelId == null) {
            return;
        }

        ItemStack renderStack = entity.getItem().copy();
        renderStack.set(DataComponents.ITEM_MODEL, modelId);
        this.itemModelResolver.updateForNonLiving(state.item, renderStack, ItemDisplayContext.GROUND, entity);
    }

    @Unique
    private static Identifier cobblemon$getGroundModel(ItemStack stack) {
        if (stack.getItem() instanceof PokeBallItem pokeBallItem) {
            return cobblemon$asStandaloneModel(pokeBallItem.getPokeBall().getModel3d());
        }

        if (stack.getItem() instanceof PokedexItem pokedexItem) {
            return cobblemon$asStandaloneModel(pokedexItem.getType().getItemModelPath("off"));
        }

        return null;
    }

    @Unique
    private static Identifier cobblemon$asStandaloneModel(Identifier modelId) {
        return modelId.withPath(path -> path.startsWith("item/") ? path.substring("item/".length()) : path);
    }
}
