package com.cobblemon.mod.common.battles.stadium

import net.minecraft.nbt.CompoundTag
import net.minecraft.world.phys.Vec3

data class StadiumArena(val center: Vec3, val width: Double, val depth: Double) {
    val minX get() = center.x - width / 2
    val maxX get() = center.x + width / 2
    val minZ get() = center.z - depth / 2
    val maxZ get() = center.z + depth / 2

    fun contains(position: Vec3) = position.x in minX..maxX && position.z in minZ..maxZ

    fun valid(origin: Vec3) = width.isFinite() && depth.isFinite() && width in 2.0..128.0 && depth in 2.0..128.0 &&
        listOf(Vec3(minX, center.y, minZ), Vec3(maxX, center.y, minZ),
            Vec3(minX, center.y, maxZ), Vec3(maxX, center.y, maxZ)).all { StadiumConfiguration.validPosition(it, origin) }

    fun toNbt() = CompoundTag().apply {
        putDouble("X", center.x); putDouble("Y", center.y); putDouble("Z", center.z)
        putDouble("Width", width); putDouble("Depth", depth)
    }

    companion object {
        fun fromNbt(tag: CompoundTag) = StadiumArena(Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z")),
            tag.getDouble("Width"), tag.getDouble("Depth"))
    }
}
