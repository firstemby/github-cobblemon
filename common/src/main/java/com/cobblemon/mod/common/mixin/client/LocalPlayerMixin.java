/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin.client;

import com.cobblemon.mod.common.CobblemonNetwork;
import com.cobblemon.mod.common.OrientationControllable;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.net.messages.server.orientation.ServerboundUpdateOrientationPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.joml.Matrix3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin extends LivingEntity {

    @Unique Matrix3f cobblemon$lastOrientation;

    protected LocalPlayerMixin(EntityType<? extends LivingEntity> entityType, Level level) {
        super(entityType, level);
    }

//    @Inject(method = "sendPosition", at = @At("TAIL"))
//    private void cobblemon$updateRotationMatrix(CallbackInfo ci) {
//        if (!(this instanceof OrientationControllable controllable)) return;
//        var controller = controllable.getOrientationController();
//        if (!controller.isActive() || controller.getOrientation() == cobblemon$lastOrientation) return;
//        cobblemon$lastOrientation = controller.getOrientation() != null ? new Matrix3f(controller.getOrientation()) : null;
//        CobblemonNetwork.INSTANCE.sendToServer(new ServerboundUpdateOrientationPacket(controller.getOrientation()));
//    }

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V"))
    private void cobblemon$updateRotationMatrixPassenger(CallbackInfo ci) {
        if (!(this instanceof OrientationControllable controllable)) return;
        var controller = controllable.getOrientationController();
        if (!controller.isActive() || controller.getOrientation() == cobblemon$lastOrientation) return;
        cobblemon$lastOrientation = controller.getOrientation() != null ? new Matrix3f(controller.getOrientation()) : null;
        CobblemonNetwork.INSTANCE.sendToServer(new ServerboundUpdateOrientationPacket(controller.getOrientation()));
    }

    @Inject(method = "rideTick", at = @At("HEAD"))
    private void cobblemon$updateOrientationControllerRideTick(CallbackInfo ci) {
        if (Minecraft.getInstance().player != (Object)this) return;
        if (!(this instanceof OrientationControllable controllable)) return;
        var shouldUseCustomOrientation = cobblemon$shouldUseCustomOrientation((LocalPlayer)(Object)this);
        controllable.getOrientationController().setActive(shouldUseCustomOrientation);
    }

    @Unique
    private boolean cobblemon$shouldUseCustomOrientation(LocalPlayer player) {
        var playerVehicle = player.getVehicle();
        if (playerVehicle == null) return false;
        if (!(playerVehicle instanceof PokemonEntity pokemonEntity)) return false;
        return pokemonEntity.ifRidingAvailableSupply(false, (behaviour, settings, state) -> {
            return behaviour.shouldRoll(settings, state, pokemonEntity);
        });
    }

    @Override
    public void stopRiding() {
        super.stopRiding();
        if (Minecraft.getInstance().player != (Object)this) return;
        if (!(this instanceof OrientationControllable controllable)) return;
        controllable.getOrientationController().setActive(false);
    }
}
