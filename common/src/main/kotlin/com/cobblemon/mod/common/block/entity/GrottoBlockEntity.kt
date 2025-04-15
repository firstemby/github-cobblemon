package com.cobblemon.mod.common.block.entity

import com.cobblemon.mod.common.CobblemonBlockEntities
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.levelgen.structure.StructureStart
import net.minecraft.world.phys.BlockHitResult

class GrottoBlockEntity(pos: BlockPos, state: BlockState) :
        BlockEntity(CobblemonBlockEntities.GROTTO_BLOCK, pos, state) {

    var detectedStructure: ResourceLocation? = null
    var hasCheckedStructure: Boolean = false

    var mimicId: ResourceLocation = BuiltInRegistries.BLOCK.getKey(Blocks.SPONGE)
    val mimickedState: BlockState
        get() = BuiltInRegistries.BLOCK.get(mimicId).defaultBlockState() ?: Blocks.SPONGE.defaultBlockState()

    fun setMimic(id: ResourceLocation) {
        mimicId = id
        setChanged()
        level?.sendBlockUpdated(worldPosition, blockState, blockState, Block.UPDATE_ALL)
    }

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

    fun cycleMimic() {
        mimicIndex = (mimicIndex + 1) % mimicCycle.size
        mimicId = mimicCycle[mimicIndex]
        setChanged()
        level?.sendBlockUpdated(worldPosition, blockState, blockState, Block.UPDATE_CLIENTS)
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
    }

    override fun loadAdditional(tag: CompoundTag, registryLookup: HolderLookup.Provider) {
        super.loadAdditional(tag, registryLookup)
        tag.getString("MimicId").let {
            mimicId = ResourceLocation.tryParse(it) ?: BuiltInRegistries.BLOCK.getKey(Blocks.SPONGE)
        }
        hasCheckedStructure = tag.getBoolean("HasCheckedStructure")
        detectedStructure = tag.getString("DetectedStructure").takeIf { it.isNotEmpty() }?.let(ResourceLocation::tryParse)
    }

    override fun getUpdatePacket(): Packet<ClientGamePacketListener> {
        return ClientboundBlockEntityDataPacket.create(this)
    }

    override fun getUpdateTag(registryLookup: HolderLookup.Provider): CompoundTag {
        return saveWithoutMetadata(registryLookup)
    }

    fun findNearbyStructure(level: ServerLevel, pos: BlockPos, radius: Int): ResourceLocation? {
        val structureManager = level.structureManager()
        val registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE)

        for (entry in registry.entrySet()) {
            val structure = entry.value
            val structureStart = structureManager.getStructureWithPieceAt(pos, structure)
            if (structureStart != StructureStart.INVALID_START && structureStart.boundingBox.isInside(pos)) {
                return entry.key.location()
            }
        }

        return null
    }

    companion object {
        val TICKER = BlockEntityTicker<GrottoBlockEntity> { world, pos, state, blockEntity ->
            if (world is ServerLevel && !blockEntity.hasCheckedStructure) {
                blockEntity.hasCheckedStructure = true

                val structure = blockEntity.findNearbyStructure(world, pos, radius = 5)
                if (structure != null) {
                    blockEntity.detectedStructure = structure
                    blockEntity.setChanged()
                    world.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS)

                    println("[GrottoBlockEntity] Detected structure at $pos: $structure")
                }
            }
        }
    }
}
