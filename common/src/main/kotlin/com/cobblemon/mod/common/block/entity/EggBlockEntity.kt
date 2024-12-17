package com.cobblemon.mod.common.block.entity

import com.cobblemon.mod.common.CobblemonBlockEntities
import com.cobblemon.mod.common.api.pokemon.breeding.Egg
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
import net.minecraft.world.level.block.Blocks

class EggBlockEntity(pos: BlockPos, state: BlockState) : BlockEntity(CobblemonBlockEntities.EGG, pos, state) {
    var egg: Egg? = null
    var renderState: BlockEntityRenderState? = null

    // Component data codec
    companion object {
        val CODEC: Codec<EggBlockEntity> = RecordCodecBuilder.create { instance ->
            instance.group(
                    ResourceLocation.CODEC.optionalFieldOf("species", cobblemonResource("unknown")).forGetter { it.egg?.species ?: cobblemonResource("unknown") },
                    CodecUtils.UUID_CODEC.optionalFieldOf("parentOneUUID", UUID.randomUUID()).forGetter { it.egg?.parentOneUUID ?: UUID.randomUUID() },
                    CodecUtils.UUID_CODEC.optionalFieldOf("parentTwoUUID", UUID.randomUUID()).forGetter { it.egg?.parentTwoUUID ?: UUID.randomUUID() },
                    Codec.INT.optionalFieldOf("timeToHatch", 0).forGetter { it.egg?.timeToHatch ?: 0 }
            ).apply(instance) { species, parentOneUUID, parentTwoUUID, timeToHatch ->
                EggBlockEntity(BlockPos(0, 0, 0), Blocks.AIR.defaultBlockState()).apply {
                    this.egg = Egg(
                            species = species,
                            parentOneUUID = parentOneUUID,
                            parentTwoUUID = parentTwoUUID,
                            timeToHatch = timeToHatch
                    )
                }
            }
        }
    }

    /**
     * Sets the egg data and notifies the client.
     */
    fun setEggData(egg: Egg) {
        this.egg = egg
        setChanged()
        level?.sendBlockUpdated(blockPos, blockState, blockState, Block.UPDATE_CLIENTS)
    }
}
