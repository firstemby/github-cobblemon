/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.entity.scan

import com.cobblemon.mod.common.CobblemonEntities
import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.api.pokedex.scan.BlockScanDefinition
import com.cobblemon.mod.common.api.pokedex.scan.ScannedDataCategory
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import net.minecraft.nbt.Tag
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityDimensions
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.Pose
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks

/**
 * An invisible, builder-placed marker that makes a block position scannable by the Pokedex scanner.
 * It is anchored to a block; if that block is broken or replaced by a different block, the node
 * removes itself. Its per-instance config (category, scan id, up to four frame fields) is configured
 * in-world via the ScanNodeStick and synced to clients so the scanner can read it.
 */
class ScanNodeEntity(type: EntityType<out ScanNodeEntity>, level: Level) : Entity(type, level) {

    constructor(level: Level) : this(CobblemonEntities.SCAN_NODE, level)

    companion object {
        val CATEGORY: EntityDataAccessor<String> = SynchedEntityData.defineId(ScanNodeEntity::class.java, EntityDataSerializers.STRING)
        val SCAN_ID: EntityDataAccessor<String> = SynchedEntityData.defineId(ScanNodeEntity::class.java, EntityDataSerializers.STRING)
        val FRAME_1: EntityDataAccessor<String> = SynchedEntityData.defineId(ScanNodeEntity::class.java, EntityDataSerializers.STRING)
        val FRAME_2: EntityDataAccessor<String> = SynchedEntityData.defineId(ScanNodeEntity::class.java, EntityDataSerializers.STRING)
        val FRAME_3: EntityDataAccessor<String> = SynchedEntityData.defineId(ScanNodeEntity::class.java, EntityDataSerializers.STRING)
        val FRAME_4: EntityDataAccessor<String> = SynchedEntityData.defineId(ScanNodeEntity::class.java, EntityDataSerializers.STRING)
        private val FRAME_ACCESSORS = listOf(FRAME_1, FRAME_2, FRAME_3, FRAME_4)

        const val SIZE = 0.3F
        val DIMENSIONS: EntityDimensions = EntityDimensions.fixed(SIZE, SIZE)
        const val FRAME_COUNT = 4
        private const val ANCHOR_CHECK_INTERVAL = 10

        private const val NBT_CATEGORY = "ScanCategory"
        private const val NBT_SCAN_ID = "ScanId"
        private const val NBT_FRAMES = "ScanFrames"
        private const val NBT_ANCHOR_X = "AnchorX"
        private const val NBT_ANCHOR_Y = "AnchorY"
        private const val NBT_ANCHOR_Z = "AnchorZ"
        private const val NBT_ANCHOR_BLOCK = "AnchorBlock"
    }

    /** The block this node is bound to; broken/replaced -> node dies. Server-side only. */
    var anchorPos: BlockPos = BlockPos.ZERO
    var anchorBlock: ResourceLocation = BuiltInRegistries.BLOCK.getKey(Blocks.AIR)

    var category: ScannedDataCategory
        get() = ScannedDataCategory.fromId(entityData.get(CATEGORY)) ?: ScannedDataCategory.MISC
        set(value) = entityData.set(CATEGORY, value.id)

    var scanId: String
        get() = entityData.get(SCAN_ID)
        set(value) = entityData.set(SCAN_ID, value)

    val frameFields: List<String>
        get() = FRAME_ACCESSORS.map { entityData.get(it) }

    fun setFrameField(index: Int, value: String) {
        FRAME_ACCESSORS.getOrNull(index)?.let { entityData.set(it, value) }
    }

    /** The key recorded into the scanned-data store when this node is scanned. */
    fun scanKey(): ResourceLocation {
        val raw = scanId.trim()
        if (raw.isEmpty()) return cobblemonResource("unknown")
        return if (raw.contains(':')) (ResourceLocation.tryParse(raw) ?: cobblemonResource("unknown")) else cobblemonResource(raw)
    }

    /** Frames built from the configured fields, composed into the agreed lang key pattern. */
    fun resolvedFrames(): List<BlockScanDefinition.Frame> {
        val cat = category.id
        val id = scanId.trim().ifEmpty { "unknown" }
        return frameFields.mapIndexedNotNull { i, field ->
            if (field.isBlank()) null
            else BlockScanDefinition.Frame(
                slot = i + 1,
                text = BlockScanDefinition.ScanText.Translate("ui.pokedex.scanner.$cat.$id.${field.trim()}")
            )
        }
    }

    override fun defineSynchedData(builder: SynchedEntityData.Builder) {
        builder.define(CATEGORY, ScannedDataCategory.MISC.id)
        builder.define(SCAN_ID, "")
        builder.define(FRAME_1, "")
        builder.define(FRAME_2, "")
        builder.define(FRAME_3, "")
        builder.define(FRAME_4, "")
    }

    override fun tick() {
        super.tick()
        if (!level().isClientSide) {
            if (!isNoGravity) isNoGravity = true
            if (tickCount % ANCHOR_CHECK_INTERVAL == 0 && !anchorBlockStillPresent()) {
                discard()
            }
        }
    }

    private fun anchorBlockStillPresent(): Boolean {
        val current = BuiltInRegistries.BLOCK.getKey(level().getBlockState(anchorPos).block)
        return current == anchorBlock
    }

    override fun hurt(source: DamageSource, amount: Float): Boolean {
        // Removable only by a sneaking player holding the ScanNodeStick.
        val attacker = source.entity
        if (!level().isClientSide && attacker is Player && attacker.isShiftKeyDown &&
            (attacker.mainHandItem.`is`(CobblemonItems.SCAN_NODE_STICK) || attacker.offhandItem.`is`(CobblemonItems.SCAN_NODE_STICK))
        ) {
            discard()
            return true
        }
        return false
    }

    override fun addAdditionalSaveData(nbt: CompoundTag) {
        nbt.putString(NBT_CATEGORY, category.id)
        nbt.putString(NBT_SCAN_ID, scanId)
        nbt.put(NBT_FRAMES, ListTag().also { list -> frameFields.forEach { list.add(StringTag.valueOf(it)) } })
        nbt.putInt(NBT_ANCHOR_X, anchorPos.x)
        nbt.putInt(NBT_ANCHOR_Y, anchorPos.y)
        nbt.putInt(NBT_ANCHOR_Z, anchorPos.z)
        nbt.putString(NBT_ANCHOR_BLOCK, anchorBlock.toString())
    }

    override fun readAdditionalSaveData(nbt: CompoundTag) {
        category = ScannedDataCategory.fromId(nbt.getString(NBT_CATEGORY)) ?: ScannedDataCategory.MISC
        scanId = nbt.getString(NBT_SCAN_ID)
        val frames = nbt.getList(NBT_FRAMES, Tag.TAG_STRING.toInt())
        for (i in 0 until FRAME_COUNT) setFrameField(i, if (i < frames.size) frames.getString(i) else "")
        anchorPos = BlockPos(nbt.getInt(NBT_ANCHOR_X), nbt.getInt(NBT_ANCHOR_Y), nbt.getInt(NBT_ANCHOR_Z))
        anchorBlock = ResourceLocation.tryParse(nbt.getString(NBT_ANCHOR_BLOCK)) ?: BuiltInRegistries.BLOCK.getKey(Blocks.AIR)
    }

    override fun getDimensions(pose: Pose): EntityDimensions = DIMENSIONS
    override fun isPickable(): Boolean = true
    override fun isPushable(): Boolean = false
    override fun isIgnoringBlockTriggers(): Boolean = true
}
