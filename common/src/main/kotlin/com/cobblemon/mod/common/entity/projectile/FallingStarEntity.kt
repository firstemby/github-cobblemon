/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.entity.projectile

import com.bedrockk.molang.runtime.struct.QueryStruct
import com.cobblemon.mod.common.api.net.serializers.PoseTypeDataSerializer
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.scheduling.Schedulable
import com.cobblemon.mod.common.api.scheduling.SchedulingTracker
import com.cobblemon.mod.common.client.entity.FallingStarClientDelegate
import com.cobblemon.mod.common.entity.PosableEntity
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.net.messages.client.spawn.SpawnFallingStarPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.Packet
import net.minecraft.resources.ResourceKey
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.level.ServerEntity
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.projectile.ThrowableProjectile
import net.minecraft.world.level.Level
import net.minecraft.world.level.storage.loot.LootParams
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets
import net.minecraft.world.level.storage.loot.parameters.LootContextParams
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.Vec3

class FallingStarEntity(
    entityType: EntityType<out FallingStarEntity>,
    level: Level
) : ThrowableProjectile(entityType, level), PosableEntity, Schedulable {

    override val schedulingTracker = SchedulingTracker()
    override val delegate = if (level.isClientSide) {
        FallingStarClientDelegate()
    } else {
        FallingStarServerDelegate()
    }
    override val struct: QueryStruct = QueryStruct(hashMapOf())

    var horizontalDirection = 0F // radians
    var resultType: FallingStarResultType = FallingStarResultType.MINIOR
    var poseType: PoseType
        get() = entityData.get(POSE_TYPE)
        set(value) { entityData.set(POSE_TYPE, value) }

    init {
        delegate.initialize(this)
        addPosableFunctions(struct)
    }

    override fun setDeltaMovement(deltaMovement: Vec3) {
        if (poseType == PoseType.NONE) {
            super.setDeltaMovement(
                Vec3(
                    kotlin.math.sin(horizontalDirection) * 0.8,
                    deltaMovement.y,
                    kotlin.math.cos(horizontalDirection) * 0.8
                )
            )
        } else {
            super.setDeltaMovement(Vec3.ZERO)
        }
    }

    override fun tick() {
        super.tick()
        delegate.tick(this)
        schedulingTracker.update(1/20F)

        if (level().isClientSide) {
            repeat(times = 3) {
                level().addParticle(
                    ParticleTypes.FLAME,
                    x + (random.nextDouble() - 0.5) * 0.5,
                    y + (random.nextDouble() - 0.5) * 0.5,
                    z + (random.nextDouble() - 0.5) * 0.5,
                    0.0, 0.0, 0.0
                )
            }
        }
    }

    override fun onHitBlock(result: BlockHitResult) {
        if (!level().isClientSide && poseType == PoseType.NONE) {
            poseType = PoseType.STAND
            val vec3 = result.getLocation().subtract(this.x, this.y, this.z)
            val vec32 = vec3.normalize().scale(0.05)
            this.setPosRaw(this.x - vec32.x, this.y - vec32.y, this.z - vec32.z)
            // based on our result type we should probably choose the item based on a loot table (not sure if we want other mons besides minior though..)
            when (resultType) {
                FallingStarResultType.ITEM -> {
                    val world = level() as ServerLevel
                    val lootTable = world.server.reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, cobblemonResource("gameplay/falling_star")))
                    val lootParams = LootParams.Builder(world)
                        .withParameter(LootContextParams.ORIGIN, result.location)
                        .create(LootContextParamSets.COMMAND)
                    lootTable.getRandomItems(lootParams).forEach { stack ->
                        world.addFreshEntity(ItemEntity(world, result.location.x, result.location.y, result.location.z, stack).also { it.setDefaultPickUpDelay() })
                    }
                }
                else -> level().addFreshEntity(PokemonProperties.parse("minior").createEntity(level()).also { it.moveTo(result.location.x, result.location.y, result.location.z) })
            }
            level().playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.NEUTRAL, 1F, 1F)
            discard()
        }
    }

    override fun onHitEntity(result: EntityHitResult) {
        if (!level().isClientSide && poseType == PoseType.NONE) {
            poseType = PoseType.STAND
            // TODO deal damage - you got killed by a star bro
            level().playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.NEUTRAL, 1F, 1F)
        }
    }

    override fun defineSynchedData(builder: SynchedEntityData.Builder) {
        builder.define(POSE_TYPE, PoseType.NONE)
    }

    override fun readAdditionalSaveData(compound: CompoundTag) {
        // TODO("Not yet implemented")
    }

    override fun addAdditionalSaveData(compound: CompoundTag) {
        // TODO("Not yet implemented")
    }

    override fun getCurrentPoseType(): PoseType = PoseType.NONE

    override fun getAddEntityPacket(entityTrackerEntry: ServerEntity) = ClientboundCustomPayloadPacket(
        SpawnFallingStarPacket(
            poseType = poseType,
            horizontalDirection = horizontalDirection,
            vanillaSpawnPacket = super.getAddEntityPacket(entityTrackerEntry) as ClientboundAddEntityPacket
        )
    ) as Packet<ClientGamePacketListener>

    companion object {
        val POSE_TYPE = SynchedEntityData.defineId(FallingStarEntity::class.java, PoseTypeDataSerializer)
    }
}