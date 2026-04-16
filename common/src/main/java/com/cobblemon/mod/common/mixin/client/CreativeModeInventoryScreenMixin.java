/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin.client;

import com.mojang.blaze3d.platform.cursor.CursorType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeModeInventoryScreenMixin {

    @Redirect(
        method = "renderTabButton",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;requestCursor(Lcom/mojang/blaze3d/platform/cursor/CursorType;)V"
        )
    )
    private void cobblemon$skipCreativeTabCursor(GuiGraphicsExtractor guiGraphics, CursorType cursorType, GuiGraphicsExtractor context, int mouseX, int mouseY, CreativeModeTab tab) {
        // 1.21.1 had no special cursor on creative tab hover; keep the arrow cursor for tab icons.
    }
}
