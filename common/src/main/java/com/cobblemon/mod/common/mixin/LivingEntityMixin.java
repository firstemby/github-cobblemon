/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin;

import com.cobblemon.mod.common.OrientationControllable;
import com.cobblemon.mod.common.api.orientation.OrientationController;
import com.cobblemon.mod.common.api.pokemon.effect.ShoulderEffectRegistry;
import com.cobblemon.mod.common.api.riding.Seat;
import com.cobblemon.mod.common.client.entity.PokemonClientDelegate;
import com.cobblemon.mod.common.client.render.MatrixWrapper;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity implements OrientationControllable {

    public LivingEntityMixin(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Unique
    private final OrientationController cobblemon$orientationController = new OrientationController((LivingEntity) (Object) this);

    public OrientationController getOrientationController() {
        return cobblemon$orientationController;
    }

    @Inject(method = "onEffectRemoved", at = @At(value = "TAIL"))
    private void cobblemon$onEffectRemoved(MobEffectInstance effect, CallbackInfo ci) {
        final LivingEntity entity = (LivingEntity) (Object) this;
        if (entity instanceof ServerPlayer) {
            ShoulderEffectRegistry.INSTANCE.onEffectEnd((ServerPlayer) entity);
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void cobblemon$updateRenderOrientation(CallbackInfo ci) {
        this.cobblemon$orientationController.tick();
    }

    @Override
    public void absMoveTo(double x, double y, double z, float yaw, float pitch) {
        if (cobblemon$orientationController.getActive()) {
            this.absMoveTo(x, y, z);
            this.setYRot(yaw % 360.0f);
            this.setXRot(pitch % 360.0f);
            this.yRotO = this.getYRot();
            this.xRotO = this.getXRot();
        } else {
            this.absMoveTo(x, y, z);
            this.absRotateTo(yaw, pitch);
        }
    }

}
