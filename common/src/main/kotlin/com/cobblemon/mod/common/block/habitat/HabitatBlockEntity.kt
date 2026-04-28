/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block.habitat

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonBlockEntities
import com.cobblemon.mod.common.CobblemonNetwork.sendPacket
import com.cobblemon.mod.common.api.habitats.ActivatedHabitatPool
import com.cobblemon.mod.common.api.habitats.ActivatedHabitatSpawn
import com.cobblemon.mod.common.api.habitats.HabitatPhaseOrder
import com.cobblemon.mod.common.api.habitats.NaturalHabitatPool
import com.cobblemon.mod.common.api.habitats.dto.HabitatSettingsDTO
import com.cobblemon.mod.common.api.habitats.spawningstyle.ActivatedHabitatSpawning
import com.cobblemon.mod.common.api.habitats.spawningstyle.HabitatSpawningStyle
import com.cobblemon.mod.common.api.habitats.spawningstyle.NaturalHabitatSpawning
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.spawning.SpawnBucket
import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail
import com.cobblemon.mod.common.api.spawning.influence.SpawningInfluence
import com.cobblemon.mod.common.api.spawning.position.SpawnablePosition
import com.cobblemon.mod.common.api.spawning.spawner.Spawner
import com.cobblemon.mod.common.net.messages.client.habitat.OpenHabitatBlockEditorPacket
import com.cobblemon.mod.common.util.DataKeys
import com.cobblemon.mod.common.util.adapters.IntRangeAdapter
import com.cobblemon.mod.common.util.asIdentifierDefaultingNamespace
import kotlin.random.Random
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import net.minecraft.nbt.Tag
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult

class HabitatBlockEntity(pos: BlockPos, state: BlockState) :
    BlockEntity(CobblemonBlockEntities.HABITAT_BLOCK, pos, state),
    SpawningInfluence {
    var initialized = false

    var receivingSignal = false
    val spawnedEntityIDs = mutableSetOf<Int>()

    var spawningStyle: HabitatSpawningStyle = ActivatedHabitatSpawning(this)

    var numberOfPhases: Int = 1
    var phaseOrder = HabitatPhaseOrder.SIMPLE
    var currentPhase = 1

    var levelRange: IntRange = 1..Cobblemon.config.maxPokemonLevel
    var modifiers: PokemonProperties = PokemonProperties()

    var mimicId: ResourceLocation = BuiltInRegistries.BLOCK.getKey(Blocks.STONE)
    val mimickedState: BlockState
        get() = BuiltInRegistries.BLOCK.get(mimicId).defaultBlockState() ?: Blocks.STONE.defaultBlockState()
    var displaySpeciesIds: List<ResourceLocation> = emptyList()

    fun onUse(player: Player, hit: BlockHitResult): InteractionResult {
        if (player.level().isClientSide) {
            return InteractionResult.SUCCESS
        }

        player as ServerPlayer

        if (!player.isCreative) {
            return InteractionResult.PASS
        }

        player.sendPacket(OpenHabitatBlockEditorPacket(this))
        return InteractionResult.SUCCESS
    }

    /**
     * Accepts that some changes have been made to the block settings and therefore we should re-initialize a bunch of stuff.
     */
    fun refreshFromSettings() {
        val spawningStyle = spawningStyle
        var activatedStyle = false
        var cancelsRegularSpawns = false
        if (spawningStyle is ActivatedHabitatSpawning) {
            spawningStyle.generateSpawnDetails(level as ServerLevel, blockPos)
            activatedStyle = true
            cancelsRegularSpawns = spawningStyle.cancelledNaturalSpawningRange > 0
        } else if (spawningStyle is NaturalHabitatSpawning) {
            spawningStyle.generateSpawnDetails()
            cancelsRegularSpawns = spawningStyle.replaceSpawns
        }
        refreshDisplaySpeciesIds()
        level!!.setBlockAndUpdate(
            blockPos,
            blockState
                .setValue(HabitatBlock.ACTIVATED_STYLE, activatedStyle)
                .setValue(HabitatBlock.CANCELS_REGULAR_SPAWNS, cancelsRegularSpawns)
        )
    }

    fun calculatePhase(gameTime: Long): Int {
        if (numberOfPhases <= 1) {
            return 1
        }
        val basePhase = ((gameTime / 24000) % numberOfPhases).toInt() + 1
        return when (phaseOrder) {
            HabitatPhaseOrder.FULL_RANDOM -> {
                // We want this to be deterministic based on the day, but also want it to be different for each block. So we use the block position as a salt.
                Random(gameTime / 24000 + blockPos.asLong()).nextInt(numberOfPhases) + 1
            }
            HabitatPhaseOrder.FIXED_RANDOM -> calculatePhaseOrder()[basePhase - 1]
            HabitatPhaseOrder.SIMPLE -> basePhase
        }
    }

    /** Returns a list of all the possible phases in the order that is based on the block position of the habitat block. */
    fun calculatePhaseOrder(): List<Int> {
        return (1..numberOfPhases).shuffled(Random(blockPos.asLong()))
    }

    fun applySettings(habitatSettingsDTO: HabitatSettingsDTO) {
        val activatedSettings = habitatSettingsDTO.activatedSettings
        val activatedPool = habitatSettingsDTO.activatedSettings?.habitatPool?.toHabitatPool() as? ActivatedHabitatPool
        val isActivated = habitatSettingsDTO.isActivatedSpawning
        if (isActivated && activatedPool == null) {
            return // Pool was bad, check console logs
        }
        val naturalSettings = habitatSettingsDTO.naturalSettings
        val naturalPool = habitatSettingsDTO.naturalSettings?.habitatPool?.toHabitatPool() as? NaturalHabitatPool
        if (!isActivated && naturalPool == null) {
            return // Pool was bad, check console logs
        }
        this.numberOfPhases = habitatSettingsDTO.numberOfPhases
        this.phaseOrder = habitatSettingsDTO.phaseOrder
        this.levelRange = habitatSettingsDTO.levelRange
        this.modifiers = PokemonProperties.parse(habitatSettingsDTO.modifiers)
        this.mimicId = habitatSettingsDTO.mimicId
        if (isActivated && activatedSettings != null) {
            val activatedHabitatSpawning = ActivatedHabitatSpawning(this)
            activatedHabitatSpawning.trigger = activatedSettings.trigger
            activatedHabitatSpawning.chance = activatedSettings.chance
            activatedHabitatSpawning.cancelledNaturalSpawningRange = activatedSettings.cancelledNaturalSpawningRange
            activatedHabitatSpawning.spawnRange = activatedSettings.spawnRange
            activatedHabitatSpawning.maxSpawns = activatedSettings.maxSpawns
            activatedHabitatSpawning.maxSpawnsPerActivation = activatedSettings.maxSpawnsPerActivation
            activatedHabitatSpawning.pool = activatedPool!!
            this.spawningStyle = activatedHabitatSpawning
        } else if (naturalSettings != null && naturalPool != null) {
            val naturalHabitatSpawning = NaturalHabitatSpawning(this)
            naturalHabitatSpawning.replaceSpawns = naturalSettings.replaceSpawns
            naturalHabitatSpawning.rangeOfInfluence = naturalSettings.rangeOfInfluence
            naturalHabitatSpawning.pool = naturalPool
            this.spawningStyle = naturalHabitatSpawning
        }

        refreshFromSettings()
        setChanged()
        level?.sendBlockUpdated(worldPosition, blockState, blockState, Block.UPDATE_ALL)
    }

    override fun saveAdditional(tag: CompoundTag, registryLookup: HolderLookup.Provider) {
        super.saveAdditional(tag, registryLookup)
        tag.putString(DataKeys.HABITAT_MIMIC, mimicId.toString())
        tag.putInt(DataKeys.HABITAT_PHASE_COUNT, numberOfPhases)
        tag.putString(DataKeys.HABITAT_PHASE_ORDER, phaseOrder.toString())
        // Only save the level range when it's a non-trivial one
        if (levelRange.first != 1 || levelRange.last != Cobblemon.config.maxPokemonLevel) {
            tag.putString(DataKeys.HABITAT_LEVEL_RANGE, IntRangeAdapter.serialize(levelRange))
        }
        tag.putString(DataKeys.HABITAT_MODIFIERS, modifiers.asString())
        tag.putString(DataKeys.HABITAT_SPAWNING_STYLE, spawningStyle.type.toString())
        spawningStyle.writeToNBT(tag)
        val speciesListTag = ListTag()
        displaySpeciesIds.forEach { speciesListTag.add(StringTag.valueOf(it.toString())) }
        tag.put(DISPLAY_SPECIES_KEY, speciesListTag)
    }

    override fun loadAdditional(tag: CompoundTag, registryLookup: HolderLookup.Provider) {
        super.loadAdditional(tag, registryLookup)
        tag.getString(DataKeys.HABITAT_MIMIC).let {
            mimicId = ResourceLocation.tryParse(it) ?: BuiltInRegistries.BLOCK.getKey(Blocks.STONE)
        }
        displaySpeciesIds = if (tag.contains(DISPLAY_SPECIES_KEY, Tag.TAG_LIST.toInt())) {
            tag.getList(DISPLAY_SPECIES_KEY, Tag.TAG_STRING.toInt())
                .mapNotNull { speciesTag -> ResourceLocation.tryParse(speciesTag.asString) }
                .distinct()
        } else {
            emptyList()
        }

        if (level?.isClientSide == true) {
            return // Client doesn't need to try loading the rest
        }

        numberOfPhases = tag.getInt(DataKeys.HABITAT_PHASE_COUNT)
        phaseOrder = HabitatPhaseOrder.valueOf(tag.getString(DataKeys.HABITAT_PHASE_ORDER))
        levelRange = tag.getString(DataKeys.HABITAT_LEVEL_RANGE).takeIf { it.isNotBlank() }?.let(IntRangeAdapter::deserialize) ?: 1..Cobblemon.config.maxPokemonLevel
        modifiers = tag.getString(DataKeys.HABITAT_MODIFIERS).takeIf { it.isNotBlank() }?.let(PokemonProperties::parse) ?: PokemonProperties()
        val spawningStyleType = tag.getString(DataKeys.HABITAT_SPAWNING_STYLE).asIdentifierDefaultingNamespace()
        spawningStyle = if (spawningStyleType == ActivatedHabitatSpawn.TYPE) {
            ActivatedHabitatSpawning(this)
        } else {
            NaturalHabitatSpawning(this)
        }
        spawningStyle.readFromNBT(tag)
        refreshDisplaySpeciesIds()
    }

    override fun getUpdatePacket(): Packet<ClientGamePacketListener> {
        return ClientboundBlockEntityDataPacket.create(this)
    }

    override fun getUpdateTag(registryLookup: HolderLookup.Provider): CompoundTag {
        // Don't tell the client everything, otherwise hackermans may learn too much
        val tag = CompoundTag()
        tag.putString(DataKeys.HABITAT_MIMIC, mimicId.toString())
        val speciesListTag = ListTag()
        displaySpeciesIds.forEach { speciesListTag.add(StringTag.valueOf(it.toString())) }
        tag.put(DISPLAY_SPECIES_KEY, speciesListTag)
        return tag
    }

    private fun refreshDisplaySpeciesIds() {
        displaySpeciesIds = when (val style = spawningStyle) {
            is ActivatedHabitatSpawning -> style.pool.spawns.map { it.species.resourceIdentifier }.distinct()
            is NaturalHabitatSpawning -> style.pool.spawns.map { it.species.resourceIdentifier }.distinct()
        }
    }

    fun getInfluentialRange(spawner: Spawner): Int {
        val spawningStyle = spawningStyle
        return if (spawningStyle is ActivatedHabitatSpawning) {
            spawningStyle.cancelledNaturalSpawningRange
        } else if (spawningStyle is NaturalHabitatSpawning) {
            if (spawner == Cobblemon.bestSpawner.fishingSpawner && !spawningStyle.affectsFishing) {
                0 // We don't impact fishing
            } else if (spawner != Cobblemon.bestSpawner.fishingSpawner && !spawningStyle.affectsOverworld) {
                0 // We don't impact non-fishing spawns. I'd like it if we could tag spawners better than what I'm doing here.
            } else {
                spawningStyle.rangeOfInfluence
            }
        } else {
            0
        }
    }

    fun updateLevelAndPos(level: Level, pos: BlockPos) {
        val block = this.blockState.block
        if (block is HabitatBlock) {
            block.level = level
            block.pos = pos
        }
    }

    override fun affectSpawnable(detail: SpawnDetail, spawnablePosition: SpawnablePosition): Boolean {
        val spawningStyle = spawningStyle
        return if (spawningStyle is NaturalHabitatSpawning) {
            if (spawningStyle.replaceSpawns) {
                // Replace mode! Permit spawning only if it's from this block
                detail in spawningStyle.spawnDetails
            } else {
                true // We don't mess with other types
            }
        } else if (spawningStyle is ActivatedHabitatSpawning) {
            // If this influence is on your spawnable position then it's because we have cancellation on or it's our own spawner, only allow OUR spawns
            spawningStyle.spawner == spawnablePosition.spawner
        } else {
            true // Not likely
        }
    }

    override fun injectSpawns(bucket: SpawnBucket, spawnablePosition: SpawnablePosition): List<SpawnDetail>? {
        val spawningStyle = spawningStyle
        return if (spawningStyle is NaturalHabitatSpawning) {
            spawningStyle.spawnDetails.filter { it.spawnablePositionType.clazz == spawnablePosition::class.java && it.isSatisfiedBy(spawnablePosition) }
        } else {
            null
        }
    }

    companion object {
        private const val DISPLAY_SPECIES_KEY = "DisplaySpecies"
        val TICKER = BlockEntityTicker<HabitatBlockEntity> { world, pos, _, blockEntity ->
            if (world is ServerLevel) {
                if (!blockEntity.initialized) {
                    blockEntity.refreshFromSettings()
                    blockEntity.initialized = true
                }
                val style = blockEntity.spawningStyle

                // Every couple of seconds, check what phase we should be in
                if (world.gameTime % 40 == 0L) {
                    val desiredPhase = blockEntity.calculatePhase(world.gameTime)
                    if (blockEntity.currentPhase != desiredPhase) {
                        blockEntity.currentPhase = desiredPhase
                        blockEntity.setChanged()
                    }
                }
                if (style is ActivatedHabitatSpawning) {
                    if (world.gameTime % 20 == 0L) {
                        blockEntity.spawnedEntityIDs.removeIf { id -> world.getEntity(id)?.isAlive != true }
                    }

                    if (style.trigger == ActivatedHabitatSpawning.Trigger.TICK) {
                        style.activate(world, pos) // TODO if it spawns something it would be good to emit a redstone pulse
                    } else if (style.trigger == ActivatedHabitatSpawning.Trigger.REDSTONE) {
                        val hasSignalNow = world.getDirectSignalTo(pos) > 0
                        if (blockEntity.receivingSignal && !hasSignalNow) {
                            blockEntity.receivingSignal = false
                        } else if (!blockEntity.receivingSignal && hasSignalNow) {
                            blockEntity.receivingSignal = true
                            style.activate(world, pos)
                        }
                    }
                }
            }
        }
    }
}
