package com.cobblemon.mod.common.battles.stadium

import com.cobblemon.mod.common.util.lang
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.Vec3
import kotlin.math.floor

object StadiumSetup {
    data class Slot(val side: Int, val type: String, val index: Int) {
        val label get() = if (type == "actors") lang("stadium.ui.trainer_slot", side, index + 1)
            else lang("stadium.ui.pokemon_slot", side, index + 1)
        fun placements(data: StadiumBlockData, layout: String): MutableList<StadiumBlockPlacement>? {
            val source = data.layout(layout) ?: return null
            val team = if (side == 1) source.side1 else source.side2
            return if (type == "actors") team.actors else team.pokemon
        }
    }

    fun slots(data: StadiumBlockData, layout: String) = (1..2).flatMap { side ->
        listOf("actors", "pokemon").flatMap { type -> (0 until data.slots(layout, type)).map { Slot(side, type, it) } }
    }

    fun firstMissing(data: StadiumBlockData, layout: String) = slots(data, layout).firstOrNull {
        it.placements(data, layout)?.getOrNull(it.index) == null
    }

    fun area(first: Vec3, second: Vec3): StadiumArena {
        val minX = floor(minOf(first.x, second.x)); val maxX = floor(maxOf(first.x, second.x)) + 1
        val minZ = floor(minOf(first.z, second.z)); val maxZ = floor(maxOf(first.z, second.z)) + 1
        return StadiumArena(Vec3((minX + maxX) / 2, first.y, (minZ + maxZ) / 2), maxX - minX, maxZ - minZ)
    }

    fun areaError(data: StadiumBlockData, arena: StadiumArena, origin: Vec3): Component? {
        if (!arena.valid(origin)) return lang("stadium.setup.invalid_area")
        val placements = StadiumConfiguration.layoutIds.mapNotNull(data::layout).flatMap {
            it.side1.actors + it.side1.pokemon + it.side2.actors + it.side2.pokemon
        }
        if (placements.any { !arena.contains(it.position) }) return lang("stadium.setup.excluded_placement")
        return null
    }

    fun placementError(data: StadiumBlockData, layout: String, slot: Slot, position: Vec3, origin: Vec3): Component? {
        if (slot !in slots(data, layout)) return lang("stadium.setup.missing_slot")
        if (!StadiumConfiguration.validPosition(position, origin) || data.arena?.contains(position) != true)
            return lang("stadium.setup.aim_inside")
        val placements = slot.placements(data, layout)
        if (slot.index > (placements?.size ?: 0)) return lang("stadium.setup.previous_slot")
        if (slots(data, layout).any { other -> other != slot &&
                other.placements(data, layout)?.getOrNull(other.index)?.position?.distanceToSqr(position)?.let { it < 0.25 } == true })
            return lang("stadium.setup.separate_placements")
        return null
    }
}
