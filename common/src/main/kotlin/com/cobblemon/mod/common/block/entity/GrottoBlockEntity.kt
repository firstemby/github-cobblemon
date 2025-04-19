package com.cobblemon.mod.common.block.entity

import com.cobblemon.mod.common.CobblemonBlockEntities
import com.cobblemon.mod.common.api.habitats.Habitats
import com.cobblemon.mod.common.api.habitats.WeightedSpecies
import com.cobblemon.mod.common.block.GrottoBlock
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.levelgen.structure.StructureStart
import net.minecraft.world.phys.BlockHitResult

class GrottoBlockEntity(pos: BlockPos, state: BlockState) :
    BlockEntity(CobblemonBlockEntities.GROTTO_BLOCK, pos, state) {

    var detectedStructure: ResourceLocation? = null
    var structureCenter: BlockPos? = null
    var hasCheckedStructure: Boolean = false
    var lastMoonPhaseChecked: Int = -1
    var currentSpawnGroup: List<WeightedSpecies> = emptyList()

    var mimicId: ResourceLocation = BuiltInRegistries.BLOCK.getKey(Blocks.STONE)
    val mimickedState: BlockState
        get() = BuiltInRegistries.BLOCK.get(mimicId).defaultBlockState() ?: Blocks.STONE.defaultBlockState()

    // todo to test with checking how it looks as other blocks
    val mimicCycle: List<ResourceLocation> = listOf(
        ResourceLocation.parse("minecraft:stone"),
        ResourceLocation.parse("minecraft:cobblestone"),
        ResourceLocation.parse("minecraft:granite"),
        ResourceLocation.parse("minecraft:diorite"),
        ResourceLocation.parse("minecraft:andesite"),
        ResourceLocation.parse("minecraft:deepslate"),
        ResourceLocation.parse("minecraft:polished_deepslate"),

        ResourceLocation.parse("minecraft:oak_planks"),
        ResourceLocation.parse("minecraft:spruce_planks"),
        ResourceLocation.parse("minecraft:birch_planks"),
        ResourceLocation.parse("minecraft:jungle_planks"),
        ResourceLocation.parse("minecraft:acacia_planks"),
        ResourceLocation.parse("minecraft:dark_oak_planks"),
        ResourceLocation.parse("minecraft:mangrove_planks"),
        ResourceLocation.parse("minecraft:cherry_planks"),
        ResourceLocation.parse("minecraft:bamboo_planks"),

        ResourceLocation.parse("minecraft:bricks"),
        ResourceLocation.parse("minecraft:mossy_cobblestone"),
        ResourceLocation.parse("minecraft:moss_block"),
        ResourceLocation.parse("minecraft:bookshelf"),
        ResourceLocation.parse("minecraft:loom"),
        ResourceLocation.parse("minecraft:note_block"),

        ResourceLocation.parse("minecraft:coal_block"),
        ResourceLocation.parse("minecraft:iron_block"),
        ResourceLocation.parse("minecraft:gold_block"),
        ResourceLocation.parse("minecraft:diamond_block"),
        ResourceLocation.parse("minecraft:emerald_block"),
        ResourceLocation.parse("minecraft:lapis_block"),
        ResourceLocation.parse("minecraft:redstone_block"),
        ResourceLocation.parse("minecraft:netherite_block"),

        ResourceLocation.parse("minecraft:obsidian"),
        ResourceLocation.parse("minecraft:crying_obsidian"),
        ResourceLocation.parse("minecraft:glowstone"),
        ResourceLocation.parse("minecraft:blue_ice"),
        ResourceLocation.parse("minecraft:packed_ice"),
        ResourceLocation.parse("minecraft:ice"),

        ResourceLocation.parse("minecraft:sandstone"),
        ResourceLocation.parse("minecraft:red_sandstone"),
        ResourceLocation.parse("minecraft:cut_sandstone"),
        ResourceLocation.parse("minecraft:smooth_sandstone"),

        ResourceLocation.parse("minecraft:black_concrete"),
        ResourceLocation.parse("minecraft:white_concrete"),
        ResourceLocation.parse("minecraft:lime_concrete"),
        ResourceLocation.parse("minecraft:cyan_concrete"),
        ResourceLocation.parse("minecraft:purple_concrete"),
        ResourceLocation.parse("minecraft:light_blue_concrete"),
        ResourceLocation.parse("minecraft:yellow_concrete"),
        ResourceLocation.parse("minecraft:red_concrete")
    )

    var mimicIndex: Int = 0

    fun setMimic(id: ResourceLocation) {
        mimicId = id
        setChanged()
        level?.sendBlockUpdated(worldPosition, blockState, blockState, Block.UPDATE_ALL)
    }

    fun cycleMimic() {
        mimicIndex = (mimicIndex + 1) % mimicCycle.size
        setMimic(mimicCycle[mimicIndex])
    }

    fun onUse(player: Player, hit: BlockHitResult): InteractionResult {
        cycleMimic()
        player.displayClientMessage(Component.literal("Mimicking: $mimicId"), true)
        return InteractionResult.SUCCESS
    }

    override fun saveAdditional(tag: CompoundTag, registryLookup: HolderLookup.Provider) {
        super.saveAdditional(tag, registryLookup)
        tag.putString("MimicId", mimicId.toString())
        detectedStructure?.let { tag.putString("DetectedStructure", it.toString()) }
        tag.putBoolean("HasCheckedStructure", hasCheckedStructure)

        val list = ListTag()
        currentSpawnGroup.forEach {
            val entry = CompoundTag()
            entry.putString("species", it.species.toString())
            entry.putInt("weight", it.weight)
            list.add(entry)
        }
        tag.put("CurrentSpawnGroup", list)

        structureCenter?.let {
            val centerTag = CompoundTag()
            centerTag.putInt("X", it.x)
            centerTag.putInt("Y", it.y)
            centerTag.putInt("Z", it.z)
            tag.put("StructureCenter", centerTag)
        }
    }

    override fun loadAdditional(tag: CompoundTag, registryLookup: HolderLookup.Provider) {
        super.loadAdditional(tag, registryLookup)
        tag.getString("MimicId").let {
            mimicId = ResourceLocation.tryParse(it) ?: BuiltInRegistries.BLOCK.getKey(Blocks.STONE)
        }
        hasCheckedStructure = tag.getBoolean("HasCheckedStructure")
        detectedStructure =
            tag.getString("DetectedStructure").takeIf { it.isNotEmpty() }?.let(ResourceLocation::tryParse)

        val list = tag.getList("CurrentSpawnGroup", Tag.TAG_COMPOUND.toInt())
        currentSpawnGroup = list.mapNotNull {
            if (it !is CompoundTag) return@mapNotNull null
            val species = ResourceLocation.tryParse(it.getString("species")) ?: return@mapNotNull null
            val weight = it.getInt("weight")
            WeightedSpecies(species, weight)
        }

        if (tag.contains("StructureCenter", Tag.TAG_COMPOUND.toInt())) {
            val centerTag = tag.getCompound("StructureCenter")
            val x = centerTag.getInt("X")
            val y = centerTag.getInt("Y")
            val z = centerTag.getInt("Z")
            structureCenter = BlockPos(x, y, z)
        }
    }

    override fun getUpdatePacket(): Packet<ClientGamePacketListener> {
        return ClientboundBlockEntityDataPacket.create(this)
    }

    override fun getUpdateTag(registryLookup: HolderLookup.Provider): CompoundTag {
        return saveWithoutMetadata(registryLookup)
    }

    fun updateLevelAndPos(level: Level, pos: BlockPos) {
        val block = this.blockState.block
        if (block is GrottoBlock) {
            block.level = level
            block.pos = pos
        }
    }

    fun findNearbyStructure(level: ServerLevel, pos: BlockPos, radius: Int): ResourceLocation? {
        val structureManager = level.structureManager()
        val registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE)

        for (entry in registry.entrySet()) {
            val structure = entry.value
            val structureStart = structureManager.getStructureWithPieceAt(pos, structure)
            if (structureStart != StructureStart.INVALID_START && structureStart.boundingBox.isInside(pos)) {
                structureCenter = structureStart.boundingBox.center
                return entry.key.location()
            }
        }

        return null
    }

    companion object {
        val TICKER = BlockEntityTicker<GrottoBlockEntity> { world, pos, state, blockEntity ->
            if (world is ServerLevel) {
                if (!blockEntity.hasCheckedStructure) {
                    blockEntity.hasCheckedStructure = true

                    val structure = blockEntity.findNearbyStructure(world, pos, radius = 5)
                    if (structure != null) {
                        blockEntity.detectedStructure = structure
                        blockEntity.setChanged()
                        world.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS)

                        println("[GrottoBlockEntity] Found structure at $pos: $structure")
                    }
                }

                val currentMoonPhase = world.moonPhase
                if (currentMoonPhase != blockEntity.lastMoonPhaseChecked) {
                    blockEntity.lastMoonPhaseChecked = currentMoonPhase

                    if (currentMoonPhase == 7 || blockEntity.currentSpawnGroup.isEmpty()) {
                        val structure = blockEntity.detectedStructure
                        if (structure != null) {
                            blockEntity.detectedStructure = structure
                            blockEntity.setChanged()
                            world.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS)

                            println("[GrottoBlockEntity] Detected structure at $pos: $structure")

                            val habitat = Habitats.getHabitat(structure)
                            if (habitat != null && habitat.spawnPool.isNotEmpty()) {
                                val selectedGroup = weightedGroupRoll(habitat.spawnPool)
                                blockEntity.currentSpawnGroup = selectedGroup.spawns
                                blockEntity.lastMoonPhaseChecked = world.moonPhase
                                blockEntity.setChanged()

                                println("[GrottoBlockEntity] Selected spawn group for $structure: ${blockEntity.currentSpawnGroup.map { it.species }}")
                            }
                        }
                    }
                }
            }
        }

        private fun weightedGroupRoll(groups: List<com.cobblemon.mod.common.api.habitats.WeightedGroup>): com.cobblemon.mod.common.api.habitats.WeightedGroup {
            val totalWeight = groups.sumOf { it.weight.toDouble() }
            var roll = Math.random() * totalWeight
            for (group in groups) {
                roll -= group.weight
                if (roll <= 0) return group
            }
            return groups.last() // fallback
        }
    }
}
