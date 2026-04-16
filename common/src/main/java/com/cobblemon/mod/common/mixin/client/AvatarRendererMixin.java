/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin.client;

import com.cobblemon.mod.common.duck.AvatarRenderStateDuck;
import com.cobblemon.mod.common.duck.LivingEntityRenderStateDuck;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.phys.Vec3;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void cobblemon$captureMountedPlayer(Avatar avatar, AvatarRenderState state, float partialTick, CallbackInfo ci) {
        AvatarRenderStateDuck duck = (AvatarRenderStateDuck) state;
        LivingEntityRenderStateDuck livingDuck = (LivingEntityRenderStateDuck) state;
        if (avatar instanceof AbstractClientPlayer player && player.getVehicle() instanceof PokemonEntity pokemonEntity) {
            duck.cobblemon$setMountedPlayer(player);
            duck.cobblemon$setMountedPokemon(pokemonEntity);
            state.isCrouching = false;
        } else {
            duck.cobblemon$setMountedPlayer(null);
            duck.cobblemon$setMountedPokemon(null);
        }
    }

    @ModifyExpressionValue(
        method = "getRenderOffset(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)Lnet/minecraft/world/phys/Vec3;",
        at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;isCrouching:Z", opcode = Opcodes.GETFIELD)
    )
    private boolean cobblemon$disableMountedCrouchOffset(boolean isCrouching, AvatarRenderState state) {
        AvatarRenderStateDuck duck = (AvatarRenderStateDuck) state;
        if (duck.cobblemon$getMountedPlayer() != null && duck.cobblemon$getMountedPokemon() != null) {
            return false;
        }
        return isCrouching;
    }

}
