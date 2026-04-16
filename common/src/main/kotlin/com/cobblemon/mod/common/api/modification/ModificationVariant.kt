/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.modification

import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

data class ModificationVariant(
    @Transient
    var identifier: ResourceLocation = ResourceLocation.withDefaultNamespace("missing"),
    val parent: ResourceLocation,
    val model: String,
    val id: Int = 0,
    val shape: List<List<Double>>? = emptyList(),
    val size: ModificationVariantSize? = null
) {
    fun resolvedModelIdentifier(): ResourceLocation {
        return ResourceLocation.tryParse(model) ?: ResourceLocation.fromNamespaceAndPath(parent.namespace, model)
    }

    fun resolvedBlockModelIdentifier(): ResourceLocation {
        val resolved = resolvedModelIdentifier()
        return if (resolved.path.startsWith("block/")) {
            resolved
        } else {
            ResourceLocation.fromNamespaceAndPath(resolved.namespace, "block/${resolved.path}")
        }
    }

    fun createShape(): VoxelShape? {
        if (!shape.isNullOrEmpty()) {
            var combined = Shapes.empty()
            for (entry in shape) {
                if (entry.size != 6) {
                    continue
                }

                combined = Shapes.or(
                    combined,
                    Shapes.box(
                        entry[0] / 16.0,
                        entry[1] / 16.0,
                        entry[2] / 16.0,
                        entry[3] / 16.0,
                        entry[4] / 16.0,
                        entry[5] / 16.0
                    )
                )
            }

            return if (combined.isEmpty) null else combined
        }

        val sized = size ?: return null
        return Shapes.box(
            sized.offsetX / 16.0,
            sized.offsetY / 16.0,
            sized.offsetZ / 16.0,
            (sized.offsetX + sized.width) / 16.0,
            (sized.offsetY + sized.height) / 16.0,
            (sized.offsetZ + sized.depth) / 16.0
        )
    }
}

data class ModificationVariantSize(
    val width: Double = 16.0,
    val height: Double = 16.0,
    val depth: Double = 16.0,
    val offsetX: Double = 0.0,
    val offsetY: Double = 0.0,
    val offsetZ: Double = 0.0
)
