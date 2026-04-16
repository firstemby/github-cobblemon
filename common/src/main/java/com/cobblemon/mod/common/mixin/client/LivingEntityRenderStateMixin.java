/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin.client;

import com.cobblemon.mod.common.duck.LivingEntityRenderStateDuck;
import com.cobblemon.mod.common.entity.PoseType;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LivingEntityRenderState.class)
public class LivingEntityRenderStateMixin implements LivingEntityRenderStateDuck {
    @Unique
    private @Nullable LivingEntity cobblemon$mountedEntity;

    @Unique
    private float cobblemon$mountedPartialTick;

    @Unique
    private int cobblemon$mountedSeatIndex = -1;

    @Unique
    private @Nullable String cobblemon$mountedSeatLocator;

    @Unique
    private @Nullable PoseType cobblemon$mountedPoseType;

    @Override
    public @Nullable LivingEntity cobblemon$getMountedEntity() {
        return this.cobblemon$mountedEntity;
    }

    @Override
    public void cobblemon$setMountedEntity(@Nullable LivingEntity entity) {
        this.cobblemon$mountedEntity = entity;
    }

    @Override
    public float cobblemon$getMountedPartialTick() {
        return this.cobblemon$mountedPartialTick;
    }

    @Override
    public void cobblemon$setMountedPartialTick(float partialTick) {
        this.cobblemon$mountedPartialTick = partialTick;
    }

    @Override
    public int cobblemon$getMountedSeatIndex() {
        return this.cobblemon$mountedSeatIndex;
    }

    @Override
    public void cobblemon$setMountedSeatIndex(int seatIndex) {
        this.cobblemon$mountedSeatIndex = seatIndex;
    }

    @Override
    public @Nullable String cobblemon$getMountedSeatLocator() {
        return this.cobblemon$mountedSeatLocator;
    }

    @Override
    public void cobblemon$setMountedSeatLocator(@Nullable String seatLocator) {
        this.cobblemon$mountedSeatLocator = seatLocator;
    }

    @Override
    public @Nullable PoseType cobblemon$getMountedPoseType() {
        return this.cobblemon$mountedPoseType;
    }

    @Override
    public void cobblemon$setMountedPoseType(@Nullable PoseType poseType) {
        this.cobblemon$mountedPoseType = poseType;
    }
}
