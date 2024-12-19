package com.cobblemon.mod.common.block

import com.cobblemon.mod.common.block.entity.IncubatorBlockEntity
import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.ItemInteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.DirectionProperty
import net.minecraft.world.phys.BlockHitResult

class IncubatorBlock(settings: Properties) : BaseEntityBlock(settings) {

    companion object {
        val FACING: DirectionProperty = DirectionProperty.create("facing", { dir -> dir.axis.isHorizontal })
        val CODEC: MapCodec<IncubatorBlock> = simpleCodec(::IncubatorBlock) as MapCodec<IncubatorBlock>
    }

    init {
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH))
    }

    override fun getStateForPlacement(ctx: BlockPlaceContext): BlockState? {
        return defaultBlockState().setValue(FACING, ctx.horizontalDirection.opposite)
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(FACING)
    }

    override fun newBlockEntity(pos: BlockPos, state: BlockState): IncubatorBlockEntity {
        return IncubatorBlockEntity(pos, state)
    }

    override fun useWithoutItem(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hitResult: BlockHitResult
    ): InteractionResult {
        return super.useWithoutItem(state, level, pos, player, hitResult)
    }

    override fun useItemOn(
        stack: ItemStack,
        state: BlockState,
        world: Level,
        pos: BlockPos,
        player: Player,
        hand: InteractionHand,
        hit: BlockHitResult
    ): ItemInteractionResult {
        val entity = world.getBlockEntity(pos) as? IncubatorBlockEntity ?: return ItemInteractionResult.FAIL
        return entity.updateEgg(player, hand)
    }

    override fun playerWillDestroy(level: Level, pos: BlockPos, state: BlockState, player: Player): BlockState {
        val entity = level.getBlockEntity(pos) as? IncubatorBlockEntity
        entity?.let {
            if (!player.isCreative) {
                it.clearContent()
            }
        }
        return super.playerWillDestroy(level, pos, state, player)
    }

    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.MODEL

    override fun codec(): MapCodec<out BaseEntityBlock> {
        return CODEC
    }
}
