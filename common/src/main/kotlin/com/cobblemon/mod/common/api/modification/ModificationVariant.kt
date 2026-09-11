/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.modification

import com.cobblemon.mod.common.CobblemonItemComponents
import com.cobblemon.mod.common.item.components.ModificationVariantComponent
import com.cobblemon.mod.common.util.rotateShape
import java.util.EnumMap
import net.minecraft.core.Direction
import net.minecraft.core.Vec3i
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.shapes.BooleanOp
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

class ModificationVariant(
    val identifier: ResourceLocation,
    val parent: ResourceLocation,
    val block: ResourceLocation,
    val model: ResourceLocation,
    val blockstateModels: List<ModificationVariantBlockstateModel>,
    val cells: List<Vec3i>,
    val hitbox: List<AABB>,
    val order: Int,
    val isTranslucent: Boolean
) {
    private val hitboxesByFacing = EnumMap<Direction, VoxelShape>(Direction::class.java)
    private val cellHitboxes = hashMapOf<Pair<Direction, Vec3i>, VoxelShape>()

    fun targetBlock(): Block? = BuiltInRegistries.BLOCK.getOptional(block).orElse(null)

    fun parentItem(): Item? = BuiltInRegistries.ITEM.getOptional(parent).orElse(null)

    fun displayName(): MutableComponent = Component.translatable("modification_variant.${identifier.namespace}.${identifier.path}")

    fun modelFor(state: BlockState): ResourceLocation = blockstateModels.firstOrNull { it.matches(state) }?.model ?: model

    fun allModels(): List<ResourceLocation> = listOf(model) + blockstateModels.map { it.model }

    fun createStack(): ItemStack {
        val item = targetBlock()?.asItem() ?: return ItemStack.EMPTY
        if (item == Items.AIR) {
            return ItemStack.EMPTY
        }
        return ItemStack(item).apply {
            set(CobblemonItemComponents.MODIFICATION_VARIANT, ModificationVariantComponent(identifier))
            set(DataComponents.ITEM_NAME, displayName())
        }
    }

    fun rotatedCells(facing: Direction): List<Vec3i> = cells.map { rotateCell(it, facing) }

    fun fillerCells(facing: Direction): List<Vec3i> = rotatedCells(facing).filter { it != Vec3i.ZERO }

    fun cellsMin(): Vec3i = Vec3i(cells.minOf { it.x }, cells.minOf { it.y }, cells.minOf { it.z })

    fun cellsMax(): Vec3i = Vec3i(cells.maxOf { it.x }, cells.maxOf { it.y }, cells.maxOf { it.z })

    fun hitbox(facing: Direction): VoxelShape = hitboxesByFacing.getOrPut(facing) { rotateShape(Direction.SOUTH, facing, unrotatedHitbox()) }

    fun cellHitbox(facing: Direction, cell: Vec3i): VoxelShape = cellHitboxes.getOrPut(facing to cell) {
        val shifted = hitbox(facing).move(-cell.x.toDouble(), -cell.y.toDouble(), -cell.z.toDouble())
        Shapes.join(shifted, Shapes.block(), BooleanOp.AND)
    }

    fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeResourceLocation(identifier)
        buffer.writeResourceLocation(parent)
        buffer.writeResourceLocation(block)
        buffer.writeResourceLocation(model)
        buffer.writeCollection(blockstateModels) { buf, blockstateModel -> blockstateModel.encode(buf) }
        buffer.writeCollection(cells) { buf, cell ->
            buf.writeVarInt(cell.x)
            buf.writeVarInt(cell.y)
            buf.writeVarInt(cell.z)
        }
        buffer.writeCollection(hitbox) { buf, box ->
            buf.writeDouble(box.minX)
            buf.writeDouble(box.minY)
            buf.writeDouble(box.minZ)
            buf.writeDouble(box.maxX)
            buf.writeDouble(box.maxY)
            buf.writeDouble(box.maxZ)
        }
        buffer.writeVarInt(order)
        buffer.writeBoolean(isTranslucent)
    }

    private fun unrotatedHitbox(): VoxelShape {
        val boxes = hitbox.ifEmpty { cells.map(::cellBox) }
        return boxes.fold(Shapes.empty()) { combined, box ->
            Shapes.or(combined, Shapes.box(box.minX / 16.0, box.minY / 16.0, box.minZ / 16.0, box.maxX / 16.0, box.maxY / 16.0, box.maxZ / 16.0))
        }
    }

    companion object {
        const val MAX_HEIGHT = 5
        const val MAX_DEPTH = 3
        const val MAX_HITBOX_BOXES = 64
        val MIN_CELL_OFFSET = Vec3i(-2, 0, -2)
        val MAX_CELL_OFFSET = Vec3i(2, 4, 2)

        fun rotateCell(cell: Vec3i, facing: Direction): Vec3i {
            var rotated = cell
            repeat(facing.get2DDataValue()) {
                rotated = Vec3i(-rotated.z, rotated.y, rotated.x)
            }
            return rotated
        }

        fun cellBox(cell: Vec3i): AABB = AABB(
            cell.x * 16.0,
            cell.y * 16.0,
            cell.z * 16.0,
            (cell.x + 1) * 16.0,
            (cell.y + 1) * 16.0,
            (cell.z + 1) * 16.0
        )

        fun decode(buffer: RegistryFriendlyByteBuf): ModificationVariant = ModificationVariant(
            identifier = buffer.readResourceLocation(),
            parent = buffer.readResourceLocation(),
            block = buffer.readResourceLocation(),
            model = buffer.readResourceLocation(),
            blockstateModels = buffer.readList { buf -> ModificationVariantBlockstateModel.decode(buf) },
            cells = buffer.readList { buf -> Vec3i(buf.readVarInt(), buf.readVarInt(), buf.readVarInt()) },
            hitbox = buffer.readList { buf ->
                AABB(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble())
            },
            order = buffer.readVarInt(),
            isTranslucent = buffer.readBoolean()
        )
    }
}
