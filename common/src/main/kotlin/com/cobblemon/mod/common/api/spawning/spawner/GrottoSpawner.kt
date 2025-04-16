package com.cobblemon.mod.common.api.spawning.spawner

import com.cobblemon.mod.common.CobblemonPoiTypes
import com.cobblemon.mod.common.api.habitats.Habitats
import com.cobblemon.mod.common.api.spawning.SpawnCause
import com.cobblemon.mod.common.api.spawning.SpawnerManager
import com.cobblemon.mod.common.api.spawning.context.SpawningContext
import com.cobblemon.mod.common.api.spawning.detail.SpawnPool
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnDetail
import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail
import com.cobblemon.mod.common.api.spawning.grotto.GrottoSpawnCause
import com.cobblemon.mod.common.api.spawning.selection.SpawningSelector
import com.cobblemon.mod.common.block.entity.GrottoBlockEntity
import com.cobblemon.mod.common.util.server
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.ai.village.poi.PoiManager
import java.util.concurrent.ConcurrentHashMap

class GrottoSpawner(
        name: String,
        fallbackSpawns: SpawnPool,
        manager: SpawnerManager
) : AreaSpawner(name, fallbackSpawns, manager) {

    companion object {
        private val cache = ConcurrentHashMap<BlockPos, Pair<SpawnPool, Long>>()
        private const val CACHE_DURATION_MS = 10_000
    }

    override var ticksBetweenSpawns: Float = 120F

    // todo use this getArea to try to use a new GrottoSpawnCause and use that to send the structure center so spawns are around the structures
    override fun getArea(cause: SpawnCause): SpawningArea? {
        if (cause is GrottoSpawnCause) {
            val center = cause.structureCenter
            return SpawningArea(
                cause = cause,
                world = server()?.getLevel(cause.entityWorldId) ?: return null,
                baseX = center.x - 8,
                baseY = center.y - 4,
                baseZ = center.z - 8,
                length = 16,
                height = 8,
                width = 16
            )
        }
        return null
    }

    // todo this older getArea method made it so it was getting the area around the player as spawnable instead of around the center of the grotto
    /*override fun getArea(cause: SpawnCause): SpawningArea? {
        val entity = cause.entity ?: server()?.playerList?.players?.firstOrNull() ?: return null
        val pos = entity.blockPosition()

        return SpawningArea(
                cause = cause,
                world = entity.level() as? ServerLevel ?: return null,
                baseX = pos.x - 8,
                baseY = pos.y - 4,
                baseZ = pos.z - 8,
                length = 16,
                height = 8,
                width = 16
        )
    }*/

    override fun run(cause: SpawnCause): Pair<SpawningContext, SpawnDetail>? {
        println("[GrottoSpawner] run() starting for cause: $cause")
        val area = getArea(cause)
        println("[GrottoSpawner] getArea() result: $area")
        val constrainedArea = if (area != null) constrainArea(area) else null
        println("[GrottoSpawner] constrainArea() result: $constrainedArea")

        if (constrainedArea != null) {
            val slice = prospector.prospect(this, constrainedArea)
            val contexts = resolver.resolve(this, contextCalculators, slice)
            return getSpawningSelector().select(this, contexts)
        }

        println("[GrottoSpawner] Area is null or invalid, skipping spawn")
        return null
    }

    // todo test tick
    override fun tick() {
        super.tick()
        ticksUntilNextSpawn -= tickTimerMultiplier
        if (ticksUntilNextSpawn > 0) return

        val server = server() ?: return
        val world = server.overworld() ?: return

        for (player in world.players()) {
            val searchOrigin = player.blockPosition()
            val poiManager = world.poiManager
            val grottoPOIs = poiManager.findAll(
                { it.`is`(CobblemonPoiTypes.GROTTO_BLOCK_KEY) },
                { true },
                searchOrigin,
                128, // Search radius
                PoiManager.Occupancy.ANY
            )

            for (grottoPos in grottoPOIs) {
                val blockEntity = world.getBlockEntity(grottoPos) as? GrottoBlockEntity ?: continue
                val structureCenter = blockEntity.structureCenter ?: continue

                val cause = GrottoSpawnCause(this, chooseBucket(), player, structureCenter, grottoPos)
                val result = run(cause)

                println("[GrottoSpawner] Grotto at $grottoPos spawn result: $result")

                if (result != null) {
                    val (context, detail) = result
                    val action = detail.doSpawn(context)
                    println("[GrottoSpawner] doSpawn: $detail -> $action")
                    val completion = action.complete()
                    println("[GrottoSpawner] spawn complete: $completion")
                }
            }
        }

        ticksUntilNextSpawn = ticksBetweenSpawns
    }




    /*override fun tick() {
        super.tick()
        ticksUntilNextSpawn -= tickTimerMultiplier
        if (ticksUntilNextSpawn <= 0) {
            val cause = SpawnCause(this, chooseBucket(), server()?.playerList?.players?.firstOrNull())
            val result = run(cause)

            println("[GrottoSpawner] spawn result: $result")
            ticksUntilNextSpawn = ticksBetweenSpawns
            if (result != null) {
                val (context, detail) = result
                val action = detail.doSpawn(context)
                println("[GrottoSpawner] doSpawn: $detail -> $action")
                val completion = action.complete()
                println("[GrottoSpawner] spawn complete: $completion")
            } else {
                println("[GrottoSpawner] No spawn selected.")
            }
        }
    }
*/
    override fun getSpawningSelector(): SpawningSelector {
        return object : SpawningSelector {
            override fun select(spawner: Spawner, contexts: List<SpawningContext>): Pair<SpawningContext, SpawnDetail>? {
                val pool = when {
                    spawner is GrottoSpawner && contexts.firstOrNull()?.cause is GrottoSpawnCause ->
                        spawner.getSpawnPool(contexts.first().cause)
                    else -> spawner.getSpawnPool()
                }

                for (ctx in contexts) {
                    val details = pool.details.filterIsInstance<PokemonSpawnDetail>()
                    if (details.isEmpty()) continue

                    val chosen = weightedRandom(details) { it.weight.takeIf { w -> w > 0 } ?: 1.0f }
                    println("[GrottoSpawner] Selected ${chosen.pokemon.species} for spawn")
                    return ctx to chosen
                }

                println("[GrottoSpawner] No spawnable Pokémon found.")
                return null
            }

            override fun getTotalWeights(spawner: Spawner, contexts: List<SpawningContext>): Map<SpawnDetail, Float> {
                return spawner.getSpawnPool().details.associateWith { it.weight.takeIf { w -> w > 0 } ?: 1.0f }
            }

            private fun <T> weightedRandom(entries: List<T>, weightFn: (T) -> Float): T {
                val totalWeight = entries.sumOf { weightFn(it).toDouble() }
                var roll = Math.random() * totalWeight
                for (entry in entries) {
                    roll -= weightFn(entry)
                    if (roll <= 0) return entry
                }
                return entries.last()
            }
        }
    }


    override fun getSpawnPool(): SpawnPool {
        return spawns
    }

    // todo new getSpawnPool
    fun getSpawnPool(cause: SpawnCause): SpawnPool {
        val server = server() ?: return spawns
        val world = server.overworld() ?: return spawns
        val now = System.currentTimeMillis()

        if (cause !is GrottoSpawnCause) return spawns

        val center = cause.grottoPos
        val targetEntity = world.getChunk(center)?.getBlockEntity(center) as? GrottoBlockEntity ?: return spawns

        val structure = targetEntity.detectedStructure ?: return spawns
        val spawnGroup = targetEntity.currentSpawnGroup
        if (spawnGroup.isEmpty()) return spawns

        val cached = cache[center]
        if (cached != null && now - cached.second <= CACHE_DURATION_MS) {
            println("[GrottoSpawner] Cache hit at $center — using group: $spawnGroup")
            return cached.first
        }

        val habitat = Habitats.getHabitat(structure)
        if (habitat == null) {
            println("[GrottoSpawner] No habitat found for structure: $structure")
            return spawns
        }

        val selectedGroup = habitat.spawnPool.find { group ->
            group.spawns.toSet() == spawnGroup.toSet()
        } ?: return spawns.also {
            println("[GrottoSpawner] Could not match spawn group at $center to any group in habitat: $structure")
            println("  ↳ Current spawnGroup: $spawnGroup")
            println("  ↳ Habitat groups:")
            habitat.spawnPool.forEachIndexed { i, g ->
                println("    [$i]: ${g.spawns}")
            }
        }

        val poolCacheName = "grotto_pool_${center.x}_${center.y}_${center.z}"
        val pool = SpawnPool(poolCacheName)
        for (entry in selectedGroup.spawns) {
            val detail = PokemonSpawnDetail().apply {
                pokemon.species = entry.species.toString()
                weight = entry.weight.toFloat()
            }
            pool.details.add(detail)
        }

        println("[GrottoSpawner] Constructing pool for Grotto at ${cause.structureCenter}")
        println("  ↳ Detected structure: $structure")
        println("  ↳ Spawn group: $spawnGroup")
        println("  ↳ Selected group: $selectedGroup")
        println("  ↳ Pool size: ${pool.details.size}")

        cache[center] = pool to now
        return pool
    }





    /*override fun getSpawnPool(): SpawnPool {
        val server = server() ?: return spawns
        val world = server.overworld() ?: return spawns
        val now = System.currentTimeMillis()

        val serverLevel = server.overworld() // Replace with your target level
        for (chunk in serverLevel.chunkSource.chunkMap.visibleChunkMap.values) {
            val levelChunk = chunk.getChunkToSend() ?: continue
            for ((pos, blockEntity) in levelChunk.blockEntities) {
                if (blockEntity is GrottoBlockEntity) {
                    // Process your GrottoBlockEntity here
                }
            }
        }


        println("[GrottoSpawner] Tick — current moon phase: ${world.moonPhase}")

        for (player in world.players()) {
            val pos = player.blockPosition()
            val radius = 48

            val checkRegion = BlockPos.betweenClosed(
                    pos.offset(-radius, -10, -radius),
                    pos.offset(radius, 10, radius)
            )

            for (checkPos in checkRegion) {
                val blockEntity = world.getBlockEntity(checkPos) as? GrottoBlockEntity ?: continue
                val structure = blockEntity.detectedStructure ?: continue
                val spawnGroup = blockEntity.currentSpawnGroup
                if (spawnGroup.isEmpty()) continue

                val cached = cache[checkPos]
                if (cached != null && now - cached.second <= CACHE_DURATION_MS) {
                    println("[GrottoSpawner] Cache hit at $checkPos — using group: $spawnGroup")
                    return cached.first
                }

                val habitat = Habitats.getHabitat(structure)
                if (habitat == null) {
                    println("[GrottoSpawner] No habitat found for structure: $structure")
                    continue
                }

                // Match currentSpawnGroup to a full weighted group (species + weight)
                val selectedGroup = habitat.spawnPool.find { group ->
                    group.spawns.toSet() == spawnGroup.toSet()
                }

                if (selectedGroup == null) {
                    println("[GrottoSpawner] Could not match spawn group to any known group in habitat: $structure")
                    println("  ↳ Current group: $spawnGroup")
                    println("  ↳ Available groups:")
                    habitat.spawnPool.forEachIndexed { i, g ->
                        println("    [$i]: ${g.spawns}")
                    }
                    continue
                }

                val pool = SpawnPool("grotto_dynamic_pool")

                for (entry in selectedGroup.spawns) {
                    val detail = PokemonSpawnDetail()
                    detail.pokemon.species = entry.species.toString()
                    detail.weight = entry.weight.toFloat()
                    pool.details.add(detail)
                }

                println("[GrottoSpawner] Generated new pool at $checkPos:")
                println("  ↳ Structure: $structure")
                println("  ↳ Group: ${selectedGroup.spawns.map { "${it.species} (weight=${it.weight})" }}")

                cache[checkPos] = pool to now
                return pool
            }
        }

        println("[GrottoSpawner] No valid Grotto nearby — using fallback spawn pool (${spawns.details.size} entries)")
        return spawns
    }*/
}
