package com.cobblemon.mod.common.block

import com.cobblemon.mod.common.CobblemonBlockEntities
import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.block.entity.EggBlockEntity
import com.cobblemon.mod.common.block.entity.IncubatorBlockEntity
import com.cobblemon.mod.common.block.entity.NestBlockEntity
import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.ItemInteractionResult
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.AirItem
import net.minecraft.world.item.EggItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.PickaxeItem
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.level.block.state.properties.DirectionProperty
import net.minecraft.world.phys.BlockHitResult

class IncubatorBlock(settings: Properties) : BaseEntityBlock(settings) {

    companion object {
        val FACING: DirectionProperty = DirectionProperty.create("facing", { dir -> dir.axis.isHorizontal })
        val CODEC: MapCodec<IncubatorBlock> = simpleCodec(::IncubatorBlock) as MapCodec<IncubatorBlock>
    }

    init {
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, net.minecraft.core.Direction.NORTH)
        )
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
        blockState: BlockState,
        level: Level,
        blockPos: BlockPos,
        player: Player,
        blockHitResult: BlockHitResult
    ): InteractionResult {
        // Ensure this logic only runs on the server
        if (level.isClientSide) {
            return InteractionResult.SUCCESS // Client-side only needs to show the hand animation
        }

        val serverPlayer = player as? ServerPlayer ?: return InteractionResult.FAIL // Cast safely to ServerPlayer

        val entity = level.getBlockEntity(blockPos) as? IncubatorBlockEntity ?: return InteractionResult.FAIL

        val egg = entity.getEgg()
        if (egg != null && player.uuid != egg?.parentOne?.getOwnerUUID() && player.uuid != egg?.parentTwo?.getOwnerUUID()) {
            return InteractionResult.FAIL
        }

        return entity.onUse(blockState, level, blockPos, serverPlayer, InteractionHand.MAIN_HAND, blockHitResult)
    }

    override fun playerWillDestroy(level: Level, pos: BlockPos, state: BlockState, player: Player): BlockState {
        val entity = level.getBlockEntity(pos) as? IncubatorBlockEntity
        val itemStack = player.mainHandItem

        entity?.let {
            if (!player.isCreative) {
                it.dropEgg(state, level, pos)
                if (itemStack.item is PickaxeItem) {
                    val itemEntity = ItemEntity(level, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, CobblemonItems.INCUBATOR.defaultInstance)
                    level.addFreshEntity(itemEntity)
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player)
    }

    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.MODEL

    override fun codec(): MapCodec<out BaseEntityBlock> {
        return CODEC
    }

    override fun <T : BlockEntity?> getTicker(level: Level, blockState: BlockState, blockEntityType: BlockEntityType<T>): BlockEntityTicker<T>? {
        return createTickerHelper(blockEntityType, CobblemonBlockEntities.INCUBATOR, IncubatorBlockEntity.TICKER)
    }
}
