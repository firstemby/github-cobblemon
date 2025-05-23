package com.cobblemon.mod.common.mixin.structure;

import com.cobblemon.mod.common.Cobblemon;

import com.google.common.collect.Iterators;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureEntityInfo;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Iterator;

@Mixin(StructureTemplate.class)
public abstract class StructureTemplateMixin {
  @ModifyExpressionValue(
    method = "placeEntities",
    at = @At(value = "INVOKE",
      target = "Ljava/util/List;iterator()Ljava/util/Iterator;"
    )) // Modifies the iterator to ignore entities that are blacklisted
  private Iterator<StructureEntityInfo> cobblemon$cancelStructureSpawn(Iterator<StructureEntityInfo> original) {
    return Iterators.filter(original, info ->
      Cobblemon.spawnConfig.isBlacklisted(info.nbt.getString("id"))
    );
  }
}
