/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client

import com.cobblemon.mod.common.BakingOverride
import com.cobblemon.mod.common.Cobblemon.LOGGER
import com.cobblemon.mod.common.util.cobblemonResource
import java.util.concurrent.ConcurrentHashMap
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.block.dispatch.BlockModelRotation
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart
import net.minecraft.client.resources.model.ModelBaker
import net.minecraft.client.resources.model.ModelDebugName
import net.minecraft.client.resources.model.ModelDiscovery
import net.minecraft.client.resources.model.cuboid.MissingCuboidModel
import net.minecraft.client.resources.model.cuboid.CuboidModel
import net.minecraft.client.resources.model.geometry.BakedQuad
import net.minecraft.client.resources.model.geometry.QuadCollection
import net.minecraft.client.resources.model.sprite.Material
import net.minecraft.client.resources.model.sprite.MaterialBaker
import net.minecraft.client.renderer.texture.TextureAtlas
import net.minecraft.data.AtlasIds
import net.minecraft.core.Direction
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.ResourceManager
import org.joml.Vector3fc

/**
 * The purpose of this class is to hold models that we want baked, but aren't associated with
 * any block state. Actual baking happens during client asset reload.
 */
object CobblemonBakingOverrides {
    val models = mutableListOf<BakingOverride>()

    // Blocks
    val RESTORATION_TANK_FLUID_BUBBLING = registerOverride(
        cobblemonResource("block/restoration_tank_fluid_bubbling")
    )
    val RESTORATION_TANK_FLUID_CHUNKED_1 = registerOverride(
        cobblemonResource("block/restoration_tank_fluid_chunked_1")
    )
    val RESTORATION_TANK_FLUID_CHUNKED_2 = registerOverride(
        cobblemonResource("block/restoration_tank_fluid_chunked_2")
    )
    val RESTORATION_TANK_FLUID_CHUNKED_3 = registerOverride(
        cobblemonResource("block/restoration_tank_fluid_chunked_3")
    )
    val RESTORATION_TANK_FLUID_CHUNKED_4 = registerOverride(
        cobblemonResource("block/restoration_tank_fluid_chunked_4")
    )
    val RESTORATION_TANK_FLUID_CHUNKED_5 = registerOverride(
        cobblemonResource("block/restoration_tank_fluid_chunked_5")
    )
    val RESTORATION_TANK_FLUID_CHUNKED_6 = registerOverride(
        cobblemonResource("block/restoration_tank_fluid_chunked_6")
    )
    val RESTORATION_TANK_FLUID_CHUNKED_7 = registerOverride(
        cobblemonResource("block/restoration_tank_fluid_chunked_7")
    )
    val RESTORATION_TANK_FLUID_CHUNKED_8 = registerOverride(
        cobblemonResource("block/restoration_tank_fluid_chunked_8")
    )
    val RESTORATION_TANK_CONNECTOR = registerOverride(
        cobblemonResource("block/restoration_tank_connector")
    )

    val COARSE_MULCH = registerOverride(
        cobblemonResource("block/coarse_mulch")
    )

    val GROWTH_MULCH = registerOverride(
        cobblemonResource("block/growth_mulch")
    )

    val HUMID_MULCH = registerOverride(
        cobblemonResource("block/humid_mulch")
    )

    val LOAMY_MULCH = registerOverride(
        cobblemonResource("block/loamy_mulch")
    )

    val PEAT_MULCH = registerOverride(
        cobblemonResource("block/peat_mulch")
    )

    val RICH_MULCH = registerOverride(
        cobblemonResource("block/rich_mulch")
    )

    val SANDY_MULCH = registerOverride(
        cobblemonResource("block/sandy_mulch")
    )

    val SURPRISE_MULCH = registerOverride(
        cobblemonResource("block/surprise_mulch")
    )

    val MULCH = registerOverride(
        cobblemonResource("block/mulch")
    )

    fun registerOverride(modelLocation: Identifier): BakingOverride {
        val result = BakingOverride(modelLocation)
        models.add(result)
        return result
    }

    fun reload(resourceManager: ResourceManager) {
        val atlasManager = Minecraft.getInstance().atlasManager
        val blockAtlas = atlasManager.getAtlasOrThrow(AtlasIds.BLOCKS)
        val itemAtlas = atlasManager.getAtlasOrThrow(AtlasIds.ITEMS)
        val missingBlockModel = MissingCuboidModel.missingModel()
        val missingSprite = blockAtlas.missingSprite()
        val missingBlockModelPart = object : BlockStateModelPart {
            override fun getQuads(direction: Direction?): List<BakedQuad> = emptyList()
            override fun useAmbientOcclusion(): Boolean = false
            override fun particleMaterial() = Material.Baked(missingSprite, false)
            override fun materialFlags() = 0
        }

        val parsedModels: Map<Identifier, net.minecraft.client.resources.model.UnbakedModel> = models.associate { bakingOverride ->
            val resourceLocation = bakingOverride.modelLocation.withPath("models/${bakingOverride.modelLocation.path}.json")
            val model = try {
                resourceManager.getResourceOrThrow(resourceLocation).open().reader().use(CuboidModel::fromStream)
            } catch (exception: Exception) {
                LOGGER.warn("Failed to load baked model {}", resourceLocation, exception)
                missingBlockModel
            }
            bakingOverride.modelLocation to model
        }

        val discovery = ModelDiscovery(parsedModels, missingBlockModel)
        parsedModels.forEach { (identifier, model) ->
            discovery.addSpecialModel(identifier, model)
        }
        val resolvedModels = discovery.resolve()

        val vectorCache = ConcurrentHashMap<Vector3fc, Vector3fc>()
        val materialInfoCache = ConcurrentHashMap<BakedQuad.MaterialInfo, BakedQuad.MaterialInfo>()
        val interner = object : ModelBaker.Interner {
            override fun vector(vector: Vector3fc): Vector3fc {
                return vectorCache.computeIfAbsent(vector) { it }
            }

            override fun materialInfo(material: BakedQuad.MaterialInfo): BakedQuad.MaterialInfo {
                return materialInfoCache.computeIfAbsent(material) { it }
            }
        }

        val operationCache = ConcurrentHashMap<ModelBaker.SharedOperationKey<*>, Any>()
        val materialBaker = object : MaterialBaker {
            private val blockMissing = Material.Baked(missingSprite, false)
            private val bakedMaterials = ConcurrentHashMap<Material, Material.Baked>()

            override fun get(material: Material, name: ModelDebugName): Material.Baked {
                return bakedMaterials.computeIfAbsent(material) {
                    val itemSprite = itemAtlas.getSprite(material.sprite())
                    if (itemSprite !== itemAtlas.missingSprite()) {
                        Material.Baked(itemSprite, material.forceTranslucent())
                    } else {
                        val blockSprite = blockAtlas.getSprite(material.sprite())
                        if (blockSprite !== blockAtlas.missingSprite()) {
                            Material.Baked(blockSprite, material.forceTranslucent())
                        } else {
                            blockMissing
                        }
                    }
                }
            }

            override fun reportMissingReference(reference: String, name: ModelDebugName): Material.Baked {
                LOGGER.warn("Missing texture reference {} in model {}", reference, name.debugName())
                return blockMissing
            }
        }

        val modelBaker = object : ModelBaker {
            override fun getModel(identifier: Identifier) =
                resolvedModels[identifier] ?: resolvedModels[MissingCuboidModel.LOCATION] ?: error("Missing baked model $identifier")

            override fun missingBlockModelPart() = missingBlockModelPart

            override fun materials() = materialBaker

            override fun interner() = interner

            @Suppress("UNCHECKED_CAST")
            override fun <T : Any> compute(operation: ModelBaker.SharedOperationKey<T>): T {
                return operationCache.computeIfAbsent(operation) { operation.compute(this) } as T
            }
        }

        models.forEach { bakingOverride ->
            val resolvedModel = resolvedModels[bakingOverride.modelLocation]
            if (resolvedModel == null) {
                LOGGER.warn("No resolved model found for {}", bakingOverride.modelLocation)
                bakingOverride.setModel(QuadCollection.EMPTY)
                return@forEach
            }

            try {
                bakingOverride.setModel(
                    resolvedModel.bakeTopGeometry(
                        resolvedModel.getTopTextureSlots(),
                        modelBaker,
                        BlockModelRotation.IDENTITY
                    )
                )
            } catch (exception: Exception) {
                LOGGER.warn("Failed to bake override model {}", bakingOverride.modelLocation, exception)
                bakingOverride.setModel(QuadCollection.EMPTY)
            }
        }
    }
}
