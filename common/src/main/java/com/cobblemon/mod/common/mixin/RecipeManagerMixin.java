package com.cobblemon.mod.common.mixin;

import com.cobblemon.mod.common.CobblemonRecipePropertySets;
import com.cobblemon.mod.common.item.crafting.brewingstand.BrewingStandRecipe;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipePropertySet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.Optional;

@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {

    @Shadow
    @Final
    private static Map<ResourceKey<RecipePropertySet>, RecipeManager.IngredientExtractor> RECIPE_PROPERTY_SETS;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void cobblemon$addPropertySets(CallbackInfo ci) {
        RECIPE_PROPERTY_SETS.put(CobblemonRecipePropertySets.INSTANCE.getBREWING_BOTTLE(), recipe -> {
            if (recipe instanceof BrewingStandRecipe brew) {
                return Optional.of(brew.getBottle());
            }
            return Optional.empty();
        });

        RECIPE_PROPERTY_SETS.put(CobblemonRecipePropertySets.INSTANCE.getBREWING_INPUT(), recipe -> {
            if (recipe instanceof BrewingStandRecipe brew) {
                return Optional.of(brew.getInput());
            }
            return Optional.empty();
        });
    }
}
