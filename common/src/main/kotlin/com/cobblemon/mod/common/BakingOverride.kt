/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common

import net.minecraft.resources.Identifier
import net.minecraft.client.resources.model.geometry.QuadCollection

/**
 * Contains information for forcing a model to be baked
 *
 * @param modelLocation The location of the model
 */
data class BakingOverride(
    val modelLocation: Identifier
) {
    private var bakedModel: QuadCollection = QuadCollection.EMPTY

    fun getModel(): QuadCollection {
        return bakedModel
    }

    fun setModel(model: QuadCollection) {
        bakedModel = model
    }
}
