package com.cobblemon.mod.common.block.entity

import com.cobblemon.mod.common.CobblemonBlockEntities
import com.cobblemon.mod.common.api.pokemon.breeding.Egg
import com.cobblemon.mod.common.item.components.EggComponent
import com.cobblemon.mod.common.util.cobblemonResource
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.Block
import java.util.UUID
import com.cobblemon.mod.common.util.codec.CodecUtils
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.block.Blocks

class EggBlockEntity(pos: BlockPos, state: BlockState) : BlockEntity(CobblemonBlockEntities.EGG, pos, state) {
    private var eggComponent: EggComponent? = null
    var renderState: BlockEntityRenderState? = null

    // Component data codec
    companion object {
        val CODEC: Codec<EggBlockEntity> = RecordCodecBuilder.create { instance ->
            instance.group(
                    ResourceLocation.CODEC.optionalFieldOf("species", cobblemonResource("unknown")).forGetter { it.getEgg()?.species ?: cobblemonResource("unknown") },
                    CodecUtils.UUID_CODEC.optionalFieldOf("parentOneUUID", UUID.randomUUID()).forGetter { it.getEgg()?.parentOneUUID ?: UUID.randomUUID() },
                    CodecUtils.UUID_CODEC.optionalFieldOf("parentTwoUUID", UUID.randomUUID()).forGetter { it.getEgg()?.parentTwoUUID ?: UUID.randomUUID() },
                    Codec.INT.optionalFieldOf("timeToHatch", 0).forGetter { it.getEgg()?.timeToHatch ?: 0 }
            ).apply(instance) { species, parentOneUUID, parentTwoUUID, timeToHatch ->
                EggBlockEntity(BlockPos(0, 0, 0), Blocks.AIR.defaultBlockState()).apply {
                    setEgg(
                            Egg(
                                    species = species,
                                    parentOneUUID = parentOneUUID,
                                    parentTwoUUID = parentTwoUUID,
                                    timeToHatch = timeToHatch
                            )
                    )
                }
            }
        }
    }

    /**
     * Sets the egg data and notifies the client.
     */
    fun setEggData(egg: Egg) {
        this.eggComponent = EggComponent(egg)
        setChanged()
        level?.sendBlockUpdated(blockPos, blockState, blockState, Block.UPDATE_CLIENTS)
    }

    /**
     * Retrieves the Egg data from the EggComponent.
     */
    fun getEgg(): Egg? {
        return eggComponent?.egg
    }

    /**
     * Sets the EggComponent and synchronizes with the client.
     */
    fun setEgg(egg: Egg) {
        this.eggComponent = EggComponent(egg)
        setChanged()
        level?.sendBlockUpdated(blockPos, blockState, blockState, Block.UPDATE_CLIENTS)
    }

    /**
     * Save the EggComponent data manually to NBT.
     */
    override fun saveAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.saveAdditional(tag, registries)
        eggComponent?.let { component ->
            EggComponent.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, component)
                    .result()
                    ?.ifPresent { encoded -> tag.put("EggComponent", encoded) }
        }
    }

    /**
     * Load the EggComponent data manually from NBT.
     */
    override fun loadAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.loadAdditional(tag, registries)
        if (tag.contains("EggComponent")) {
            eggComponent = EggComponent.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag.get("EggComponent"))
                    .result()
                    ?.orElse(null)
            println("Client loaded EggComponent: ${eggComponent?.egg?.species}")
        }
    }
}
