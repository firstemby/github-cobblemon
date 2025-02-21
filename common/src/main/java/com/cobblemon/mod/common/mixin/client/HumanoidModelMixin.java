/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin.client;

import com.cobblemon.mod.common.client.render.player.MountedPlayerRenderer;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;

@Mixin(HumanoidModel.class)
public class HumanoidModelMixin {
    @Unique HashMap<String, ModelPart> relevantPartsByName = new HashMap<>();

    @Shadow @Final
    public ModelPart head;
    @Shadow @Final
    public ModelPart rightArm;
    @Shadow @Final
    public ModelPart leftArm;
    @Shadow @Final
    public ModelPart rightLeg;
    @Shadow @Final
    public ModelPart leftLeg;

    @Inject(method = "setupAnim*", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/geom/ModelPart;copyFrom(Lnet/minecraft/client/model/geom/ModelPart;)V", shift = At.Shift.BEFORE))
    public void cobblemon$animateRiders(
        LivingEntity entity,
        float limbSwing,
        float limbSwingAmount,
        float ageInTicks,
        float netHeadYaw,
        float headPitch,
        CallbackInfo ci
    ) {
        if (relevantPartsByName.isEmpty()) {
            relevantPartsByName.put("arm_left", leftArm);
            relevantPartsByName.put("arm_right", rightArm);
            relevantPartsByName.put("leg_left", leftLeg);
            relevantPartsByName.put("leg_right", rightLeg);
            relevantPartsByName.put("head", head);
        }

        Entity vehicle = entity.getVehicle();
        if (vehicle instanceof PokemonEntity && entity instanceof AbstractClientPlayer) {
            MountedPlayerRenderer.INSTANCE.animate((PokemonEntity) vehicle, (AbstractClientPlayer) entity, relevantPartsByName, netHeadYaw, headPitch, ageInTicks, limbSwing, limbSwingAmount);
        }
    }
}