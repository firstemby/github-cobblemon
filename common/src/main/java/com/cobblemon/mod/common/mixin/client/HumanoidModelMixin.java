/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin.client;

import com.cobblemon.mod.common.client.render.player.MountedPlayerRenderer;
import com.cobblemon.mod.common.duck.AvatarRenderStateDuck;
import com.cobblemon.mod.common.duck.LivingEntityRenderStateDuck;
import com.cobblemon.mod.common.entity.PoseType;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
public class HumanoidModelMixin {
    @Shadow @Final
    public ModelPart head;
    @Shadow @Final
    public ModelPart hat;
    @Shadow @Final
    public ModelPart rightArm;
    @Shadow @Final
    public ModelPart leftArm;
    @Shadow @Final
    public ModelPart rightLeg;
    @Shadow @Final
    public ModelPart leftLeg;
    @Shadow @Final
    public ModelPart body;

    @Inject(
        method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V",
        at = @At("TAIL")
    )
    public void cobblemon$animateRiders(HumanoidRenderState renderState, CallbackInfo ci) {
        MountedPlayerRenderer.shouldApplyRootAnimation = false;

        if (!(renderState instanceof AvatarRenderState avatarRenderState)) {
            return;
        }

        AvatarRenderStateDuck duck = (AvatarRenderStateDuck) avatarRenderState;
        LivingEntityRenderStateDuck livingDuck = (LivingEntityRenderStateDuck) avatarRenderState;
        AbstractClientPlayer player = duck.cobblemon$getMountedPlayer();
        PokemonEntity pokemonEntity = duck.cobblemon$getMountedPokemon();
        if (player == null || pokemonEntity == null) {
            return;
        }

        int seatIndex = livingDuck.cobblemon$getMountedSeatIndex();
        String seatLocator = livingDuck.cobblemon$getMountedSeatLocator();
        PoseType poseType = livingDuck.cobblemon$getMountedPoseType();

        if (seatIndex < 0 || poseType == null) {
            return;
        }

        MountedPlayerRenderer.relevantPartsByName.put("arm_left", leftArm);
        MountedPlayerRenderer.relevantPartsByName.put("arm_right", rightArm);
        MountedPlayerRenderer.relevantPartsByName.put("leg_left", leftLeg);
        MountedPlayerRenderer.relevantPartsByName.put("leg_right", rightLeg);
        MountedPlayerRenderer.relevantPartsByName.put("head", head);
        MountedPlayerRenderer.relevantPartsByName.put("hat", hat);
        MountedPlayerRenderer.relevantPartsByName.put("torso", body);

        Entity vehicle = player.getVehicle();
        if (!(vehicle instanceof PokemonEntity)) {
            return;
        }

        var shouldRotatePlayerHead = pokemonEntity.ifRidingAvailableSupply(false, (behaviour, settings, state) ->
                behaviour.shouldRotateRiderHead(settings, state, pokemonEntity)
        );

        float netHeadYaw = avatarRenderState.yRot;
        float headPitch = avatarRenderState.xRot;

        if (!shouldRotatePlayerHead) {
            netHeadYaw = 0f;
            headPitch = 0f;
            this.head.yRot = 0f;
            this.head.xRot = 0f;
        }

        MountedPlayerRenderer.INSTANCE.animate(
            (HumanoidModel<?>)(Object)this,
            pokemonEntity,
            player,
            avatarRenderState,
            seatIndex,
            seatLocator,
            poseType,
            netHeadYaw,
            headPitch,
            avatarRenderState.ageInTicks,
            avatarRenderState.walkAnimationPos,
            avatarRenderState.walkAnimationSpeed
        );
        MountedPlayerRenderer.shouldApplyRootAnimation = true;

    }
}
