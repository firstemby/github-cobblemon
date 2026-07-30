/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.fabric.data.generator

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.fabric.data.generator.providers.TypeGemsLootTableProvider
import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator

class CobblemonFabricDataGenerator : DataGeneratorEntrypoint {
    override fun onInitializeDataGenerator(generator: FabricDataGenerator?) {
        if (generator == null) {
            Cobblemon.LOGGER.error("Fabric Data Generator couldn't initialize")
            return
        }

        val pack = generator.createPack()

        pack.addProvider(::TypeGemsLootTableProvider);
    }
}