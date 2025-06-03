/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block

import com.cobblemon.mod.common.api.tags.CobblemonItemTags
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.dispenser.DispenseItemBehavior
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.BlockTags
import net.minecraft.util.Mth
import net.minecraft.util.RandomSource
import net.minecraft.world.Containers
import net.minecraft.world.InteractionHand
import net.minecraft.world.ItemInteractionResult
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.*
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.level.block.state.properties.IntegerProperty
import net.minecraft.world.level.gameevent.GameEvent
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.level.pathfinder.PathComputationType
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.EntityCollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
class SaccharineLeafBlock(settings: Properties) : LeavesBlock(settings), BonemealableBlock {

    init {
        this.registerDefaultState(
            this.stateDefinition.any()
                .setValue(AGE, MIN_AGE)
                .setValue(DISTANCE, DISTANCE_MAX)
                .setValue(PERSISTENT, false)
                .setValue(WATERLOGGED, false)
        )
    }

    override fun getStateForPlacement(ctx: BlockPlaceContext): BlockState {
        val level = ctx.level
        val pos = ctx.clickedPos
        val fluidState = level.getFluidState(pos)
        return this.defaultBlockState()
            .setValue(WATERLOGGED, fluidState.`is`(Fluids.WATER))
    }

    override fun isRandomlyTicking(state: BlockState): Boolean {
        return !state.getValue(PERSISTENT) && state.getValue(DISTANCE) > 6
    }

    override fun randomTick(state: BlockState, world: ServerLevel, pos: BlockPos, random: RandomSource) {
        val currentAge = state.getValue(AGE)

        if (currentAge > MIN_AGE && random.nextInt(2) == 0) {
            for (i in 1..10) {
                val belowPos = pos.below(i)
                val belowState = world.getBlockState(belowPos)

                if (belowState.isAir) {
                    continue
                } else if (belowState.block is SaccharineLeafBlock) {
                    val belowAge = belowState.getValue(AGE)
                    if (belowAge < MAX_AGE) {
                        changeAge(state, -1)
                        changeAge(belowState, 1)
                    }
                    break
                } else {
                    break
                }
            }
        }

        super.randomTick(state, world, pos, random)
    }

    @Deprecated("Deprecated in Java")
    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape {
        return SHAPE
    }

    @Deprecated("Deprecated in Java")
    override fun getCollisionShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext
    ): VoxelShape {
        if (context is EntityCollisionContext && (context.entity as? ItemEntity)?.item?.`is`(CobblemonItemTags.APRICORNS) == true) {
            return Shapes.empty()
        }
        return super.getCollisionShape(state, level, pos, context)
    }

    override fun isValidBonemealTarget(level: LevelReader, pos: BlockPos, state: BlockState): Boolean = true

    override fun isBonemealSuccess(level: Level, random: RandomSource, pos: BlockPos, state: BlockState) = true

    override fun performBonemeal(level: ServerLevel, random: RandomSource, pos: BlockPos, state: BlockState) {
        changeAge(state, 1)
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        super.createBlockStateDefinition(builder)
        builder.add(AGE)
    }

    @Deprecated("Deprecated in Java")
    override fun animateTick(state: BlockState, level: Level, pos: BlockPos, random: RandomSource) {

        val particleCount = random.nextInt(3)

        if (state.getValue(AGE) == 2) {
            repeat(particleCount) {
                spawnHoneyParticles(level, pos, state, 0.75f)
            }
        } else if (state.getValue(AGE) == 1) {
            repeat(particleCount) {
                spawnHoneyParticles(level, pos, state, 0.9f)
            }
        }


        // this code was for them aging as time goes on
        /* if (world.random.nextInt(5) == 0) {

            val currentAge = state.get(AGE)
            if (currentAge < MAX_AGE) {
                world.setBlockState(pos, state.setValue(AGE, currentAge + 1), 2)
            }
        }*/
    }

    private fun spawnHoneyParticles(level: Level, pos: BlockPos, state: BlockState, rate: Float) {
        if (state.fluidState.isEmpty && !(level.random.nextFloat() < rate)) {
            val voxelShape = state.getCollisionShape(level, pos)
            val d = voxelShape.max(Direction.Axis.Y)
            if (d >= 1.0 && !state.`is`(BlockTags.IMPERMEABLE)) {
                val e = voxelShape.min(Direction.Axis.Y)
                if (e > 0.0) {
                    this.addHoneyParticle(level, pos, voxelShape, pos.y.toDouble() + e - 0.05)
                } else {
                    val blockPos = pos.below()
                    val blockState = level.getBlockState(blockPos)
                    val voxelShape2 = blockState.getCollisionShape(level, blockPos)
                    val f = voxelShape2.max(Direction.Axis.Y)
                    if ((f < 1.0 || !blockState.isSolidRender(level, blockPos)) && blockState.fluidState.isEmpty) {
                        this.addHoneyParticle(level, pos, voxelShape, pos.y.toDouble() - 0.05)
                    }
                }
            }
        }
    }

    private fun addHoneyParticle(level: Level, pos: BlockPos, shape: VoxelShape, height: Double) {
        this.addHoneyParticle(
            level, pos.x.toDouble() + shape.min(Direction.Axis.X), pos.x.toDouble() + shape.max(
                Direction.Axis.X
            ), pos.z.toDouble() + shape.min(Direction.Axis.Z), pos.z.toDouble() + shape.max(
                Direction.Axis.Z
            ), height
        )
    }

    private fun addHoneyParticle(level: Level, minX: Double, maxX: Double, minZ: Double, maxZ: Double, height: Double) {
        level.addParticle(
            ParticleTypes.DRIPPING_HONEY,
            Mth.lerp(level.random.nextDouble(), minX, maxX),
            height,
            Mth.lerp(level.random.nextDouble(), minZ, maxZ),
            0.0,
            0.0,
            0.0
        )
    }

    override fun isPathfindable(state: BlockState, type: PathComputationType): Boolean = false

    override fun useItemOn(
        stack: ItemStack,
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hand: InteractionHand,
        hit: BlockHitResult
    ): ItemInteractionResult {
        val itemStack = player.getItemInHand(hand)
        val isGlassBottle = itemStack.`is`(Items.GLASS_BOTTLE)
        val isHoneyBottle = itemStack.`is`(Items.HONEY_BOTTLE)

        if (isGlassBottle && !isAtMinAge(state)) {
            // decrement stack if not in creative mode
            if (!player.isCreative)
                itemStack.shrink(1)

            // give player honey bottle for now
            player.addItem(Items.HONEY_BOTTLE.defaultInstance)
            level.setBlock(pos, state.setValue(AGE, 0), 2)
            level.gameEvent(null, GameEvent.BLOCK_CHANGE, pos)
        } else if (isHoneyBottle && !isAtMaxAge(state)) {
            // decrement stack if not in creative mode
            if (!player.isCreative)
                itemStack.shrink(1)

            level.setBlock(pos, state.setValue(AGE, 2), 2)
            level.gameEvent(null, GameEvent.BLOCK_CHANGE, pos)
        }

        if (state.getValue(AGE) != MAX_AGE) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit)
        }

        //doHarvest(world, state, pos, player)
        return ItemInteractionResult.SUCCESS
    }

    private fun isAtMaxAge(state: BlockState) = state.getValue(AGE) == MAX_AGE
    private fun isAtMinAge(state: BlockState) = state.getValue(AGE) == MIN_AGE

    private fun changeAge(state: BlockState, value: Int): BlockState {
        val newAge = (state.getValue(AGE) + value).coerceIn(MIN_AGE, MAX_AGE)
        return state.setValue(AGE, newAge)
    }

    override fun spawnDestroyParticles(level: Level, player: Player, pos: BlockPos, state: BlockState) {
        if (isAtMinAge(state)) {
            super.spawnDestroyParticles(level, player, pos, state)
            return
        } else {
            // TODO: Change to Yellow Leaves in stead of concrete
            val particle = Blocks.YELLOW_CONCRETE_POWDER.defaultBlockState()
            level.levelEvent(
                LevelEvent.PARTICLES_DESTROY_BLOCK,
                pos,
                Block.getId(particle)
            )
        }
    }

    /*override fun onBlockBreakStart(state: BlockState, world: World, pos: BlockPos, player: PlayerEntity) {
        if (state.get(AGE) != MAX_AGE) {
            return super.onBlockBreakStart(state, world, pos, player)
        }

        //doHarvest(world, state, pos, player)
    }*/

    companion object {

        val AGE: IntegerProperty = BlockStateProperties.AGE_2
        val DISTANCE: IntegerProperty = BlockStateProperties.DISTANCE
        val PERSISTENT: BooleanProperty = BlockStateProperties.PERSISTENT
        const val MAX_AGE = 2
        const val MIN_AGE = 0
        const val DISTANCE_MAX = 7

        private val SHAPE: VoxelShape = box(0.0, 0.0, 0.0, 16.0, 16.0, 16.0)

        fun createBehavior(item: Item): DispenseItemBehavior {
            return DispenseItemBehavior { source, stack ->
                val level = source.level
                val facing = source.state.getValue(DispenserBlock.FACING)
                val pos = source.pos.relative(facing)
                val blockState = level.getBlockState(pos)

                if (blockState.block is SaccharineLeafBlock) {
                    val currentAge = blockState.getValue(AGE)
                    val newAge = when {
                        item == Items.HONEY_BOTTLE && currentAge < MAX_AGE -> (currentAge + 2).coerceAtMost(MAX_AGE)
                        item == Items.GLASS_BOTTLE && currentAge > MIN_AGE -> (currentAge - 2).coerceAtLeast(MIN_AGE)
                        else -> currentAge
                    }

                    if (newAge != currentAge) {
                        level.setBlock(pos, blockState.setValue(AGE, newAge), 3)
                        level.gameEvent(null, GameEvent.BLOCK_CHANGE, pos)
                        stack.shrink(1)

                        val dispenserEntity = source.blockEntity
                        if (dispenserEntity != null) {
                            val outputItem = if (item == Items.HONEY_BOTTLE) Items.GLASS_BOTTLE else Items.HONEY_BOTTLE
                            val outputStack = ItemStack(outputItem)
                            var added = false

                            for (i in 0 until dispenserEntity.containerSize) {
                                val slotStack = dispenserEntity.getItem(i)

                                if (slotStack.isEmpty) {
                                    dispenserEntity.setItem(i, outputStack.copy())
                                    added = true
                                    break
                                } else if (slotStack.`is`(outputItem) && slotStack.count < slotStack.maxStackSize) {
                                    slotStack.grow(1)
                                    added = true
                                    break
                                }
                            }

                            if (!added) {
                                Containers.dropItemStack(
                                    level,
                                    source.pos.x.toDouble(),
                                    source.pos.y.toDouble(),
                                    source.pos.z.toDouble(),
                                    outputStack
                                )
                            }
                        }
                    }
                }

                stack
            }
        }
    }

}
