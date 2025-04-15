package com.cobblemon.mod.common.api.spawning.spawner

import com.cobblemon.mod.common.api.spawning.SpawnCause
import com.cobblemon.mod.common.api.spawning.SpawnerManager
import com.cobblemon.mod.common.api.spawning.context.SpawningContext
import com.cobblemon.mod.common.api.spawning.detail.SpawnPool
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnDetail
import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail
import com.cobblemon.mod.common.api.spawning.selection.SpawningSelector
import com.cobblemon.mod.common.block.entity.GrottoBlockEntity
import com.cobblemon.mod.common.util.server
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.entity.BlockEntity
import java.util.concurrent.ConcurrentHashMap

class GrottoSpawner(
        name: String,
        fallbackSpawns: SpawnPool,
        manager: SpawnerManager
) : AreaSpawner(name, fallbackSpawns, manager) {

    companion object {
        private val cache = ConcurrentHashMap<BlockPos, Pair<SpawnPool, Long>>()
        private const val CACHE_DURATION_MS = 10_000 // maybe we can cache the spawns for some time so it isn't calculated each tick
    }

    override var ticksBetweenSpawns: Float = 120F

    override fun getArea(cause: SpawnCause): SpawningArea? {
        val entity = cause.entity
                ?: server()?.playerList?.players?.firstOrNull()
                ?: return null

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
    }



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


    override fun tick() {
        super.tick() // optional, only if you override base logic

        ticksUntilNextSpawn -= tickTimerMultiplier
        if (ticksUntilNextSpawn <= 0) {
            val cause = SpawnCause(spawner = this, bucket = chooseBucket(), entity = server()?.playerList?.players?.firstOrNull())
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

    override fun getSpawningSelector(): SpawningSelector {
        return object : SpawningSelector {
            override fun select(spawner: Spawner, contexts: List<SpawningContext>): Pair<SpawningContext, SpawnDetail>? {
                val pool = spawner.getSpawnPool()

                for (ctx in contexts) {
                    val available = pool.details.filterIsInstance<PokemonSpawnDetail>()
                    println("[GrottoSpawner] Selecting randomly from ${available.size} spawn(s) in ${ctx.position}")
                    if (available.isNotEmpty()) {
                        val detail = available.random()
                        return ctx to detail
                    }
                }

                println("[GrottoSpawner] No spawnable Pokémon found.")
                return null
            }

            override fun getTotalWeights(spawner: Spawner, contexts: List<SpawningContext>): Map<SpawnDetail, Float> {
                val pool = spawner.getSpawnPool()
                val result = mutableMapOf<SpawnDetail, Float>()

                for (ctx in contexts) {
                    pool.details.forEach { detail ->
                        if (detail is PokemonSpawnDetail) {
                            result[detail] = 1.0f // Uniform weight for testing things out for now
                        }
                    }
                }

                return result
            }
        }
    }



    override fun getSpawnPool(): SpawnPool {
        val server = server() ?: return spawns
        val world = server.overworld() ?: return spawns
        val now = System.currentTimeMillis()

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
                val group = blockEntity.currentSpawnGroup
                val structure = blockEntity.detectedStructure

                if (group.isNotEmpty()) {
                    val cached = cache[checkPos]
                    if (cached != null && now - cached.second <= CACHE_DURATION_MS) {
                        println("[GrottoSpawner] Cache hit at $checkPos — using group: ${group.map { it.path }}")
                        return cached.first
                    }

                    val pool = SpawnPool("grotto_dynamic_pool")

                    group.forEach { monId ->
                        val detail = PokemonSpawnDetail()
                        detail.pokemon.species = monId.toString()
                        pool.details.add(detail)
                    }

                    cache[checkPos] = pool to now

                    println("[GrottoSpawner] Generated new pool at $checkPos:")
                    println("  ↳ Structure: ${structure ?: "unknown"}")
                    println("  ↳ Group: ${group.map { it.path }}")
                    return pool
                } else {
                    println("[GrottoSpawner] Grotto at $checkPos has no spawn group (moon not full or not set?)")
                }
            }
        }

        println("[GrottoSpawner] No valid Grotto nearby — using fallback spawn pool (${spawns.details.size} entries)")
        return spawns
    }
}
