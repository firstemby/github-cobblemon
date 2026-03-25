/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin;

import com.cobblemon.mod.common.block.entity.TMShelfBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NoteBlock.class)
public abstract class NoteBlockMixin {
    @Inject(method = "triggerEvent", at = @At("HEAD"), cancellable = true)
    private void cobblemon$triggerTMShelfSequencer(BlockState state, Level level, BlockPos pos, int type, int data, CallbackInfoReturnable<Boolean> cir) {
        final BlockPos shelfPos = pos.below();
        final BlockEntity blockEntity = level.getBlockEntity(shelfPos);
        if (!(blockEntity instanceof TMShelfBlockEntity tmShelfBlockEntity)) {
            return;
        }

        if (!level.isClientSide) {
            final BlockPos instrumentPos = shelfPos.below();
            final SoundEvent soundEvent;
            if (level.getBlockState(instrumentPos).isAir()) {
                soundEvent = SoundEvents.NOTE_BLOCK_BIT.value();
            } else {
                final NoteBlockInstrument instrument = level.getBlockState(instrumentPos).instrument();
                final Holder<SoundEvent> holder = instrument.getSoundEvent();
                soundEvent = holder.value();
            }
            tmShelfBlockEntity.onNoteBlockPulse(level, pos, soundEvent);
        }

        // Suppress vanilla note-block playback while it is on top of a TM Shelf.
        cir.setReturnValue(false);
    }
}
