/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin.client;

import com.cobblemon.mod.common.client.render.entity.MountedLivingEntityRenderer;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin<T extends LivingEntity, S extends LivingEntityRenderState, M extends EntityModel<S>> {

    @Inject(
            method = "setupRotations",
            at = @At("HEAD")
    )
    private void cobblemon$modifyRoll(S livingEntityRenderState, PoseStack poseStack, float yBodyRot, float scale, CallbackInfo ci) {
        if (livingEntityRenderState.passengerOffset != null) {
            //TODO NEED TO INJECT MORE INTO STATE
            //var vehicle = entity.getVehicle();
            if (false) {
                MountedLivingEntityRenderer.INSTANCE.render(livingEntityRenderState, poseStack, yBodyRot, scale);
            }
        }
    }
}
