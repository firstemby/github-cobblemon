package com.cobblemon.mod.common.block.entity

import com.cobblemon.mod.common.CobblemonBlockEntities
import com.cobblemon.mod.common.api.pokemon.breeding.Egg
import com.cobblemon.mod.common.item.components.EggComponent
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.cobblemonResource
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.Block
import com.cobblemon.mod.common.util.codec.CodecUtils
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.world.level.block.Blocks
import java.util.*

class EggBlockEntity(pos: BlockPos, state: BlockState) : BlockEntity(CobblemonBlockEntities.EGG, pos, state) {
    private var eggComponent: EggComponent? = null
    var renderState: BlockEntityRenderState? = null

    // Component data codec
    companion object {
        val CODEC: Codec<EggBlockEntity> = RecordCodecBuilder.create { instance ->
            instance.group(
                    ResourceLocation.CODEC.optionalFieldOf("species", cobblemonResource("unknown")).forGetter { it.getEgg()?.species ?: cobblemonResource("unknown") },
                    Pokemon.CODEC.optionalFieldOf("parentOne").forGetter { Optional.ofNullable(it.getEgg()?.parentOne) },
                    Pokemon.CODEC.optionalFieldOf("parentTwo").forGetter { Optional.ofNullable(it.getEgg()?.parentTwo) },
                    Codec.INT.optionalFieldOf("timeToHatch", 0).forGetter { it.getEgg()?.timeToHatch ?: 0 }
            ).apply(instance) { species, parentOneOpt, parentTwoOpt, timeToHatch ->
                EggBlockEntity(BlockPos(0, 0, 0), Blocks.AIR.defaultBlockState()).apply {
                    setEgg(
                            Egg(
                                    species = species,
                                    parentOne = parentOneOpt.orElse(null),
                                    parentTwo = parentTwoOpt.orElse(null),
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
    fun setEggData(egg: Egg?) {
        this.eggComponent = EggComponent(egg)
        setChanged()
        level?.sendBlockUpdated(blockPos, blockState, blockState, Block.UPDATE_CLIENTS)
    }

    /**
     * Retrieves the Egg data from the EggComponent.
     */
    /*fun getEgg(): Egg? {
        return eggComponent?.egg ?: Egg(
                species = cobblemonResource("unknown"),
                parentOne = null,
                parentTwo = null,
                timeToHatch = 0
        )
    }*/
    fun getEgg(): Egg? {
        return eggComponent?.egg
    }

    /**
     * Sets the EggComponent and synchronizes with the client.
     */
    fun setEgg(egg: Egg?) {
        this.eggComponent = EggComponent(egg)
        setChanged()
        level?.sendBlockUpdated(blockPos, blockState, blockState, Block.UPDATE_CLIENTS)
    }

    /**
     * Save the EggComponent data manually to NBT.
     */
    override fun saveAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.saveAdditional(tag, registries)
        eggComponent?.egg?.let { egg ->
            Egg.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, egg)
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
            val egg = Egg.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag.get("EggComponent"))
                    .result()
                    ?.orElse(null)
            eggComponent = if (egg != null) EggComponent(egg) else null
        }
    }


    override fun getUpdatePacket(): Packet<ClientGamePacketListener>? {
        return ClientboundBlockEntityDataPacket.create(this)
    }

    override fun getUpdateTag(registryLookup: HolderLookup.Provider): CompoundTag {
        val tag = saveWithoutMetadata(registryLookup)
        return tag
    }

}
