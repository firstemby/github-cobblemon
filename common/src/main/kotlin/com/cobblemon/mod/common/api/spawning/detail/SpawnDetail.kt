/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.spawning.detail

import com.bedrockk.molang.runtime.struct.QueryStruct
import com.bedrockk.molang.runtime.value.DoubleValue
import com.bedrockk.molang.runtime.value.StringValue
import com.cobblemon.mod.common.Cobblemon.LOGGER
import com.cobblemon.mod.common.api.ModDependant
import com.cobblemon.mod.common.api.molang.MoLangFunctions.queryStructOf
import com.cobblemon.mod.common.api.spawning.SpawnBucket
import com.cobblemon.mod.common.api.spawning.condition.CompositeSpawningCondition
import com.cobblemon.mod.common.api.spawning.condition.SpawningCondition
import com.cobblemon.mod.common.api.spawning.context.RegisteredSpawningContext
import com.cobblemon.mod.common.api.spawning.context.SpawningContext
import com.cobblemon.mod.common.api.spawning.multiplier.WeightMultiplier
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.util.asArrayValue
import com.cobblemon.mod.common.util.asTranslated
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer

/**
 * A spawnable unit in the Best Spawner API. This is extended for any kind of entity
 * you want to spawn.
 *
 * @author Hiroku
 * @since January 31st, 2022
 */
abstract class SpawnDetail : ModDependant {
    companion object {
        val spawnDetailTypes = mutableMapOf<String, RegisteredSpawnDetail<*>>()
        fun <T : SpawnDetail> registerSpawnType(name: String, detailClass: Class<T>) {
            spawnDetailTypes[name] = RegisteredSpawnDetail(detailClass)
        }
    }

    abstract val type: String
    var id = ""
    var displayName: String? =  null
    lateinit var context: RegisteredSpawningContext<*>
    var bucket = SpawnBucket("", 0F)
    var conditions = mutableListOf<SpawningCondition<*>>()
    var anticonditions = mutableListOf<SpawningCondition<*>>()
    var compositeCondition: CompositeSpawningCondition? = null
    var weightMultipliers = mutableListOf<WeightMultiplier>()
    var width = -1
    var height = -1

    var weight = -1F
    var percentage = -1F

    var labels = mutableListOf<String>()

    /**
     * This is calculated when the server starts. It is a set of all biome identifiers in which this spawn
     * is possible. It is used as part of the [com.cobblemon.mod.common.api.spawning.condition.BiomePrecalculation].
     */
    @Transient
    val validBiomes = mutableSetOf<ResourceLocation>()

    @Transient
    val struct: QueryStruct = queryStructOf(
        "weight" to { DoubleValue(weight) },
        "percentage" to { DoubleValue(percentage) },
        "id" to { StringValue(id) },
        "bucket" to { StringValue(bucket.name) },
        "width" to { DoubleValue(width.toDouble()) },
        "height" to { DoubleValue(height.toDouble()) },
        "context" to { StringValue(context.name) },
        "labels" to { labels.asArrayValue { StringValue(it) } }
    )

    override var neededInstalledMods = listOf<String>()
    override var neededUninstalledMods = listOf<String>()

    open fun autoLabel() {}

    open fun getName() = displayName?.asTranslated() ?: id.text()

    open fun onServerLoad(server: MinecraftServer) {
        val biomeRegistry = server.registryAccess().registryOrThrow(Registries.BIOME)
        validBiomes.clear()

        // Calculate in advance what biomes of this world the spawn detail is valid for.
        biomeRegistry.holders().forEach { holder ->
            val key = holder.unwrapKey().orElse(null) ?: return@forEach
            if (conditions.isEmpty() || conditions.any { it.biomes == null || it.biomes!!.isEmpty() || it.biomes!!.any { it.fits(holder) } }) {
                if (anticonditions.isEmpty() || anticonditions.none { it.biomes != null && it.biomes!!.any { it.fits(holder) } }) {
                    if (compositeCondition?.isBiomeValid(holder) != false) {
                        validBiomes.add(key.location())
                    }
                }
            }
        }
    }

    open fun isSatisfiedBy(ctx: SpawningContext): Boolean {
        if (!ctx.preFilter(this)) {
            return false
        } else if (conditions.isNotEmpty() && conditions.none { it.isSatisfiedBy(ctx) }) {
            return false
        } else if (anticonditions.isNotEmpty() && anticonditions.any { it.isSatisfiedBy(ctx) }) {
            return false
        } else if (compositeCondition?.satisfiedBy(ctx) == false) {
            return false
        } else if (!ctx.postFilter(this)) {
            return false
        }

        return true
    }

    open fun isValid(): Boolean {
        var containsNullValues = false
        if (conditions.isNotEmpty() && conditions.any { !it.isValid() }) {
            containsNullValues = true
            LOGGER.error("Spawn Detail with id $id is invalid as it contains invalid values in its conditions (commonly caused by trailing comma in biomes or other arrays)")
        }
        if (anticonditions.isNotEmpty() && anticonditions.any { !it.isValid() }) {
            containsNullValues = true
            LOGGER.error("Spawn Detail with id $id is invalid as it contains invalid values in its anticonditions (commonly caused by trailing comma in biomes or other arrays)")
        }
        return super.isModDependencySatisfied() && !containsNullValues
    }

    abstract fun doSpawn(ctx: SpawningContext): SpawnAction<*>
}
