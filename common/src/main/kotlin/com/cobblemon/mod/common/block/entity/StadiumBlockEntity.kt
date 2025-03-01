package com.cobblemon.mod.common.block.entity

import com.cobblemon.mod.common.CobblemonBlockEntities
import com.cobblemon.mod.common.client.render.models.blockbench.drone.DroneState
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.network.protocol.game.ClientboundPlayerLookAtPacket
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3

class StadiumBlockEntity(
    pos: BlockPos,
    blockState: BlockState
): BlockEntity(
    CobblemonBlockEntities.STADIUM,
    pos,
    blockState
) {
    var isInUse = false

    val droneState = DroneState()

    val pathPoints: MutableList<Vec3> = mutableListOf()

    fun generateFlightPath(world: Level, radiusX: Double, radiusZ: Double, height: Double, steps: Int) {
        pathPoints.clear()

        for (i in 0 until steps) {
            val angle = (i.toDouble() / steps) * Math.PI * 2
            val xOffset = radiusX * Math.cos(angle)
            val zOffset = radiusZ * Math.sin(angle)

            val basePos = Vec3(blockPos.x + xOffset, blockPos.y + height, blockPos.z + zOffset)
            val adjustedPos = getValidPoint(world, basePos)

            pathPoints.add(adjustedPos)
        }
    }

    private fun getValidPoint(world: Level, start: Vec3): Vec3 {
        val maxHeight = 40 // Max height for drone path
        val stepHeight = 1.0 // Step size for raycast

        var adjustedHeight = start.y
        for (h in 0..maxHeight step stepHeight.toInt()) {
            val checkPos = BlockPos(start.x.toInt(), start.y.toInt() - h, start.z.toInt()) // Convert to BlockPos
            if (!world.getBlockState(checkPos).isAir) { // Check if block is solid
                return Vec3(start.x, start.y - h - 1, start.z) // Adjust position
            }
        }
        return start // No obstruction found
    }


    fun getPlayer1Pos(): BlockPos {
        return blockPos.north(10)
    }
    fun getPlayer2Pos(): BlockPos {
        return blockPos.south(10)
    }
    fun getPoke1Pos(): BlockPos {
        return blockPos.north(5)
    }
    fun getPoke2Pos(): BlockPos {
        return blockPos.south(5)
    }




    /**
     * Saves data to NBT.
     */
    override fun saveAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.saveAdditional(tag, registries)

        // Save the isInUse state
        tag.putBoolean("IsInUse", isInUse)

        // Save path points
        val pathTag = ListTag()
        for (point in pathPoints) {
            val pointTag = CompoundTag()
            pointTag.putDouble("x", point.x)
            pointTag.putDouble("y", point.y)
            pointTag.putDouble("z", point.z)
            pathTag.add(pointTag)
        }
        tag.put("PathPoints", pathTag)
    }

    /**
     * Loads data from NBT.
     */
    override fun loadAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.loadAdditional(tag, registries)

        // Load the isInUse state
        isInUse = tag.getBoolean("IsInUse")

        // Load path points
        pathPoints.clear()
        val pathTag = tag.getList("PathPoints", 10) // 10 is the ID for CompoundTag
        for (i in 0 until pathTag.size) {
            val pointTag = pathTag.getCompound(i)
            val point = Vec3(pointTag.getDouble("x"), pointTag.getDouble("y"), pointTag.getDouble("z"))
            pathPoints.add(point)
        }
    }

    override fun getUpdatePacket(): Packet<ClientGamePacketListener>? {
        //println("getUpdatePacket called for NestBlockEntity at $blockPos with egg: $eggComponent")
        return ClientboundBlockEntityDataPacket.create(this)
    }

    override fun getUpdateTag(registryLookup: HolderLookup.Provider): CompoundTag {
        val tag = saveWithoutMetadata(registryLookup)
        //println("getUpdateTag called for NestBlockEntity at $blockPos with tag: $tag")
        return tag
    }


}