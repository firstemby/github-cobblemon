package com.example.mod.entity

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonEntities.REALTIME_MOVE_PROJECTILE_AOE
import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.ai.CobblemonAttackTargetData
import com.cobblemon.mod.common.pokemon.ai.moves.AreaOfEffectMoveInstance
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.TraceableEntity
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import kotlin.collections.forEach

class RealtimeMoveAreaEffect(
    type: EntityType<out RealtimeMoveAreaEffect>,
    level: Level,
) : Entity(type, level), TraceableEntity {

    var areaOfEffectMoveInstance: AreaOfEffectMoveInstance? = null // TODO make non-nullable
    private var delayTicks = 0
    private var durationTicks = 0
    var radius = 0f
    override fun defineSynchedData(builder: SynchedEntityData.Builder) {
        builder.define(RADIUS, 0f)
    }

    override fun onSyncedDataUpdated(data: EntityDataAccessor<*>) {
        if (RADIUS == data) {
            val i = entityData.get(RADIUS) as Float
            this.radius = if (i > 0) i else 0f
        }

        super.onSyncedDataUpdated(data)
    }

    constructor(
        level: Level,
        entityType: EntityType<out RealtimeMoveAreaEffect?> = REALTIME_MOVE_PROJECTILE_AOE
    ) : this(entityType, level)

    constructor(
        level: Level,
        realTimeMoveInstance: AreaOfEffectMoveInstance
    ) : this(
        level, REALTIME_MOVE_PROJECTILE_AOE
    ) {
        this.areaOfEffectMoveInstance = realTimeMoveInstance
        entityData.set(RADIUS, areaOfEffectMoveInstance?.areaOfEffectMove?.radius ?: 0f)

    }

    override fun remove(reason: Entity.RemovalReason) {
        println("Entity removed: $this, reason=$reason")
        super.remove(reason)
    }

    override fun tick() {
        super.tick()
        if(level().isClientSide) return
        if (areaOfEffectMoveInstance == null) {
            if (this.tickCount > 0) {
                discard()
            }
            return
        }
        val moveData = areaOfEffectMoveInstance!!.areaOfEffectMove

        if (delayTicks < moveData.delayTicks) {
            delayTicks++
            return
        }

        if (durationTicks < moveData.durationTicks) {
            if (moveData.durationTicks % moveData.damageTickRate == 0) {
                val entities = getEntities()
                entities.forEach { it ->
                    areaOfEffectMoveInstance!!.dealDamage(it)
                }
            }
            durationTicks++
            return
        }
        Cobblemon.LOGGER.error("Discarding: " + uuid)
        discard()
    }

    override fun readAdditionalSaveData(compound: CompoundTag) {
        //TODO("Not yet implemented")
    }

    override fun addAdditionalSaveData(compound: CompoundTag) {
        //TODO("Not yet implemented")
    }

    fun getEntities(): List<Entity> {
        val moveData = areaOfEffectMoveInstance!!.areaOfEffectMove

        val searchBox = AABB(
            x - moveData.radius,
            y,
            z - moveData.radius,
            x + moveData.radius,
            y + moveData.height,
            z + moveData.radius

        )

        val entities = level().getEntities(this, searchBox)
            .filter { it is LivingEntity
                    && it.uuid != areaOfEffectMoveInstance!!.pokemonEntity.uuid
                    && it.uuid != areaOfEffectMoveInstance!!.pokemonEntity.owner?.uuid
                    && intersectsCylinder(it)
            }

        return entities
    }

    fun intersectsCylinder(entity: Entity): Boolean {
        val moveData = areaOfEffectMoveInstance!!.areaOfEffectMove

        val box = entity.boundingBox


        // Y overlap
        if (box.maxY < (y)
            || box.minY > (y + moveData.height) ) {
            return false
        }

        // Closest point in XZ plane

        val closestX = x.coerceIn(box.minX, box.maxX)
        val closestZ = z.coerceIn(box.minZ, box.maxZ)

        val dx = x - closestX
        val dz = z - closestZ

        return dx * dx + dz * dz <= moveData.radius * moveData.radius
    }

//    fun end() {
//        // TODO: add this as an "on hit" post event on the project
//        var attackTargetData = areaOfEffectMoveInstance!!.pokemonEntity.biggerBrain.getMemory(CobblemonMemories.ATTACK_TARGET_DATA)
//        if (attackTargetData != null) {
//            attackTargetData.hitsLanded += 1
//
//        } else {
//            attackTargetData = CobblemonAttackTargetData()
//            attackTargetData.hitsLanded = 1
//        }
//        areaOfEffectMoveInstance!!.pokemonEntity.biggerBrain.setMemory(CobblemonMemories.ATTACK_TARGET_DATA, attackTargetData)
//    }

    override fun getOwner(): Entity? {
        return areaOfEffectMoveInstance?.pokemonEntity
    }

    companion object {
        val RADIUS =
            SynchedEntityData.defineId(RealtimeMoveAreaEffect::class.java, EntityDataSerializers.FLOAT)
    }
}