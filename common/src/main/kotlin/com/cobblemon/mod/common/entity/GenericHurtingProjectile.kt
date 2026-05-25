/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//
package net.minecraft.world.entity.projectile

import com.bedrockk.molang.Expression
import com.bedrockk.molang.runtime.value.MoValue
import com.cobblemon.mod.common.CobblemonEntities.GENERIC_HURTING_PROJECTILE_ENTITY
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMostSpecificMoLangValue
import com.cobblemon.mod.common.util.asExpression
import com.cobblemon.mod.common.util.mainThreadRuntime
import com.cobblemon.mod.common.util.withQueryValue
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.Level
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3


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
    constructor(level: Level, entityType: EntityType<out GenericHurtingProjectile?> = GENERIC_HURTING_PROJECTILE_ENTITY) : super(entityType, level)

    constructor(
        level: Level,
        owner: LivingEntity,
        movement: Vec3,
        hasGravity: Boolean,
        damage: Float,
        onHitEffect: Expression = "".asExpression()) : super(
        GENERIC_HURTING_PROJECTILE_ENTITY, owner, movement, level) {

        this.damage = damage
        this.onHitEffect = onHitEffect
        this.hasGravity = hasGravity
    }



    constructor(
        entityType: EntityType<out GenericHurtingProjectile?>,
        x: Double,
        y: Double,
        z: Double,
        movement: Vec3,
        level: Level
    ) : super(entityType, x, y, z, movement, level)

    constructor(entityType: EntityType<out GenericHurtingProjectile?>, owner: LivingEntity, movement: Vec3, level: Level) : super(
        entityType,
        owner,
        movement,
        level
    )



    override fun onHit(hitResult: HitResult) {
        super.onHit(hitResult)
        if (hitResult.type == HitResult.Type.ENTITY ) {
            val targetedEntity = (hitResult as EntityHitResult).entity
            if (!this.level().isClientSide()) {
                if (hitResult.type == HitResult.Type.ENTITY) {
                    // TODO: calculate damage base on pokemon stats
                    targetedEntity.hurt(damageSources().mobProjectile(this, owner as LivingEntity?), damage)
                    val runtime = mainThreadRuntime.withQueryValue("entity", owner?.asMostSpecificMoLangValue() ?: MoValue.of(null))
                        .withQueryValue("targetEntity", targetedEntity.asMostSpecificMoLangValue())
                    runtime.execute(onHitEffect)
                }
            }
        }
        this.discard()

    }



    override fun isNoGravity(): Boolean {
        return hasGravity
    }


    override fun hurt(source: DamageSource, amount: Float): Boolean {
        return false
    }


}