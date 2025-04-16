package com.cobblemon.mod.common.block

import com.cobblemon.mod.common.CobblemonBlockEntities
import com.cobblemon.mod.common.block.entity.GrottoBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.loot.LootContext
import net.minecraft.world.level.storage.loot.LootParams
import net.minecraft.world.level.storage.loot.parameters.LootContextParams
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import java.util.*

class GrottoBlock(properties: Properties) : Block(properties), EntityBlock {
    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity {
        return GrottoBlockEntity(pos, state)
    }

    override fun <T : BlockEntity> getTicker(
        level: Level,
        state: BlockState,
        type: BlockEntityType<T>
    ): BlockEntityTicker<T>? {
        return if (!level.isClientSide && type == CobblemonBlockEntities.GROTTO_BLOCK) {
            @Suppress("UNCHECKED_CAST")
            GrottoBlockEntity.TICKER as BlockEntityTicker<T>
        } else {
            null
        }
    }

    override fun useWithoutItem(state: BlockState, level: Level, pos: BlockPos, player: Player, hitResult: BlockHitResult): InteractionResult {
        if (!level.isClientSide) {
            val be = level.getBlockEntity(pos) as? GrottoBlockEntity ?: return InteractionResult.PASS
            return be.onUse(player, hitResult)
        }
        return InteractionResult.SUCCESS
    }

    override fun getDrops(state: BlockState, params: LootParams.Builder): MutableList<ItemStack> {
        val blockEntity = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) as? GrottoBlockEntity
        val player = params.getOptionalParameter(LootContextParams.THIS_ENTITY) as? Player

        if (player?.isCreative == true) return mutableListOf()

        val mimic = blockEntity?.mimickedState?.block ?: return mutableListOf()
        return mutableListOf(ItemStack(mimic))
    }

    override fun getRenderShape(state: BlockState): RenderShape {
        return RenderShape.MODEL
    }

    override fun getShape(state: BlockState, world: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape {
        return box(0.0, 0.0, 0.0, 16.0, 16.0, 16.0)
    }

    override fun getCollisionShape(
            state: BlockState,
            level: BlockGetter,
            pos: BlockPos,
            context: CollisionContext
    ): VoxelShape {
        return box(0.0, 0.0, 0.0, 16.0, 16.0, 16.0)
    }

    override fun getVisualShape(
        state: BlockState,
        world: BlockGetter,
        pos: BlockPos,
        context: CollisionContext
    ): VoxelShape {
        return box(0.0, 0.0, 0.0, 16.0, 16.0, 16.0)
    }

    override fun useShapeForLightOcclusion(state: BlockState): Boolean = true
}
