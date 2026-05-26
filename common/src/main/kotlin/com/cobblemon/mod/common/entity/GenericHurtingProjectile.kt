/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */


package net.minecraft.world.entity.projectile

import com.bedrockk.molang.Expression
import com.bedrockk.molang.runtime.value.MoValue
import com.cobblemon.mod.common.CobblemonEntities.GENERIC_HURTING_PROJECTILE_ENTITY
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMostSpecificMoLangValue
import com.cobblemon.mod.common.util.asExpression
import com.cobblemon.mod.common.util.mainThreadRuntime
import com.cobblemon.mod.common.util.withQueryValue
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.Level
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.math.pow


/*
 *
 *  Placeholder projectile for real time pokemon attacks/moves
 *  Has configurable damage and molang on hit effect
 *
 *
 */
class GenericHurtingProjectile : AbstractHurtingProjectile {

    private var damage = 0f
    private var onHitEffect = "".asExpression()
    private var hasGravity = true
    private var hitEntities = mutableSetOf<UUID>()
    private var isPiercing = false
    private var piercingMultiplier = 1.0f
    constructor(
        level: Level,
        entityType: EntityType<out GenericHurtingProjectile?> = GENERIC_HURTING_PROJECTILE_ENTITY
    ) : super(entityType, level)

    constructor(
        level: Level,
        owner: LivingEntity,
        movement: Vec3,
        hasGravity: Boolean,
        damage: Float,
        isPiercing: Boolean,
        piercingMultiplier: Float,
        onHitEffect: Expression = "".asExpression()
    ) : super(
        GENERIC_HURTING_PROJECTILE_ENTITY, owner, movement, level
    ) {

        this.damage = damage
        this.onHitEffect = onHitEffect
        this.hasGravity = hasGravity
        this.isPiercing = isPiercing
        this.piercingMultiplier = piercingMultiplier
    }


    constructor(
        entityType: EntityType<out GenericHurtingProjectile?>,
        x: Double,
        y: Double,
        z: Double,
        movement: Vec3,
        level: Level
    ) : super(entityType, x, y, z, movement, level)

    constructor(
        entityType: EntityType<out GenericHurtingProjectile?>,
        owner: LivingEntity,
        movement: Vec3,
        level: Level
    ) : super(
        entityType,
        owner,
        movement,
        level
    )


    override fun onHit(hitResult: HitResult) {
        super.onHit(hitResult)
        if (hitResult.type == HitResult.Type.ENTITY) {
            val targetEntity = (hitResult as EntityHitResult).entity
            if (targetEntity != null && !this.level().isClientSide()) {


                if (isPiercing) {
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
        val piercingMultiplier = if (isPiercing)  this.piercingMultiplier.toDouble().pow(hitEntities.count().toDouble()) else 1.0
        // TODO: calculate damage base on pokemon stats
        targetEntity.hurt(damageSources().mobProjectile(this, owner as LivingEntity?),
            (damage * piercingMultiplier).toFloat()
        )
        val runtime =
            mainThreadRuntime.withQueryValue("entity", owner?.asMostSpecificMoLangValue() ?: MoValue.of(null))
                .withQueryValue("targetEntity", targetEntity.asMostSpecificMoLangValue())
        runtime.execute(onHitEffect)
    }


    override fun isNoGravity(): Boolean {
        return hasGravity
    }


    override fun hurt(source: DamageSource, amount: Float): Boolean {
        return false
    }


}