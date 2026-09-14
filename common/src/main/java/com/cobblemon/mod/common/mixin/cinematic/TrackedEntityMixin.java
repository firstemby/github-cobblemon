/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin.cinematic;

import com.cobblemon.mod.common.api.cinematic.runtime.CinematicViewerScopedEntity;
import com.cobblemon.mod.common.mixin.accessor.ServerEntityEntityAccessor;
import com.cobblemon.mod.common.mixin.accessor.TrackedEntityAccessor;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;
import java.util.UUID;

@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class TrackedEntityMixin {

    // Cancellation must also unpair existing viewers, or they keep receiving updates after the scope narrows.
    @Inject(method = "updatePlayer", at = @At("HEAD"), cancellable = true)
    private void cobblemon$filterViewerScopedEntity(ServerPlayer player, CallbackInfo ci) {
        final TrackedEntityAccessor self = (TrackedEntityAccessor) this;
        final ServerEntity serverEntity = self.getServerEntity();
        if (serverEntity == null) return;
        final Entity entity = ((ServerEntityEntityAccessor) serverEntity).getEntity();
        if (entity == null) return;

        if (entity instanceof CinematicViewerScopedEntity scoped) {
            final Set<UUID> allowed = scoped.getCobblemonSceneViewers();
            if (allowed != null && !allowed.contains(player.getUUID())) {
                if (self.getSeenBy().remove(player.connection)) {
                    serverEntity.removePairing(player);
                }
                ci.cancel();
            }
        }
    }
}
