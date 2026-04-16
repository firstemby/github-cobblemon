/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.duck;

import com.cobblemon.mod.common.entity.PoseType;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

public interface LivingEntityRenderStateDuck {
    @Nullable LivingEntity cobblemon$getMountedEntity();
    void cobblemon$setMountedEntity(@Nullable LivingEntity entity);

    float cobblemon$getMountedPartialTick();
    void cobblemon$setMountedPartialTick(float partialTick);

    int cobblemon$getMountedSeatIndex();
    void cobblemon$setMountedSeatIndex(int seatIndex);

    @Nullable String cobblemon$getMountedSeatLocator();
    void cobblemon$setMountedSeatLocator(@Nullable String seatLocator);

    @Nullable PoseType cobblemon$getMountedPoseType();
    void cobblemon$setMountedPoseType(@Nullable PoseType poseType);
}
