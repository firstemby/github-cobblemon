package com.cobblemon.mod.common.mixin;

import com.cobblemon.mod.common.block.BlockExtensions;
import com.cobblemon.mod.common.block.habitat.HabitatBlock;
import com.cobblemon.mod.common.block.habitat.HabitatBlockEntity;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ExplosionDamageCalculator.class)
public class ExplosionDamageCalculatorMixin {
    @ModifyArg(
            method = "getBlockExplosionResistance",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/lang/Math;max(FF)F"),
            index = 0
    )
    public float customBlockResistance(float resistance, @Local(argsOnly = true) BlockState state, @Local(argsOnly = true) BlockGetter reader, @Local(argsOnly = true) BlockPos pos) {
        if (state.getBlock() instanceof BlockExtensions) {
            return ((BlockExtensions) state.getBlock()).getExplosionResistance(state, reader, pos);
        }
        return resistance;
    }
}
