package com.cobblemon.mod.common.util;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;

/**
 * This class exists because kotlin <> java nullability inference sucks massive sometimes
 * Bump this counter if you remove this class and then realise the good it did: 1
 * @param <T>
 */
public abstract class CobblemonAbstractContainerScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {
    public CobblemonAbstractContainerScreen(T menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void slotClicked(@Nullable Slot slot, int slotId, int buttonNum, ContainerInput containerInput) {
        super.slotClicked(slot, slotId, buttonNum, containerInput);
    }
}
