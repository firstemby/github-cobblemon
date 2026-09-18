/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.entity

import com.bedrockk.molang.runtime.value.MoValue
import com.cobblemon.mod.common.CobblemonEntities.REALTIME_MOVE_PROJECTILE_ENTITY
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMostSpecificMoLangValue
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.ai.moves.ProjectileMoveInstance
import com.cobblemon.mod.common.pokemon.ai.moves.RealTimeMoveInstance.Companion.getDamageMultiplier
import com.cobblemon.mod.common.util.isServerSide
import com.cobblemon.mod.common.util.mainThreadRuntime
import com.cobblemon.mod.common.util.withQueryValue
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.*
import net.minecraft.world.entity.projectile.ThrowableProjectile
import net.minecraft.world.level.Level
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.HitResult
import java.util.*
import kotlin.math.pow


/*
 *
 *  Placeholder projectile for real time pokemon attacks/moves
 *  Has configurable damage and molang on hit effect
 *
 *  TODO: save/restoring projectiles
 *   TODO: sync projectile behaviour with client
 */
class RealtimeMoveProjectile : ThrowableProjectile {

    private var hitEntities = mutableSetOf<UUID>()
    private var realTimeMoveInstance: ProjectileMoveInstance? = null // TODO make non-nullable
    var syncedGravity = 0.1
    var syncedHasGravity = true

    constructor(
        level: Level,
        entityType: EntityType<out RealtimeMoveProjectile?> = REALTIME_MOVE_PROJECTILE_ENTITY
    ) : super(entityType, level)

    constructor(
        level: Level,
        owner: LivingEntity,
        projectileMoveInstance: ProjectileMoveInstance
    ) : super(
        REALTIME_MOVE_PROJECTILE_ENTITY, owner, level
    ) {
        this.realTimeMoveInstance = projectileMoveInstance
        entityData.set(HAS_GRAVITY, projectileMoveInstance.projectileMove.hasGravity)
        entityData.set(GRAVITY, if (projectileMoveInstance.projectileMove.hasGravity) projectileMoveInstance.projectileMove.gravity.toFloat() else 0.0f)
    }

    override fun defineSynchedData(builder: SynchedEntityData.Builder) {
        builder.define(GRAVITY, 0f)
        builder.define(HAS_GRAVITY, true)

    }

    override fun onSyncedDataUpdated(data: EntityDataAccessor<*>) {
        if (GRAVITY == data) {
            val f = entityData.get(GRAVITY) as Float
            this.syncedGravity = f.toDouble()
        }
        if (HAS_GRAVITY == data) {
            val b = entityData.get(HAS_GRAVITY) as Boolean
            this.syncedHasGravity = b
        }

        super.onSyncedDataUpdated(data)
    }

    override fun tick() {

        super.tick()
        // handle tick events
        if (realTimeMoveInstance == null) return
        if (level().isServerSide()) {
            realTimeMoveInstance!!.projectileMove.tickEvents.forEach { event ->
                val offset = this.tickCount - event.startTick
                if (offset >= 0 && offset % event.intervalTicks == 0 && offset / event.intervalTicks < event.maxOccurrences) {
                    event.actions.forEach { action ->
                        action.execute(
                            owner = realTimeMoveInstance!!.pokemonEntity,
                            target = realTimeMoveInstance!!.targetEntity,
                            parentEntity = this
                        )
                    }
                }
            }
        }

    }

    override fun updateRotation() {
        // nah
    }


    override fun onHit(hitResult: HitResult) {
        super.onHit(hitResult)
        // TODO: avoid friendly fire events
        if (realTimeMoveInstance == null) return

        // Handle collision events
        realTimeMoveInstance!!.projectileMove.collisionEvents.forEach { it ->
            if (hitResult.type == it.collisionType) {
                it.actions.forEach {
                    it.execute(
                        owner = realTimeMoveInstance!!.pokemonEntity,
                        target = realTimeMoveInstance!!.targetEntity,
                        parentEntity = this
                    )
                }
            }
        }

        if (hitResult.type == HitResult.Type.ENTITY) {
            val targetEntity = (hitResult as EntityHitResult).entity
            if (targetEntity != null && !this.level().isClientSide()) {

                if (realTimeMoveInstance!!.projectileMove.piercing) {
                    if (!hitEntities.contains(targetEntity.uuid)) {
                        hitEntities.add(targetEntity.uuid)
                        damageEntity(targetEntity)
                    }
                } else {
                    damageEntity(targetEntity)
                    this.discard()
                }
            }
        } else {
            this.discard()
        }
    }

    fun damageEntity(targetEntity: Entity) {
        if (realTimeMoveInstance == null) return

        val projectileMove = realTimeMoveInstance!!.projectileMove
        val piercingMultiplier = if (projectileMove.piercing)
            projectileMove.piercingMultiplier.toDouble().pow(hitEntities.count().toDouble()) else 1.0
        realTimeMoveInstance!!.dealDamage(targetEntity, piercingMultiplier.toFloat())
        if (Math.random() < projectileMove.effectChance) {
            val runtime =
                mainThreadRuntime.withQueryValue("entity", owner?.asMostSpecificMoLangValue() ?: MoValue.of(null))
                    .withQueryValue("targetEntity", targetEntity.asMostSpecificMoLangValue())
            runtime.execute(projectileMove.effect)
        }

    }


    override fun isNoGravity(): Boolean {
        if (realTimeMoveInstance == null) return !this.syncedHasGravity
        return !realTimeMoveInstance!!.projectileMove.hasGravity
    }

    override fun getDefaultGravity(): Double {
        return realTimeMoveInstance?.projectileMove?.gravity ?: this.syncedGravity
    }


    override fun getDimensions(pose: Pose): EntityDimensions {
        val size = realTimeMoveInstance?.projectileMove?.size ?: 0.4f
        return EntityDimensions.fixed(size, size)
    }


    override fun hurt(source: DamageSource, amount: Float): Boolean {
        return false
    }

    companion object {
        val GRAVITY: EntityDataAccessor<Float?> =
            SynchedEntityData.defineId(RealtimeMoveProjectile::class.java, EntityDataSerializers.FLOAT)
        val HAS_GRAVITY: EntityDataAccessor<Boolean?> =
            SynchedEntityData.defineId(RealtimeMoveProjectile::class.java, EntityDataSerializers.BOOLEAN)
    }

}