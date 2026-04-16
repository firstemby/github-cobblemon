/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.CobblemonVillagerProfessions;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import kotlin.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.monster.zombie.ZombieVillagerModel;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.client.renderer.entity.state.ZombieVillagerRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderLayer.class) //TODO CHECK IF THIS IS NEEDED
public abstract class VillagerProfessionLayerMixin {
    @Inject(method = "renderColoredCutoutModel", at = @At(value = "HEAD"), cancellable = true)
    private static <S extends LivingEntityRenderState> void cobblemon$renderVillagerProfessionLayer(Model<? super S> model,
                                                                                                    Identifier texture,
                                                                                                    PoseStack poseStack,
                                                                                                    SubmitNodeCollector submitNodeCollector,
                                                                                                    int i, S livingEntityRenderState,
                                                                                                    int j, int k, CallbackInfo ci) {
        if (texture.getNamespace().equals(Cobblemon.MODID)) {
            VillagerProfession profession = null;
            boolean isZombie = false;

            if (livingEntityRenderState instanceof VillagerRenderState villagerModel)
                profession = villagerModel.getVillagerData().profession().value();
            if (livingEntityRenderState instanceof ZombieVillagerRenderState zombieVillagerModel) {
                profession = zombieVillagerModel.getVillagerData().profession().value();
                isZombie = true;
            }

            Pair<String, String[]> override = CobblemonVillagerProfessions.INSTANCE.getNameTagOverride(profession);
            if (profession != null && override != null) {
                String entityName = ChatFormatting.stripFormatting(livingEntityRenderState.nameTag.getString());
                boolean nameMatches = false;
                for (String name: override.getSecond())
                    if (entityName.endsWith(name)) {
                        nameMatches = true;
                        break;
                    }
                var test = (TranslatableContents) livingEntityRenderState.nameTag.getContents();
                if (nameMatches && texture.equals(getProfessionIdentifier(texture.getNamespace(), extractProfessionFromName(test.getKey()).getPath(), isZombie))) {
                    Identifier textureLocation = getProfessionIdentifier(texture.getNamespace(), override.getFirst(), isZombie);
                    submitNodeCollector.order(k).submitModel(model, livingEntityRenderState,
                            poseStack, RenderTypes.armorCutoutNoCull(textureLocation), i,
                            LivingEntityRenderer.getOverlayCoords(livingEntityRenderState, 0.0F), j,
                            null, livingEntityRenderState.outlineColor,
                            null);
                    ci.cancel();
                }
            }
        }
    }

    private static Identifier extractProfessionFromName(String translationKey) {
        // e.g. "entity.cobblemon.villager.nurse_joy"

        String prefix = "entity.";
        String middle = ".villager.";

        if (translationKey.startsWith(prefix)) {
            String remainder = translationKey.substring(prefix.length());
            int split = remainder.indexOf(middle);

            if (split != -1) {
                String namespace = remainder.substring(0, split);
                String path = remainder.substring(split + middle.length());

                return Identifier.fromNamespaceAndPath(namespace, path);
            }
        }

        throw new RuntimeException("Failed to extract profession from translation key: " + translationKey);
    }

    private static Identifier getProfessionIdentifier(String namespace, String profession, Boolean isZombie) {
        return Identifier.fromNamespaceAndPath(namespace, "textures/entity/" + (isZombie ? "zombie_villager" : "villager") + "/profession/" + profession + ".png");
    }
}
