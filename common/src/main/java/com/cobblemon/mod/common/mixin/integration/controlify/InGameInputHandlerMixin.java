package com.cobblemon.mod.common.mixin.integration.controlify;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.integration.controlify.CobblemonControlifyEntrypoint;
import com.cobblemon.mod.common.client.keybind.keybinds.PartySendBinding;
import com.cobblemon.mod.common.pokedex.scanner.PokedexUsageContext;
import dev.isxander.controlify.controller.ControllerEntity;
import dev.isxander.controlify.ingame.InGameInputHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameInputHandler.class)
public class InGameInputHandlerMixin {
    @Shadow @Final private ControllerEntity controller;

    @Redirect(
            method = "handleKeybinds()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Inventory;swapPaint(D)V"
            )
    )
    public void cobblemon$onHotbarSwitch(Inventory instance, double direction) {
        PokedexUsageContext usageContext = CobblemonClient.INSTANCE.getPokedexUsageContext();
        if (usageContext.getScanningGuiOpen()) {
            usageContext.adjustZoom(direction);
            return;
        }

        if (CobblemonControlifyEntrypoint.Companion.getSEND_OUT_POKEMON().on(controller).digitalNow()) {
            CobblemonClient.INSTANCE.getStorage().shiftSelected(direction < 0);
            PartySendBinding.INSTANCE.actioned();
            return;
        }

        instance.swapPaint(direction);
    }
}
