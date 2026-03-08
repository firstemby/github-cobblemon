/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common

import net.minecraft.client.Minecraft
import net.minecraft.client.model.geom.ModelLayerLocation
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.resources.Identifier

/**
 * Contains information for forcing a model to be baked
 *
 * @param modelLocation The location of the model
 * @param modelIdentifier The identifier that the BakedModel will be registered to
 */
data class BakingOverride(
    val modelLocation: Identifier,
    val modelIdentifier: ModelLayerLocation
) {
    fun getModel(): ModelPart {
        return Minecraft.getInstance().modelManager.entityModels().get().bakeLayer(modelIdentifier)
    }
}
