package com.cobblemon.mod.common.mixin;

import com.cobblemon.mod.common.CobblemonItemComponents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemModelShaper.class)
public class ItemModelShaperMixin {

    @Inject(method = "getItemModel(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/client/resources/model/BakedModel;", at = @At("HEAD"), cancellable = true)
    public void getItemModel(ItemStack stack, CallbackInfoReturnable<BakedModel> cir) {
        String eggModel = stack.get(CobblemonItemComponents.EGG_MODEL);
        if (eggModel == null) return;

        ModelResourceLocation modelResourceLocation = ModelResourceLocation.inventory(ResourceLocation.parse("cobblemon:egg/" + eggModel));
        BakedModel bakedModel = Minecraft.getInstance().getModelManager().getModel(modelResourceLocation);
        cir.setReturnValue(bakedModel);
    }

}