/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin.client;

import com.cobblemon.mod.common.OrientationControllable;
import com.cobblemon.mod.common.item.PokeBallItem;
import com.cobblemon.mod.common.item.PokedexItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.spongepowered.asm.mixin.Final;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {
    @Shadow @Final private ItemModelResolver itemModelResolver;

    @Inject(method = "renderArmWithItem", at = @At(value = "HEAD"), cancellable = true)
    private void cobblemon$renderArmWithItem(AbstractClientPlayer abstractClientPlayer, float f, float g, InteractionHand interactionHand, float h, ItemStack itemStack, float i, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int j, CallbackInfo ci) {
        if (abstractClientPlayer.isUsingItem() && abstractClientPlayer.getUseItem().getItem() instanceof PokedexItem) {
            ci.cancel();
        }
        else if (abstractClientPlayer instanceof OrientationControllable controllable && controllable.getOrientationController().isActive()) {

        }
    }

    @Inject(method = "renderItem", at = @At("HEAD"), cancellable = true)
    private void cobblemon$renderItem(LivingEntity entity, ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int light, CallbackInfo ci) {
        Identifier modelId = cobblemon$getHeldModel(stack, entity, displayContext);
        if (modelId == null) {
            return;
        }

        ItemStack renderStack = stack.copy();
        renderStack.set(DataComponents.ITEM_MODEL, modelId);

        ItemStackRenderState renderState = new ItemStackRenderState();
        this.itemModelResolver.updateForTopItem(renderState, renderStack, displayContext, entity.level(), entity, entity.getId() + displayContext.ordinal());
        renderState.submit(poseStack, submitNodeCollector, light, OverlayTexture.NO_OVERLAY, 0);
        ci.cancel();
    }

    @Redirect(method = "renderHandsWithItems", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionf;)V"))
    private void cobblemon$renderHandswithItems(PoseStack instance, Quaternionf quaternion) {
        if (!(Minecraft.getInstance().player != null && Minecraft.getInstance().player.getVehicle() instanceof OrientationControllable controllable && controllable.getOrientationController().isActive())) {
            instance.mulPose(quaternion);
        }
    }

    @Unique
    private static Identifier cobblemon$getHeldModel(ItemStack stack, LivingEntity entity, ItemDisplayContext displayContext) {
        if (!cobblemon$isHandContext(displayContext)) {
            return null;
        }

        if (stack.getItem() instanceof PokeBallItem pokeBallItem) {
            return cobblemon$asStandaloneModel(pokeBallItem.getPokeBall().getModel3d());
        }

        if (stack.getItem() instanceof PokedexItem pokedexItem) {
            boolean isScanModel = entity instanceof AbstractClientPlayer && entity.getUseItem() == stack && entity.isUsingItem();
            boolean canOpenScreen = (entity.getOffhandItem() == stack && !(entity.getMainHandItem().getItem() instanceof PokedexItem)) || entity.getMainHandItem() == stack;
            return cobblemon$asStandaloneModel(pokedexItem.getType().getItemModelPath(isScanModel ? "scanning" : (canOpenScreen ? null : "off")));
        }

        return null;
    }

    @Unique
    private static boolean cobblemon$isHandContext(ItemDisplayContext displayContext) {
        return displayContext == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
            || displayContext == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
            || displayContext == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
            || displayContext == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
    }

    @Unique
    private static Identifier cobblemon$asStandaloneModel(Identifier modelId) {
        return modelId.withPath(path -> path.startsWith("item/") ? path.substring("item/".length()) : path);
    }
}
