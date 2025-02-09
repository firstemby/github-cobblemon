/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin;

import com.cobblemon.mod.common.Rollable;
import com.cobblemon.mod.common.api.riding.Rideable;
import com.cobblemon.mod.common.api.riding.Seat;
import com.cobblemon.mod.common.client.entity.PokemonClientDelegate;
import com.cobblemon.mod.common.client.render.ClientPlayerIcon;
import com.cobblemon.mod.common.client.render.player.PlayerRendererHook;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.cobblemon.mod.common.client.render.MatrixWrapper;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.mojang.authlib.minecraft.client.MinecraftClient;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.loader.impl.lib.sat4j.core.Vec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public class PlayerRendererMixin {
    private static boolean disableRollableRenderDebug = false;

    @WrapMethod(method = "render")
    public void applyRotation(AbstractClientPlayer player, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource multiBufferSource, int i, Operation<Integer> original) {
        PlayerRendererHook.INSTANCE.before(player, entityYaw, partialTicks, poseStack, multiBufferSource, i);

        original.call(player, entityYaw, partialTicks, poseStack, multiBufferSource, i);

        ClientPlayerIcon.Companion.onRenderPlayer(player);
    }
}
