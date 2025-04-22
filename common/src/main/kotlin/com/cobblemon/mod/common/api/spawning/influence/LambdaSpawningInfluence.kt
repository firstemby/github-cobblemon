/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.spawning.influence

import com.cobblemon.mod.common.api.spawning.SpawnBucket
import com.cobblemon.mod.common.api.spawning.detail.SpawnAction
import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail
import com.cobblemon.mod.common.api.spawning.position.SpawnablePosition
import com.cobblemon.mod.common.api.spawning.position.calculators.SpawnablePositionCalculator
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity

// I'm not really sure this is necessary. It would construe things oddly to use this and the gain was 1/2 of regular iteration.
// Not exactly a life changing optimization. I may change my mind.
///**
// * It's faster to stack lambdas than iterate over collections. Source: A hastily written unit test I ran 20 minutes ago.
// *
// * @author Hiroku
// * @since April 21st, 2025
// */
//class LambdaSpawningInfluence : SpawningInfluence {
//    var spawnableInfluence: ((SpawnDetail, SpawnablePosition) -> Boolean)? = null
//    var weightInfluence: ((SpawnDetail, SpawnablePosition, Float) -> Float)? = null
//    var actionInfluence: ((SpawnAction<*>) -> Unit)? = null
//    var spawnInfluence: ((SpawnAction<*>, Entity) -> Unit)? = null
//    var bucketWeightInfluence: ((MutableMap<SpawnBucket, Float>) -> Unit)? = null
//    var allowedPositionInfluence: ((ServerLevel, BlockPos, SpawnablePositionCalculator<*, *>) -> Boolean)? = null
//    var injectSpawnsInfluence: ((SpawnBucket, SpawnablePosition) -> List<SpawnDetail>?)? = null
//
//    fun addBucketWeightInfluence(influence: (MutableMap<SpawnBucket, Float>) -> Unit): LambdaSpawningInfluence {
//        val oldInfluence = this.bucketWeightInfluence
//        if (oldInfluence == null) {
//            this.bucketWeightInfluence = influence
//        } else {
//            this.bucketWeightInfluence = { bucketWeights -> oldInfluence(bucketWeights); influence(bucketWeights) }
//        }
//        return this
//    }
//
//    fun addSpawnableInfluence(influence: (SpawnDetail, SpawnablePosition) -> Boolean): LambdaSpawningInfluence {
//        val oldInfluence = this.spawnableInfluence
//        if (oldInfluence == null) {
//            this.spawnableInfluence = influence
//        } else {
//            this.spawnableInfluence = { detail, position -> oldInfluence(detail, position) && influence(detail, position) }
//        }
//        return this
//    }
//
//    fun addWeightInfluence(influence: (SpawnDetail, SpawnablePosition, Float) -> Float): LambdaSpawningInfluence {
//        val oldInfluence = this.weightInfluence
//        if (oldInfluence == null) {
//            this.weightInfluence = influence
//        } else {
//            this.weightInfluence = { detail, position, weight -> influence(detail, position, oldInfluence(detail, position, weight)) }
//        }
//        return this
//    }
//
//    fun addActionInfluence(influence: (SpawnAction<*>) -> Unit): LambdaSpawningInfluence {
//        val oldInfluence = this.actionInfluence
//        if (oldInfluence == null) {
//            this.actionInfluence = influence
//        } else {
//            this.actionInfluence = { action -> oldInfluence(action); influence(action) }
//        }
//        return this
//    }
//
//    fun addSpawnInfluence(influence: (SpawnAction<*>, Entity) -> Unit): LambdaSpawningInfluence {
//        val oldInfluence = this.spawnInfluence
//        if (oldInfluence == null) {
//            this.spawnInfluence = influence
//        } else {
//            this.spawnInfluence = { action, entity -> oldInfluence(action, entity); influence(action, entity) }
//        }
//        return this
//    }
//
//    fun addAllowedPositionInfluence(influence: (ServerLevel, BlockPos, SpawnablePositionCalculator<*, *>) -> Boolean): LambdaSpawningInfluence {
//        val oldInfluence = this.allowedPositionInfluence
//        if (oldInfluence == null) {
//            this.allowedPositionInfluence = influence
//        } else {
//            this.allowedPositionInfluence = { world, pos, calculator -> influence(world, pos, calculator) && oldInfluence(world, pos, calculator) }
//        }
//        return this
//    }
//
//    fun addInjectSpawnsInfluence(influence: (SpawnBucket, SpawnablePosition) -> List<SpawnDetail>?): LambdaSpawningInfluence {
//        val oldInfluence = this.injectSpawnsInfluence
//        if (oldInfluence == null) {
//            this.injectSpawnsInfluence = influence
//        } else {
//            this.injectSpawnsInfluence = { bucket, position ->
//                val previouslyStackedSpawns = oldInfluence(bucket, position)
//                val newSpawns = influence(bucket, position)
//                if (previouslyStackedSpawns == null) {
//                    newSpawns
//                } else if (newSpawns == null) {
//                    previouslyStackedSpawns
//                } else {
//                    previouslyStackedSpawns + newSpawns
//                }
//            }
//        }
//        return this
//    }
//
//    override fun affectBucketWeights(bucketWeights: MutableMap<SpawnBucket, Float>) = bucketWeightInfluence?.invoke(bucketWeights) ?: Unit
//    override fun affectSpawnable(detail: SpawnDetail, position: SpawnablePosition): Boolean = spawnableInfluence?.invoke(detail, position) ?: true
//    override fun affectWeight(detail: SpawnDetail, position: SpawnablePosition, weight: Float): Float = weightInfluence?.invoke(detail, position, weight) ?: weight
//    override fun affectAction(action: SpawnAction<*>) = actionInfluence?.invoke(action) ?: Unit
//    override fun affectSpawn(action: SpawnAction<*>, entity: Entity) = spawnInfluence?.invoke(action, entity) ?: Unit
//    override fun isAllowedPosition(world: ServerLevel, pos: BlockPos, calculator: SpawnablePositionCalculator<*, *>): Boolean = allowedPositionInfluence?.invoke(world, pos, calculator) ?: true
//    override fun injectSpawns(bucket: SpawnBucket, position: SpawnablePosition): List<SpawnDetail>? = injectSpawnsInfluence?.invoke(bucket, position)
//
//    fun addInfluence(influence: SpawningInfluence): LambdaSpawningInfluence {
//        if (influence is LambdaSpawningInfluence) {
//            influence.spawnInfluence?.let(::addSpawnInfluence)
//            influence.actionInfluence?.let(::addActionInfluence)
//            influence.weightInfluence?.let(::addWeightInfluence)
//            influence.spawnableInfluence?.let(::addSpawnableInfluence)
//            influence.bucketWeightInfluence?.let(::addBucketWeightInfluence)
//            influence.allowedPositionInfluence?.let(::addAllowedPositionInfluence)
//            influence.injectSpawnsInfluence?.let(::addInjectSpawnsInfluence)
//        } else {
//            this.spawnableInfluence = { detail, position -> influence.affectSpawnable(detail, position) && this.spawnableInfluence?.invoke(detail, position) ?: true }
//            this.weightInfluence = { detail, position, weight -> influence.affectWeight(detail, position, weight) }
//            this.actionInfluence = { action -> influence.affectAction(action) }
//            this.spawnInfluence = { action, entity -> influence.affectSpawn(action, entity) }
//            this.bucketWeightInfluence = { bucketWeights -> influence.affectBucketWeights(bucketWeights) }
//            this.allowedPositionInfluence = { world, pos, calculator -> influence.isAllowedPosition(world, pos, calculator) && this.allowedPositionInfluence?.invoke(world, pos, calculator) ?: true }
//            this.injectSpawnsInfluence = { bucket, position -> influence.injectSpawns(bucket, position) }
//        }
//        return this
//    }
//
//    fun clone(): LambdaSpawningInfluence {
//        val clone = LambdaSpawningInfluence()
//        clone.spawnableInfluence = this.spawnableInfluence
//        clone.weightInfluence = this.weightInfluence
//        clone.actionInfluence = this.actionInfluence
//        clone.spawnInfluence = this.spawnInfluence
//        clone.bucketWeightInfluence = this.bucketWeightInfluence
//        clone.allowedPositionInfluence = this.allowedPositionInfluence
//        clone.injectSpawnsInfluence = this.injectSpawnsInfluence
//        return clone
//    }
//}