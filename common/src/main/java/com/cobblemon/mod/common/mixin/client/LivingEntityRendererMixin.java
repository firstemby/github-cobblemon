/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin.client;

import com.cobblemon.mod.common.client.render.ClientPlayerIcon;
import com.cobblemon.mod.common.client.render.entity.MountedLivingEntityRenderer;
import com.cobblemon.mod.common.duck.LivingEntityRenderStateDuck;
import com.cobblemon.mod.common.entity.PoseType;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.item.WearableItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {
    @Shadow @Final protected ItemModelResolver itemModelResolver;

    @Inject(
            method = "extractRenderState",
            at = @At("TAIL")
    )
    private void cobblemon$captureMountedEntity(LivingEntity entity, LivingEntityRenderState state, float partialTick, CallbackInfo ci) {
        LivingEntityRenderStateDuck duck = (LivingEntityRenderStateDuck) state;
        if (entity.isPassenger() && entity.getVehicle() instanceof PokemonEntity) {
            PokemonEntity pokemonEntity = (PokemonEntity) entity.getVehicle();
            int seatIndex = pokemonEntity.getPassengers().indexOf(entity);
            String seatLocator = null;
            PoseType poseType = pokemonEntity.getCurrentPoseType();
            if (seatIndex >= 0 && seatIndex < pokemonEntity.getSeats().size()) {
                seatLocator = pokemonEntity.getSeats().get(seatIndex).getLocator();
                if (seatLocator == null) {
                    seatLocator = "seat_" + (seatIndex + 1);
                }
            } else {
                seatIndex = -1;
            }

            duck.cobblemon$setMountedEntity(entity);
            duck.cobblemon$setMountedPartialTick(partialTick);
            duck.cobblemon$setMountedSeatIndex(seatIndex);
            duck.cobblemon$setMountedSeatLocator(seatLocator);
            duck.cobblemon$setMountedPoseType(poseType);
        } else {
            duck.cobblemon$setMountedEntity(null);
            duck.cobblemon$setMountedPartialTick(0.0F);
            duck.cobblemon$setMountedSeatIndex(-1);
            duck.cobblemon$setMountedSeatLocator(null);
            duck.cobblemon$setMountedPoseType(null);
        }

        ItemStack headItem = entity.getItemBySlot(EquipmentSlot.HEAD);
        if (headItem.getItem() instanceof WearableItem wearableItem) {
            ItemStack renderHeadItem = headItem.copy();
            renderHeadItem.set(DataComponents.ITEM_MODEL, wearableItem.getModel3d().withPath(path -> path.startsWith("item/") ? path.substring("item/".length()) : path));
            this.itemModelResolver.updateForLiving(state.headItem, renderHeadItem, ItemDisplayContext.HEAD, entity);
        }
    }

    @Inject(
            method = "setupRotations",
            at = @At("HEAD")
    )
    private void cobblemon$modifyRoll(LivingEntityRenderState state, PoseStack poseStack, float bodyRot, float scale, CallbackInfo ci) {
        LivingEntityRenderStateDuck duck = (LivingEntityRenderStateDuck) state;
        LivingEntity entity = duck.cobblemon$getMountedEntity();
        if (entity == null || !(entity.getVehicle() instanceof PokemonEntity pokemonEntity)) {
            return;
        }

        MountedLivingEntityRenderer.INSTANCE.render(
            entity,
            pokemonEntity,
            poseStack,
            state.walkAnimationPos,
            bodyRot,
            duck.cobblemon$getMountedPartialTick(),
            scale
        );
    }

    @Inject(
        method = "submit",
        at = @At("TAIL")
    )
    private void cobblemon$renderPlayerIcon(
        LivingEntityRenderState state,
        PoseStack poseStack,
        SubmitNodeCollector submitNodeCollector,
        CameraRenderState cameraRenderState,
        CallbackInfo ci
    ) {
        if (!(state instanceof AvatarRenderState avatarState)) {
            return;
        }

        var level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        var entity = level.getEntity(avatarState.id);
        if (entity instanceof Player player) {
            ClientPlayerIcon.Companion.onRenderPlayer(player);
        }
    }
}
